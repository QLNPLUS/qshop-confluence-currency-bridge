package com.qshop.confluence.mixin.sellbox;

import com.qshop.confluence.ConfluenceMessages;
import com.qshop.sellbox.SellBoxEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 登录时补发离线出售收益的提示消息，价格改用 Confluence 面额格式。
 */
@Mixin(value = SellBoxEvents.class, remap = false)
public abstract class SellBoxEventsMixin {

    @Redirect(method = "onPlayerLoggedIn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"),
            require = 0)
    private static MutableComponent qshop_confluence$syncedMessage(String key, Object[] args) {
        return ConfluenceMessages.rewrite(key, args, "qshop_sellbox.message.synced");
    }
}
