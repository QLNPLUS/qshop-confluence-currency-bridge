package com.qshop.confluence.mixin.npc;

import com.qshop.confluence.BridgeConfig;
import com.qshop.confluence.ConfluenceCurrencyBridge;
import com.qshop.confluence.ConfluenceCurrencyFormat;
import com.qshop.confluence.ConfluenceSellBoxPrices;
import com.qshop.sellbox.PriceQuote;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes Sell Box prices using the linked QShop currency available to NeoForge Confluence NPCs. */
@Mixin(targets = "org.confluence.mod.integration.terra_entity.npc_trade.SellTrade", remap = false)
public abstract class SellTradeMixin {

    @Inject(method = "getCost(Lnet/minecraft/world/entity/player/Player;"
            + "Lorg/confluence/terraentity/api/npc/trade/ITradeHolder;"
            + "Lnet/minecraft/world/item/ItemStack;)J",
            at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void qshop_confluence$useSellBoxPrice(Player player, @Coerce Object holder,
                                                   ItemStack stack,
                                                   CallbackInfoReturnable<Long> cir) {
        PriceQuote quote = linkedQuote(stack);
        if (quote != null) {
            cir.setReturnValue(npcAdjustedPrice(quote, stack, holder));
        }
    }

    @Inject(method = "onSell(Lnet/minecraft/server/level/ServerPlayer;"
            + "Lorg/confluence/terraentity/api/npc/trade/ITradeHolder;I)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void qshop_confluence$creditSellBoxSale(ServerPlayer player, @Coerce Object holder,
                                                     int tradeIndex, CallbackInfo ci) {
        if (!"org.confluence.mod.common.menu.NPCTradesForgeMenu"
                .equals(player.containerMenu.getClass().getName())) {
            return;
        }
        int slotIndex = tradeIndex > 1000 ? tradeIndex - 1000 : 0;
        if (slotIndex < 0 || slotIndex >= player.containerMenu.slots.size()) {
            return;
        }
        Slot slot = player.containerMenu.slots.get(slotIndex);
        ItemStack stack = slot.getItem();
        PriceQuote quote = linkedQuote(stack);
        if (quote == null) {
            return;
        }

        // A bound Sell Box quote owns this sale path. Never fall back to vanilla coin
        // payout if QShop cannot credit the wallet, or a failed transaction could be
        // paid in a different currency than the displayed price.
        ci.cancel();
        long copper = npcAdjustedPrice(quote, stack, holder);
        if (copper <= 0L || !ConfluenceCurrencyBridge.credit(player, copper)) {
            return;
        }
        stack.setCount(0);
        slot.setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static PriceQuote linkedQuote(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !ConfluenceCurrencyBridge.active()) {
            return null;
        }
        PriceQuote quote = ConfluenceSellBoxPrices.resolveForSale(stack);
        return quote != null && BridgeConfig.currencyId().equals(quote.currency()) ? quote : null;
    }

    private static long npcAdjustedPrice(PriceQuote quote, ItemStack stack, Object holder) {
        long copper = ConfluenceCurrencyFormat.toCopper(quote.price() * stack.getCount());
        double adjusted = copper * moodMultiplier(holder);
        if (!(adjusted > 0.0D)) {
            return 0L;
        }
        return adjusted >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) adjusted;
    }

    /** Matches Confluence SellTrade's mood value / 100 calculation. */
    private static double moodMultiplier(Object holder) {
        try {
            Object mood = holder.getClass().getMethod("getMood").invoke(holder);
            if (mood == null) {
                return 1.0D;
            }
            Object value = mood.getClass().getMethod("getValue").invoke(mood);
            return Math.max(0.0D, ((Number) value).doubleValue() / 100.0D);
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            return 1.0D;
        }
    }
}
