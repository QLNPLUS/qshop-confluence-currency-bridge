package com.qshop.confluence.client;

import com.qshop.confluence.BridgeConfig;
import com.qshop.confluence.ConfluenceCurrencyBridge;
import com.qshop.confluence.ConfluenceCurrencyFormat;
import com.qshop.confluence.QShopConfluenceMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/** Final tooltip pass: format linked Sell Box quotes and collapse duplicate native sell rows. */
@EventBusSubscriber(modid = QShopConfluenceMod.MODID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class ConfluenceSellBoxTooltipHandler {

    private ConfluenceSellBoxTooltipHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!ConfluenceCurrencyBridge.active()) {
            return;
        }

        List<Component> tooltip = event.getToolTip();
        if (BridgeConfig.sellboxPriceFormat()) {
            Component formattedLine = formatLinkedSellBoxLine(tooltip, event.getItemStack().getCount());
            if (formattedLine != null) {
                removeDuplicateSellLines(tooltip, formattedLine);
                return;
            }
        }

        keepOneNativeSellLine(tooltip);
    }

    private static Component formatLinkedSellBoxLine(List<Component> tooltip, int stackCount) {
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

            // Sell Box pays a unit quote for every item in the stack.
            long copper = ConfluenceCurrencyFormat.toCopper(unitPrice * stackCount);
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
