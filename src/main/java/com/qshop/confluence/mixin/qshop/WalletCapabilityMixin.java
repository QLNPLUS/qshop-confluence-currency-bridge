package com.qshop.confluence.mixin.qshop;

import com.qshop.confluence.ConfluenceCurrencyBridge;
import com.qshop.confluence.ConfluenceWallet;
import com.qshop.wallet.IWallet;
import com.qshop.wallet.WalletCapability;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把 QShop 的钱包入口 {@code WalletCapability.get(Player)} 换成包装实例。
 *
 * <p>QShop 里所有余额读写都经由这个静态入口（交易、指令、FTB、KubeJS、商店同步），
 * 所以在这里拦截就可以覆盖全部路径，不需要改 QShop 源码。</p>
 *
 * <p>只在服务端包装：客户端钱包只是个空壳，余额来自 {@code SyncWalletPacket}，
 * 包装它没有意义，反而会读到客户端本地的钱币物品造成不一致。</p>
 */
@Mixin(value = WalletCapability.class, remap = false)
public abstract class WalletCapabilityMixin {

    @Inject(method = "get", at = @At("RETURN"), cancellable = true, remap = false)
    private static void qshop_confluence$wrapWallet(Player player, CallbackInfoReturnable<IWallet> cir) {
        IWallet base = cir.getReturnValue();
        if (base == null || !(player instanceof ServerPlayer)) {
            return;
        }
        if (base instanceof ConfluenceWallet) {
            return;
        }
        if (!ConfluenceCurrencyBridge.active()) {
            return;
        }
        cir.setReturnValue(new ConfluenceWallet(player, base));
    }
}
