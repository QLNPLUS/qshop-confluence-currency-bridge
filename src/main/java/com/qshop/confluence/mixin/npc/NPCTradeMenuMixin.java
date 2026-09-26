package com.qshop.confluence.mixin.npc;

import com.qshop.confluence.BridgeConfig;
import com.qshop.confluence.ConfluenceCurrencyBridge;
import com.qshop.confluence.ConfluenceCurrencyFormat;
import com.qshop.confluence.ConfluenceSellBoxPrices;
import com.qshop.sellbox.PriceQuote;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.confluence.mod.common.entity.npc.BaseNPC;
import org.confluence.mod.util.PlayerUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Makes Sell Box prices using the linked QShop currency available to Confluence NPCs.
 * The string target keeps Confluence optional; the mixin plugin gates application on
 * Confluence, QShop, and Sell Box all being present.
 */
@Mixin(targets = "org.confluence.mod.common.entity.npc.trade.NPCTradeMenu", remap = false)
public abstract class NPCTradeMenuMixin {

    @Shadow @Final private BaseNPC npc;
    @Shadow @Final private List<?> refundablePurchases;

    // Confluence's released Forge jar remaps this AbstractContainerMenu override to
    // m_7648_, while the deobfuscated development runtime exposes quickMoveStack.
    @Redirect(method = {
            "quickMoveStack(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;",
            "m_7648_(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;"
    },
            at = @At(value = "INVOKE", target = "Lorg/confluence/mod/util/PlayerUtils;"
                    + "creditFromInventory(Lnet/minecraft/world/entity/player/Player;I"
                    + "Lnet/minecraft/world/item/ItemStack;JZ)Z"), remap = false, require = 0)
    private boolean qshop_confluence$creditInventorySale(Player player, int slot, ItemStack soldStack,
                                                         long copper, boolean withPiggyBank) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return PlayerUtils.creditFromInventory(player, slot, soldStack, copper, withPiggyBank);
        }
        Inventory inventory = player.getInventory();
        if (slot < 0 || slot >= inventory.getContainerSize()) {
            return false;
        }
        ItemStack inSlot = inventory.getItem(slot);
        if (!ItemStack.isSameItemSameTags(inSlot, soldStack) || inSlot.getCount() < soldStack.getCount()) {
            return false;
        }
        if (!ConfluenceCurrencyBridge.credit(serverPlayer, copper)) {
            return false;
        }
        inSlot.shrink(soldStack.getCount());
        if (inSlot.isEmpty()) {
            inventory.setItem(slot, ItemStack.EMPTY);
        } else {
            inventory.setChanged();
        }
        return true;
    }

    @Redirect(method = "sellCarried(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE", target = "Lorg/confluence/mod/util/PlayerUtils;"
                    + "credit(Lnet/minecraft/world/entity/player/Player;J)Z"), remap = false)
    private boolean qshop_confluence$creditCursorSale(Player player, long copper) {
        if (player instanceof ServerPlayer serverPlayer) {
            return ConfluenceCurrencyBridge.credit(serverPlayer, copper);
        }
        return PlayerUtils.credit(player, copper);
    }

    @Inject(method = "getSellPrice(Lnet/minecraft/world/item/ItemStack;)J", at = @At("RETURN"),
            cancellable = true, remap = false)
    private void qshop_confluence$useSellBoxPrice(ItemStack stack, CallbackInfoReturnable<Long> cir) {
        String linkedCurrency = BridgeConfig.currencyId();
        if (linkedCurrency.isEmpty() || stack.isEmpty()) {
            return;
        }

        int remainingCount = stack.getCount();
        long refundTotal = 0L;
        try {
            for (Object entry : refundablePurchases) {
                NPCTradeBuybackAccessor buyback = (NPCTradeBuybackAccessor) entry;
                ItemStack refundStack = buyback.qshop_confluence$getStack();
                if (!ItemStack.isSameItemSameTags(stack, refundStack)) {
                    continue;
                }

                int refundCount = Math.min(remainingCount, refundStack.getCount());
                refundTotal = Math.addExact(refundTotal,
                        Math.multiplyExact(buyback.qshop_confluence$getPrice(), refundCount)
                                / refundStack.getCount());
                remainingCount -= refundCount;
                if (remainingCount == 0) {
                    return;
                }
            }

            ItemStack pricedStack = stack.copyWithCount(remainingCount);
            PriceQuote quote = ConfluenceSellBoxPrices.resolveForSale(pricedStack);
            if (quote == null || !linkedCurrency.equals(quote.currency())) {
                return;
            }

            // Match Sell Box payout semantics: aggregate the unit price for the
            // remaining stack first, then convert the total to whole copper coins.
            long copperTotal = ConfluenceCurrencyFormat.toCopper(quote.price() * remainingCount);
            long npcAdjustedTotal = ConfluenceSellBoxPrices.applyNpcSellPriceMultiplier(
                    copperTotal, qshop_confluence$getSellPriceMultiplier());
            cir.setReturnValue(Math.addExact(refundTotal, npcAdjustedTotal));
        } catch (ArithmeticException ignored) {
            // Confluence treats an unrepresentable trade total as unsellable.
            cir.setReturnValue(0L);
        }
    }

    private float qshop_confluence$getSellPriceMultiplier() {
        return ConfluenceSellBoxPrices.npcSellPriceMultiplier(npc);
    }
}
