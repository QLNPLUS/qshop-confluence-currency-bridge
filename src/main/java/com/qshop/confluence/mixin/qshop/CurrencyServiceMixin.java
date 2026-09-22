package com.qshop.confluence.mixin.qshop;

import com.qshop.api.CurrencyService;
import com.qshop.confluence.BridgeConfig;
import com.qshop.confluence.ConfluenceCurrencyBridge;
import com.qshop.confluence.ConfluenceCurrencyFormat;
import com.qshop.confluence.PendingMoney;
import com.qshop.confluence.QShopConfluenceMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * 处理 {@code CurrencyService} 里钱包包装层覆盖不到的两个地方：
 *
 * <ol>
 *   <li><b>离线 UUID 重载</b>：{@code deposit/withdraw/set/getBalance(MinecraftServer, UUID, ...)}
 *       走的是直接读写玩家 NBT 的离线钱包，拿不到玩家实体，没法动钱币物品。
 *       绑定货币改成记在 {@link PendingMoney} 账面上，登录时补发。</li>
 *   <li><b>死亡扣款</b>：Confluence 自己已经处理死亡掉落钱币，
 *       QShop 的 {@code death.loseCurrencyOnDeath} 若同时生效会扣两次，默认忽略。</li>
 * </ol>
 */
@Mixin(value = CurrencyService.class, remap = false)
public abstract class CurrencyServiceMixin {

    @Inject(method = "getBalance(Lnet/minecraft/server/MinecraftServer;Ljava/util/UUID;Ljava/lang/String;)D",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void qshop_confluence$offlineBalance(MinecraftServer server, UUID playerUuid, String currencyId,
                                                 CallbackInfoReturnable<Double> cir) {
        if (!qshop_confluence$isOfflineBound(server, playerUuid, currencyId)) {
            return;
        }
        cir.setReturnValue((double) PendingMoney.get(server).peek(playerUuid));
    }

    @Inject(method = "deposit(Lnet/minecraft/server/MinecraftServer;Ljava/util/UUID;Ljava/lang/String;D"
            + "Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/core/BlockPos;Z)D",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void qshop_confluence$offlineDeposit(MinecraftServer server, UUID playerUuid, String currencyId,
                                                 double amount, ResourceLocation source, BlockPos sourcePos,
                                                 boolean triggerEvent, CallbackInfoReturnable<Double> cir) {
        if (!qshop_confluence$isOfflineBound(server, playerUuid, currencyId)) {
            return;
        }
        PendingMoney data = PendingMoney.get(server);
        data.add(playerUuid, ConfluenceCurrencyFormat.toCopper(amount));
        cir.setReturnValue((double) data.peek(playerUuid));
    }

    @Inject(method = "withdraw(Lnet/minecraft/server/MinecraftServer;Ljava/util/UUID;Ljava/lang/String;D"
            + "Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void qshop_confluence$offlineWithdraw(MinecraftServer server, UUID playerUuid, String currencyId,
                                                  double amount, ResourceLocation source, BlockPos sourcePos,
                                                  boolean triggerEvent, CallbackInfoReturnable<Boolean> cir) {
        if (!qshop_confluence$isOfflineBound(server, playerUuid, currencyId)) {
            return;
        }
        PendingMoney data = PendingMoney.get(server);
        long pending = data.peek(playerUuid);
        long cost = ConfluenceCurrencyFormat.toCopper(amount);
        if (pending < cost) {
            cir.setReturnValue(false);
            return;
        }
        data.set(playerUuid, pending - cost);
        cir.setReturnValue(true);
    }

    @Inject(method = "set(Lnet/minecraft/server/MinecraftServer;Ljava/util/UUID;Ljava/lang/String;D"
            + "Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/core/BlockPos;Z)D",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void qshop_confluence$offlineSet(MinecraftServer server, UUID playerUuid, String currencyId,
                                             double amount, ResourceLocation source, BlockPos sourcePos,
                                             boolean triggerEvent, CallbackInfoReturnable<Double> cir) {
        if (!qshop_confluence$isOfflineBound(server, playerUuid, currencyId)) {
            return;
        }
        long target = ConfluenceCurrencyFormat.toCopper(amount);
        PendingMoney.get(server).set(playerUuid, target);
        QShopConfluenceMod.LOGGER.info(
                "离线 set {} -> {} 铜币：Confluence 的钱是物品，离线期间只能记在待补发账面上", playerUuid, target);
        cir.setReturnValue((double) target);
    }

    /**
     * 死亡扣款保护：Confluence 自己会让玩家死亡掉钱，QShop 再扣一次就是双重损失。
     * 默认忽略该来源的 set，可通过 skipDeathRetention=false 关掉。
     */
    @Inject(method = "set(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;D"
            + "Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/core/BlockPos;Z)D",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void qshop_confluence$skipDeathRetention(ServerPlayer player, String currencyId, double amount,
                                                     ResourceLocation source, BlockPos sourcePos,
                                                     boolean triggerEvent, CallbackInfoReturnable<Double> cir) {
        if (!BridgeConfig.skipDeathRetention()) {
            return;
        }
        if (!ConfluenceCurrencyBridge.bound(currencyId)) {
            return;
        }
        if (source == null || !source.equals(CurrencyService.SOURCE_DEATH)) {
            return;
        }
        cir.setReturnValue(ConfluenceCurrencyBridge.readBalance(player, 0.0D));
    }

    /** true 表示"绑定货币 + 玩家离线 + 允许离线记账"，此时由本混入接管。 */
    @Unique
    private static boolean qshop_confluence$isOfflineBound(MinecraftServer server, UUID playerUuid, String currencyId) {
        if (server == null || playerUuid == null) {
            return false;
        }
        if (!ConfluenceCurrencyBridge.bound(currencyId) || !BridgeConfig.offlinePayout()) {
            return false;
        }
        // 在线玩家交给正常路径：钱包包装层会直接增删钱币
        return server.getPlayerList().getPlayer(playerUuid) == null;
    }
}
