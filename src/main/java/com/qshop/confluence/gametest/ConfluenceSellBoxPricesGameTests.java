package com.qshop.confluence.gametest;

import com.qshop.confluence.BridgeConfig;
import com.qshop.confluence.ConfluenceSellBoxPrices;
import com.qshop.confluence.QShopConfluenceMod;
import com.qshop.sellbox.PriceQuote;
import com.qshop.sellbox.SellBoxPrices;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.confluence.mod.common.component.ValueComponent;
import org.confluence.mod.common.component.prefix.ModPrefix;
import org.confluence.mod.common.component.prefix.PrefixType;
import org.confluence.mod.util.PrefixUtils;

@GameTestHolder(QShopConfluenceMod.MODID)
@PrefixGameTestTemplate(false)
public final class ConfluenceSellBoxPricesGameTests {
    private static final double QUOTE_PRICE = 12_345.0D;
    private static final double PRICE_EPSILON = 1.0E-8D;

    private ConfluenceSellBoxPricesGameTests() {
    }

    @GameTest(templateNamespace = "qshop_confluence", template = "empty")
    public static void sellBoxOnlyQuoteIsUsedWithoutPrefix(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.DIAMOND);
        PriceQuote rawQuote = addSellBoxQuote(stack);
        PriceQuote saleQuote = ConfluenceSellBoxPrices.resolveForSale(stack);

        helper.assertTrue(rawQuote != null, "Sell Box did not resolve the configured diamond quote");
        assertPrice(helper, rawQuote, QUOTE_PRICE, "raw QShop quote");
        assertPrice(helper, saleQuote, QUOTE_PRICE, "sale quote without a prefix");
        helper.succeed();
    }

    @GameTest(templateNamespace = "qshop_confluence", template = "empty")
    public static void confluenceNativeValueIsUsedAsFallback(GameTestHelper helper) {
        ItemStack stack = findConfluenceNativePricedItem();
        long nativePrice = ValueComponent.getValue(stack.copyWithCount(1), 0);

        helper.assertTrue(nativePrice > 0L, "Could not find a Confluence item with a native resale value");
        helper.assertTrue(SellBoxPrices.resolve(stack) == null,
                "The native-price test item unexpectedly has a Sell Box quote");

        PriceQuote saleQuote = ConfluenceSellBoxPrices.resolveForSale(stack);
        helper.assertTrue(saleQuote != null, "Confluence native value was not used as the sale fallback");
        helper.assertTrue(saleQuote.currency().equals(BridgeConfig.currencyId()),
                "Native fallback used the wrong currency: " + saleQuote.currency());
        assertPrice(helper, saleQuote, nativePrice, "Confluence native fallback");
        helper.succeed();
    }

    @GameTest(templateNamespace = "qshop_confluence", template = "empty")
    public static void sellBoxQuoteReceivesPrefixRatioExactlyOnce(GameTestHelper helper) {
        PrefixedItem prefixed = findPrefixedConfluenceItem();
        ItemStack stack = prefixed.stack().copyWithCount(3);
        PriceQuote rawQuote = addSellBoxQuote(stack);

        helper.assertTrue(prefixed.baseValue() > 0L, "Prefixable test item has no base value");
        helper.assertTrue(prefixed.modifiedValue() > 0L, "Prefix did not produce a positive modified value");
        helper.assertTrue(prefixed.modifiedValue() != prefixed.baseValue(),
                "Selected prefix does not change the item's value, so it cannot detect repeated scaling");
        helper.assertTrue(rawQuote != null, "Sell Box did not resolve the prefixed item's configured quote");
        assertPrice(helper, rawQuote, QUOTE_PRICE, "unmodified QShop quote");

        double expected = rawQuote.price()
                * ((double) prefixed.modifiedValue() / (double) prefixed.baseValue());
        PriceQuote adjustedQuote = ConfluenceSellBoxPrices.resolveSellBoxQuote(stack);
        PriceQuote saleQuote = ConfluenceSellBoxPrices.resolveForSale(stack);

        assertPrice(helper, adjustedQuote, expected, "QShop quote after one prefix adjustment");
        assertPrice(helper, saleQuote, expected, "sale quote after one prefix adjustment");
        helper.succeed();
    }

    private static PriceQuote addSellBoxQuote(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        SellBoxPrices.setKubeJsPrice(itemId.toString(), null, QUOTE_PRICE, BridgeConfig.currencyId());
        return SellBoxPrices.resolve(stack);
    }

    private static ItemStack findConfluenceNativePricedItem() {
        for (Item item : BuiltInRegistries.ITEM) {
            if (!"confluence".equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            if (ValueComponent.getValue(stack, 0, true) > 0) {
                return stack;
            }
        }
        throw new IllegalStateException("No Confluence item with a native resale value was registered");
    }

    private static PrefixedItem findPrefixedConfluenceItem() {
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (!"confluence".equals(itemId.getNamespace())) {
                continue;
            }

            ItemStack unprefixed = new ItemStack(item);
            PrefixType prefixType = PrefixUtils.getPrefixType(unprefixed);
            if (prefixType == PrefixType.UNKNOWN || ValueComponent.getValue(unprefixed, 0, true) <= 0) {
                continue;
            }

            for (long seed = 0L; seed < 128L; seed++) {
                ItemStack candidate = unprefixed.copy();
                ModPrefix prefix = prefixType.randomPrefix(RandomSource.create(seed), candidate);
                if (prefix == null) {
                    continue;
                }
                PrefixUtils.setAndUpdate(candidate, prefixType, prefix);
                long baseValue = ValueComponent.getValue(candidate.copyWithCount(1), 50, true);
                long modifiedValue = ValueComponent.getValue(candidate.copyWithCount(1), 0);
                if (baseValue > 0L && modifiedValue > 0L && modifiedValue != baseValue) {
                    return new PrefixedItem(candidate, baseValue, modifiedValue);
                }
            }
        }
        throw new IllegalStateException("No Confluence item with a value-changing prefix was registered");
    }

    private static void assertPrice(GameTestHelper helper, PriceQuote quote, double expected, String label) {
        helper.assertTrue(quote != null, label + " was null");
        helper.assertTrue(Math.abs(quote.price() - expected) <= PRICE_EPSILON,
                label + " expected " + expected + " but got " + quote.price());
        helper.assertTrue(BridgeConfig.currencyId().equals(quote.currency()),
                label + " used unexpected currency " + quote.currency());
    }

    private record PrefixedItem(ItemStack stack, long baseValue, long modifiedValue) {
    }
}
