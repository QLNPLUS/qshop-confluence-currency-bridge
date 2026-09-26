package com.qshop.confluence;

import com.qshop.sellbox.PriceQuote;
import com.qshop.sellbox.SellBoxPrices;
import net.minecraft.world.item.ItemStack;
import org.confluence.mod.common.component.ValueComponent;
import org.confluence.mod.util.PrefixUtils;

/** Resolves native Confluence resale values and applies their affix adjustment to QShop quotes. */
public final class ConfluenceSellBoxPrices {

    private ConfluenceSellBoxPrices() {
    }

    /** Applies Confluence's modified/base resale ratio to an explicit linked QShop quote. */
    public static PriceQuote adjustQuote(ItemStack stack, PriceQuote quote) {
        if (stack == null || stack.isEmpty() || quote == null
                || !ConfluenceCurrencyBridge.active()) {
            return quote;
        }

        ItemStack singleItem = stack.copyWithCount(1);
        if (!ConfluenceCurrencyBridge.bound(quote.currency())) {
            return quote;
        }

        // PrefixUtils.setAndUpdate writes VALUE using this same fallback when a
        // prefix is applied, including for otherwise unpriced third-party gear.
        if (PrefixUtils.getPrefix(singleItem) == null) {
            return quote;
        }
        long baseValue = ValueComponent.getValue(singleItem, 50, true);
        if (baseValue <= 0L) {
            return quote;
        }
        long modifiedValue = ValueComponent.getValue(singleItem, 0);
        if (modifiedValue <= 0L) {
            return quote;
        }
        double adjustedPrice = quote.price() * ((double) modifiedValue / baseValue);
        if (Double.isNaN(adjustedPrice) || adjustedPrice < 0.0D) {
            adjustedPrice = 0.0D;
        } else if (Double.isInfinite(adjustedPrice)) {
            adjustedPrice = Double.MAX_VALUE / Math.max(1, stack.getCount());
        }
        return new PriceQuote(adjustedPrice, quote.currency());
    }

    /** Resolves and adjusts one raw QShop quote. Keep SellBoxPrices.resolve itself unmodified. */
    public static PriceQuote resolveSellBoxQuote(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return adjustQuote(stack, SellBoxPrices.resolve(stack));
    }

    /** Resolves one QShop quote first, then falls back to Confluence's native resale value. */
    public static PriceQuote resolveForSale(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        PriceQuote sellBoxQuote = resolveSellBoxQuote(stack);
        if (sellBoxQuote != null) {
            return sellBoxQuote;
        }
        if (!ConfluenceCurrencyBridge.active()) {
            return null;
        }

        long nativePrice = Math.max(0L, ValueComponent.getValue(stack.copyWithCount(1), 0));
        return nativePrice == 0L ? null : new PriceQuote(nativePrice, BridgeConfig.currencyId());
    }

    /** Returns Confluence's native copper value for the given stack, including its count. */
    public static long nativeCopperPrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0L;
        }
        return Math.max(0L, ValueComponent.getValueLong(stack, 0));
    }

    /** Reads the active NPC's sell multiplier, matching Confluence's own transaction path. */
    public static float npcSellPriceMultiplier(Object npc) {
        try {
            if (npc == null) {
                return 1.0F;
            }
            Object mood = npc.getClass().getMethod("getMood").invoke(npc);
            return ((Number) mood.getClass().getMethod("getSellPriceMultiplier").invoke(mood)).floatValue();
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            // Keep the price display usable if a Confluence version changes its mood API.
            return 1.0F;
        }
    }

    /** Applies Confluence's exact copper-total rounding step for NPC sell prices. */
    public static long applyNpcSellPriceMultiplier(long copperTotal, float multiplier) {
        return (long) ((double) copperTotal * multiplier);
    }

    /** Replaces Confluence's native value input with a linked Sell Box quote for Goblin reforging. */
    public static int reforgeBasePrice(ItemStack stack, int nativePrice) {
        if (stack == null || stack.isEmpty() || !ConfluenceCurrencyBridge.active()) {
            return nativePrice;
        }
        PriceQuote quote = resolveSellBoxQuote(stack);
        if (!ConfluenceCurrencyBridge.bound(quote == null ? null : quote.currency())) {
            return nativePrice;
        }
        long total = ConfluenceCurrencyFormat.toCopper(quote.price() * stack.getCount());
        return (int) Math.min(Integer.MAX_VALUE, total);
    }
}
