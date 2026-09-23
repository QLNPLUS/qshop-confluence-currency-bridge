package com.qshop.confluence.mixin.sellbox;

import com.qshop.confluence.ConfluenceSellBoxPrices;
import com.qshop.sellbox.PriceQuote;
import com.qshop.sellbox.SellBoxPrices;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds Confluence's native item resale value as a fallback Sell Box quote. */
@Mixin(value = SellBoxPrices.class, remap = false)
public abstract class SellBoxPricesMixin {

    @Inject(method = "resolve(Lnet/minecraft/world/item/ItemStack;)Lcom/qshop/sellbox/PriceQuote;",
            at = @At("RETURN"), cancellable = true, remap = false, require = 1)
    private static void qshop_confluence$adjustServerPrice(ItemStack stack,
            CallbackInfoReturnable<PriceQuote> cir) {
        cir.setReturnValue(ConfluenceSellBoxPrices.adjustQuote(stack, cir.getReturnValue()));
    }

    @Inject(method = "resolveClient(Lnet/minecraft/world/item/ItemStack;)Lcom/qshop/sellbox/PriceQuote;",
            at = @At("RETURN"), cancellable = true, remap = false, require = 1)
    private static void qshop_confluence$adjustClientPrice(ItemStack stack,
            CallbackInfoReturnable<PriceQuote> cir) {
        cir.setReturnValue(ConfluenceSellBoxPrices.adjustQuote(stack, cir.getReturnValue()));
    }
}
