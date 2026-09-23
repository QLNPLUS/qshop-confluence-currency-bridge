package com.qshop.confluence.mixin.sellbox;

import com.qshop.confluence.ConfluenceSellBoxPrices;
import com.qshop.sellbox.PriceQuote;
import com.qshop.sellbox.SellBoxSaleService;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Uses Confluence's native resale value only for the automatic sale transaction. */
@Mixin(value = SellBoxSaleService.class, remap = false)
public abstract class SellBoxSalePriceMixin {

    @Redirect(method = "sellContents",
            at = @At(value = "INVOKE", target = "Lcom/qshop/sellbox/SellBoxPrices;"
                    + "resolve(Lnet/minecraft/world/item/ItemStack;)Lcom/qshop/sellbox/PriceQuote;"),
            remap = false, require = 1)
    private static PriceQuote qshop_confluence$resolveNativeSalePrice(ItemStack stack) {
        return ConfluenceSellBoxPrices.resolveForSale(stack);
    }
}
