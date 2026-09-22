package com.qshop.confluence.mixin.sellbox;

import com.qshop.confluence.BridgeConfig;
import com.qshop.confluence.ConfluenceCurrencyBridge;
import com.qshop.confluence.ConfluenceCurrencyFormat;
import com.qshop.sellbox.PriceQuote;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 出售箱的物品牌价格行。
 *
 * <p>{@code PriceQuote#formattedPrice()} 只返回数字字符串，界面把它和货币名拼成
 * “售价: %s %s”。绑定货币直接在这里换成 Confluence 面额文本，
 * 货币名由 {@link SellBoxClientMixin} 置空，避免重复。</p>
 */
@Mixin(value = PriceQuote.class, remap = false)
public abstract class PriceQuoteMixin {

    @Inject(method = "formattedPrice", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void qshop_confluence$confluencePrice(CallbackInfoReturnable<String> cir) {
        if (!BridgeConfig.sellboxPriceFormat()) {
            return;
        }
        PriceQuote self = (PriceQuote) (Object) this;
        if (!ConfluenceCurrencyBridge.bound(self.currency())) {
            return;
        }
        cir.setReturnValue(ConfluenceCurrencyFormat.toPlainString(self.price()));
    }
}
