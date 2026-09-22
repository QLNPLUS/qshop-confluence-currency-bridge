package com.qshop.confluence.mixin.sellbox;

import com.qshop.confluence.BridgeConfig;
import com.qshop.confluence.ConfluenceCurrencyBridge;
import com.qshop.sellbox.client.SellBoxClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 绑定货币的价格已经由 {@link PriceQuoteMixin} 换成 Confluence 面额文本，
 * 再把后面的货币显示名去掉，否则会出现“1 铂金币 23 金币 金币”。
 */
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
