package com.qshop.confluence.mixin.sellbox;

import com.qshop.confluence.ConfluenceMessages;
import com.qshop.sellbox.SellBoxSaleService;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 出售箱自动出售后的聊天/物品栏提示，价格改用 Confluence 面额格式。
 */
@Mixin(value = SellBoxSaleService.class, remap = false)
public abstract class SellBoxSaleServiceMixin {

    @Redirect(method = "sellContents",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"),
            remap = false, require = 0)
    private static MutableComponent qshop_confluence$soldMessage(String key, Object[] args) {
        return ConfluenceMessages.rewrite(key, args, "qshop_sellbox.message.sold");
    }
}
