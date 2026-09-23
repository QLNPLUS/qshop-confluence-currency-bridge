package com.qshop.confluence;

import com.qshop.sellbox.PriceQuote;
import com.qshop.sellbox.SellBoxPrices;
import net.minecraft.world.item.ItemStack;
import org.confluence.mod.common.component.ValueComponent;

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

        long baseValue = ValueComponent.getValue(singleItem, 0, true);
        if (baseValue <= 0L) {
            return quote;
        }
        long modifiedValue = ValueComponent.getValue(singleItem, 0);
        if (modifiedValue <= 0L) {
            return null;
        }
        double adjustedPrice = quote.price() * ((double) modifiedValue / baseValue);
        if (Double.isNaN(adjustedPrice) || adjustedPrice < 0.0D) {
            adjustedPrice = 0.0D;
        } else if (Double.isInfinite(adjustedPrice)) {
            adjustedPrice = Double.MAX_VALUE / Math.max(1, stack.getCount());
        }
        return new PriceQuote(adjustedPrice, quote.currency());
    }

    /** Uses the native Confluence value only at sale paths that need the fallback. */
    public static PriceQuote withNativeFallback(ItemStack stack, PriceQuote quote) {
        if (quote != null || stack == null || stack.isEmpty()
                || !ConfluenceCurrencyBridge.active()) {
            return quote;
        }
        long nativePrice = Math.max(0L, ValueComponent.getValue(stack.copyWithCount(1), 0));
        return nativePrice == 0L ? null : new PriceQuote(nativePrice, BridgeConfig.currencyId());
    }

    /** Resolves a QShop quote first, then falls back to Confluence's native item resale value. */
    public static PriceQuote resolveForSale(ItemStack stack) {
        return withNativeFallback(stack, SellBoxPrices.resolve(stack));
    }
}
