package com.qshop.confluence.mixin.qshop;

import com.qshop.api.CurrencyService;
import com.qshop.confluence.BridgeConfig;
import com.qshop.confluence.ConfluenceCurrencyBridge;
import com.qshop.confluence.OfflineWalletMigration;
import com.qshop.wallet.WalletCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/** Prepares legacy wallets before QShop's native offline UUID operations run. */
@Mixin(value = CurrencyService.class, remap = false)
public abstract class CurrencyServiceMixin {

    @Shadow(remap = false) @Final private static Object OFFLINE_WALLET_LOCK;

    @Inject(method = "getBalance(Lnet/minecraft/server/MinecraftServer;Ljava/util/UUID;Ljava/lang/String;)D",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void qshop_confluence$prepareOfflineBalance(MinecraftServer server, UUID playerUuid,
                                                        String currencyId,
                                                        CallbackInfoReturnable<Double> cir) {
        if (qshop_confluence$isOfflineBound(server, playerUuid, currencyId)
                && !qshop_confluence$prepareOfflineWallet(server, playerUuid, currencyId)) {
            cir.setReturnValue(0.0D);
        }
    }

    @Inject(method = "deposit(Lnet/minecraft/server/MinecraftServer;Ljava/util/UUID;Ljava/lang/String;D"
            + "Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/core/BlockPos;Z)D",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void qshop_confluence$prepareOfflineDeposit(MinecraftServer server, UUID playerUuid,
                                                       String currencyId, double amount,
                                                       ResourceLocation source, BlockPos sourcePos,
                                                       boolean triggerEvent,
                                                       CallbackInfoReturnable<Double> cir) {
        if (qshop_confluence$isOfflineBound(server, playerUuid, currencyId)
                && !qshop_confluence$prepareOfflineWallet(server, playerUuid, currencyId)) {
            cir.setReturnValue(0.0D);
        }
    }

    @Inject(method = "withdraw(Lnet/minecraft/server/MinecraftServer;Ljava/util/UUID;Ljava/lang/String;D"
            + "Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void qshop_confluence$prepareOfflineWithdraw(MinecraftServer server, UUID playerUuid,
                                                        String currencyId, double amount,
                                                        ResourceLocation source, BlockPos sourcePos,
                                                        boolean triggerEvent,
                                                        CallbackInfoReturnable<Boolean> cir) {
        if (qshop_confluence$isOfflineBound(server, playerUuid, currencyId)
                && !qshop_confluence$prepareOfflineWallet(server, playerUuid, currencyId)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "set(Lnet/minecraft/server/MinecraftServer;Ljava/util/UUID;Ljava/lang/String;D"
            + "Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/core/BlockPos;Z)D",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void qshop_confluence$prepareOfflineSet(MinecraftServer server, UUID playerUuid,
                                                   String currencyId, double amount,
                                                   ResourceLocation source, BlockPos sourcePos,
                                                   boolean triggerEvent,
                                                   CallbackInfoReturnable<Double> cir) {
        if (qshop_confluence$isOfflineBound(server, playerUuid, currencyId)
                && !qshop_confluence$prepareOfflineWallet(server, playerUuid, currencyId)) {
            cir.setReturnValue(0.0D);
        }
    }

    /** Confluence already drops its coins on death; skip QShop's duplicate deduction. */
    @Inject(method = "set(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;D"
            + "Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/core/BlockPos;Z)D",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void qshop_confluence$skipDeathRetention(ServerPlayer player, String currencyId, double amount,
                                                     ResourceLocation source, BlockPos sourcePos,
                                                     boolean triggerEvent, CallbackInfoReturnable<Double> cir) {
        if (!BridgeConfig.skipDeathRetention() || !ConfluenceCurrencyBridge.bound(currencyId)
                || source == null || !source.equals(CurrencyService.SOURCE_DEATH)) {
            return;
        }
        var wallet = WalletCapability.get(player);
        cir.setReturnValue(wallet == null ? 0.0D : wallet.getBalance(currencyId));
    }

    private static boolean qshop_confluence$isOfflineBound(MinecraftServer server, UUID playerUuid,
                                                            String currencyId) {
        return server != null && playerUuid != null
                && ConfluenceCurrencyBridge.bound(currencyId)
                && server.getPlayerList().getPlayer(playerUuid) == null;
    }

    private static boolean qshop_confluence$prepareOfflineWallet(MinecraftServer server, UUID playerUuid,
                                                                  String currencyId) {
        synchronized (OFFLINE_WALLET_LOCK) {
            return OfflineWalletMigration.prepare(server, playerUuid, currencyId);
        }
    }
}
