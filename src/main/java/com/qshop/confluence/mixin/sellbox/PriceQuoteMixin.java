package com.qshop.confluence.mixin.sellbox;

import com.qshop.confluence.BridgeConfig;
import com.qshop.confluence.ConfluenceCurrencyBridge;
import com.qshop.sellbox.PriceQuote;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 出售箱的物品牌价格行。
 *
 * <p>{@code PriceQuote#formattedPrice()} 只返回数字字符串。这里保留绑定货币的未舍入单价，
 * 让客户端先乘物品堆叠数量，再统一换算成铜币并交给 Confluence 自己的
 * {@code ClientUtils.formatPrice} 生成 tooltip 组件。</p>
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
        cir.setReturnValue(Double.toString(self.price()));
    }
}
