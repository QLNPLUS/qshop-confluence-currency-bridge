package com.qshop.confluence;

import com.qshop.sellbox.PriceQuote;
import net.minecraft.world.item.ItemStack;
import org.confluence.mod.common.component.ValueComponent;

/** Resolves native Confluence resale values and applies their affix adjustment to QShop quotes. */
public final class ConfluenceSellBoxPrices {

    private ConfluenceSellBoxPrices() {
    }

    /**
     * Uses Confluence's per-item resale value when QShop has no quote. For an explicit
     * linked-currency quote, scale it by the stack's modified/base Confluence resale value
     * so prefixes and affixes affect the configured QShop price in the same proportion.
     */
    public static PriceQuote adjust(ItemStack stack, PriceQuote quote) {
        if (stack == null || stack.isEmpty() || !ConfluenceCurrencyBridge.active()) {
            return quote;
        }

        ItemStack singleItem = stack.copyWithCount(1);
        if (quote == null) {
            long nativePrice = Math.max(0L, ValueComponent.getValue(singleItem, 0));
            return nativePrice == 0L ? null
                    : new PriceQuote(nativePrice, BridgeConfig.currencyId());
        }
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
}
