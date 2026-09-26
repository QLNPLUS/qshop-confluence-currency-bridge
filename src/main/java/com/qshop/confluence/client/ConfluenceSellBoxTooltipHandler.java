package com.qshop.confluence.client;

import com.qshop.confluence.BridgeConfig;
import com.qshop.confluence.ConfluenceCurrencyBridge;
import com.qshop.confluence.ConfluenceCurrencyFormat;
import com.qshop.confluence.ConfluenceSellBoxPrices;
import com.qshop.confluence.QShopConfluenceMod;
import com.qshop.confluence.compat.ContainerScreenAccess;
import com.qshop.confluence.compat.NPCBuybackAccess;
import com.qshop.confluence.compat.NPCTradeMenuAccess;
import com.qshop.sellbox.PriceQuote;
import com.qshop.sellbox.SellBoxPrices;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** Formats linked Sell Box quotes and displays the active NPC's live buyback price. */
@Mod.EventBusSubscriber(modid = QShopConfluenceMod.MODID, value = Dist.CLIENT)
public final class ConfluenceSellBoxTooltipHandler {

    private ConfluenceSellBoxTooltipHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!ConfluenceCurrencyBridge.active()) {
            return;
        }

        List<Component> tooltip = event.getToolTip();
        if (formatNpcTradePrice(tooltip, event.getItemStack())) {
            return;
        }

        if (BridgeConfig.sellboxPriceFormat()) {
            Component formattedLine = formatLinkedSellBoxLine(tooltip, event.getItemStack());
            if (formattedLine != null) {
                removeDuplicateSellLines(tooltip, formattedLine);
                return;
            }
        }

        keepOneNativeSellLine(tooltip);
    }

    /** Shows the amount the currently open NPC menu would pay for this inventory stack. */
    private static boolean formatNpcTradePrice(List<Component> tooltip, ItemStack tooltipStack) {
        NPCTradeMenuAccess menu = currentNpcTradeMenu(tooltipStack);
        if (menu == null) {
            return false;
        }

        long copper;
        try {
            copper = getNpcSellPrice(menu, tooltip, tooltipStack);
        } catch (ArithmeticException ignored) {
            // Match NPCTradeMenuMixin: an unrepresentable transaction is unsellable.
            copper = 0L;
        }

        int priceLine = findNpcBuybackPriceLine(tooltip);
        Component formatted = Component.translatable("qshop_confluence.tooltip.npc_sell_price")
                .withStyle(ChatFormatting.GRAY)
                .append(ConfluenceCurrencyFormat.formatSellPrice(copper));
        Component keep = null;
        if (priceLine >= 0) {
            tooltip.set(priceLine, formatted);
            keep = formatted;
        } else if (copper > 0L) {
            tooltip.add(formatted);
            keep = formatted;
        }

        removeNpcBuybackPriceLines(tooltip, keep);
        return true;
    }

    private static NPCTradeMenuAccess currentNpcTradeMenu(ItemStack tooltipStack) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || !(minecraft.screen instanceof AbstractContainerScreen<?> screen)
                || !(screen instanceof ContainerScreenAccess screenAccessor)) {
            return null;
        }

        Slot hoveredSlot = screenAccessor.qshop_confluence$getHoveredSlot();
        if (screenAccessor.qshop_confluence$getMenu() != player.containerMenu
                || hoveredSlot == null || hoveredSlot.container != player.getInventory()) {
            return null;
        }
        ItemStack inventoryStack = hoveredSlot.getItem();
        if (inventoryStack.isEmpty()
                || !ItemStack.isSameItemSameTags(inventoryStack, tooltipStack)
                || inventoryStack.getCount() != tooltipStack.getCount()) {
            return null;
        }

        return screenAccessor.qshop_confluence$getMenu() instanceof NPCTradeMenuAccess menu
                ? menu : null;
    }

    /** Mirrors NPCTradeMenuMixin's refund-first and mood-adjusted total calculation. */
    private static long getNpcSellPrice(NPCTradeMenuAccess menu, List<Component> tooltip,
                                        ItemStack stack) {
        int remainingCount = stack.getCount();
        long refundTotal = 0L;
        List<?> refundablePurchases = menu.qshop_confluence$getRefundablePurchases();
        if (refundablePurchases != null) {
            for (Object entry : refundablePurchases) {
                if (!(entry instanceof NPCBuybackAccess buyback)) {
                    continue;
                }
                ItemStack refundStack = buyback.qshop_confluence$getStack();
                if (refundStack == null || refundStack.isEmpty()
                        || !ItemStack.isSameItemSameTags(stack, refundStack)) {
                    continue;
                }

                int refundCount = Math.min(remainingCount, refundStack.getCount());
                long refund = Math.multiplyExact(buyback.qshop_confluence$getPrice(), refundCount)
                        / refundStack.getCount();
                refundTotal = Math.addExact(refundTotal, refund);
                remainingCount -= refundCount;
                if (remainingCount == 0) {
                    return refundTotal;
                }
            }
        }

        ItemStack remainingStack = stack.copyWithCount(remainingCount);
        PriceQuote quote = resolveLinkedSellBoxQuote(tooltip, remainingStack);
        long baseTotal;
        if (quote != null) {
            baseTotal = ConfluenceCurrencyFormat.toCopper(quote.price() * remainingCount);
        } else {
            baseTotal = ConfluenceSellBoxPrices.nativeCopperPrice(remainingStack);
        }

        long adjustedTotal = ConfluenceSellBoxPrices.applyNpcSellPriceMultiplier(
                baseTotal, ConfluenceSellBoxPrices.npcSellPriceMultiplier(menu.qshop_confluence$getNpc()));
        return Math.addExact(refundTotal, adjustedTotal);
    }

    private static PriceQuote resolveLinkedSellBoxQuote(List<Component> tooltip, ItemStack stack) {
        for (int index = tooltip.size() - 1; index >= 0; index--) {
            Component line = tooltip.get(index);
            if (!isLinkedSellBoxLine(line)
                    || !(line.getContents() instanceof TranslatableContents contents)) {
                continue;
            }
            Double unitPrice = parsePrice(contents.getArgs()[0]);
            if (unitPrice == null) {
                break;
            }
            PriceQuote quote = ConfluenceSellBoxPrices.adjustQuote(stack,
                    new PriceQuote(unitPrice, BridgeConfig.currencyId()));
            return quote != null && ConfluenceCurrencyBridge.bound(quote.currency()) ? quote : null;
        }

        PriceQuote clientQuote = SellBoxPrices.resolveClient(stack);
        if (clientQuote == null || !ConfluenceCurrencyBridge.bound(clientQuote.currency())) {
            return null;
        }
        return ConfluenceSellBoxPrices.adjustQuote(stack, clientQuote);
    }

    private static int findNpcBuybackPriceLine(List<Component> tooltip) {
        for (int index = tooltip.size() - 1; index >= 0; index--) {
            Component line = tooltip.get(index);
            if (isNpcSellLine(line) || isNativeBuyLine(line) || isNativeSellLine(line)
                    || isSellBoxPriceLine(line)) {
                return index;
            }
        }
        return -1;
    }

    private static void removeNpcBuybackPriceLines(List<Component> tooltip, Component keep) {
        for (int index = tooltip.size() - 1; index >= 0; index--) {
            Component line = tooltip.get(index);
            if (line == keep) {
                continue;
            }
            if (isNpcSellLine(line) || isNativeBuyLine(line) || isNativeSellLine(line)
                    || isSellBoxPriceLine(line)) {
                tooltip.remove(index);
            }
        }
    }

    private static boolean isNativeBuyLine(Component line) {
        if (!(line.getContents() instanceof TranslatableContents contents)
                || !"tooltip.price.buy".equals(contents.getKey())) {
            return false;
        }
        return contents.getArgs().length == 0;
    }

    private static boolean isSellBoxPriceLine(Component line) {
        return line.getContents() instanceof TranslatableContents contents
                && "qshop_sellbox.tooltip.price".equals(contents.getKey());
    }

    private static boolean isNpcSellLine(Component line) {
        return line.getContents() instanceof TranslatableContents contents
                && "qshop_confluence.tooltip.npc_sell_price".equals(contents.getKey());
    }

    private static Component formatLinkedSellBoxLine(List<Component> tooltip, ItemStack stack) {
        for (int index = tooltip.size() - 1; index >= 0; index--) {
            Component line = tooltip.get(index);
            if (!(line.getContents() instanceof TranslatableContents contents)
                    || !"qshop_sellbox.tooltip.price".equals(contents.getKey())) {
                continue;
            }

            Object[] args = contents.getArgs();
            if (args.length < 2 || !(args[1] instanceof String currencyLabel) || !currencyLabel.isBlank()) {
                continue;
            }

            Double unitPrice = parsePrice(args[0]);
            if (unitPrice == null) {
                continue;
            }

            PriceQuote adjustedQuote = ConfluenceSellBoxPrices.adjustQuote(stack,
                    new PriceQuote(unitPrice, BridgeConfig.currencyId()));
            if (adjustedQuote == null) {
                tooltip.remove(index);
                return null;
            }

            // Sell Box pays a unit quote for every item in the stack.
            long copper = ConfluenceCurrencyFormat.toCopper(adjustedQuote.price() * stack.getCount());
            Component formatted = Component.translatable("tooltip.price.sell")
                    .withStyle(ChatFormatting.GRAY)
                    .append(ConfluenceCurrencyFormat.formatSellPrice(copper));
            tooltip.set(index, formatted);
            return formatted;
        }
        return null;
    }

    private static void removeDuplicateSellLines(List<Component> tooltip, Component keep) {
        for (int index = tooltip.size() - 1; index >= 0; index--) {
            Component line = tooltip.get(index);
            if (line == keep) {
                continue;
            }
            if (isNativeSellLine(line) || isLinkedSellBoxLine(line)) {
                tooltip.remove(index);
            }
        }
    }

    private static void keepOneNativeSellLine(List<Component> tooltip) {
        boolean kept = false;
        for (int index = tooltip.size() - 1; index >= 0; index--) {
            if (!isNativeSellLine(tooltip.get(index))) {
                continue;
            }
            if (!kept) {
                kept = true;
            } else {
                tooltip.remove(index);
            }
        }
    }

    private static boolean isNativeSellLine(Component line) {
        if (!(line.getContents() instanceof TranslatableContents contents)
                || !"tooltip.price.sell".equals(contents.getKey())) {
            return false;
        }
        return contents.getArgs().length == 0;
    }

    private static boolean isLinkedSellBoxLine(Component line) {
        if (!(line.getContents() instanceof TranslatableContents contents)
                || !"qshop_sellbox.tooltip.price".equals(contents.getKey())) {
            return false;
        }
        Object[] args = contents.getArgs();
        return args.length >= 2 && args[1] instanceof String currencyLabel && currencyLabel.isBlank();
    }

    private static Double parsePrice(Object price) {
        if (price instanceof Number number) {
            return number.doubleValue();
        }
        if (price instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
