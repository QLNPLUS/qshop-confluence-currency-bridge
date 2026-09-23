package com.qshop.confluence.mixin.sellbox;

import com.qshop.confluence.BridgeConfig;
import com.qshop.confluence.ConfluenceCurrencyBridge;
import com.qshop.sellbox.client.SellBoxClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hides the linked QShop currency label before the final Confluence tooltip pass. */
@Mixin(value = SellBoxClient.class, remap = false)
public abstract class SellBoxClientMixin {

    @Inject(method = "currencyDisplayName", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void qshop_confluence$blankCurrencyLabel(String currencyId, CallbackInfoReturnable<String> cir) {
        if (!BridgeConfig.sellboxPriceFormat()) {
            return;
        }
        if (ConfluenceCurrencyBridge.bound(currencyId)) {
            cir.setReturnValue("");
        }
    }
}
