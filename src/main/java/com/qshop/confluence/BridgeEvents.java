package com.qshop.confluence;

import com.mojang.logging.LogUtils;
import com.qshop.currency.Currency;
import com.qshop.currency.CurrencyRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * NeoForge 游戏总线事件：配置缓存失效、离线账面补发、货币条目自动创建、指令注册。
 */
public final class BridgeEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    private BridgeEvents() {
    }

    // ---- 模组总线 ----

    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == BridgeConfig.SPEC) {
            BridgeConfig.invalidate();
            LOGGER.info("QShop Confluence Bridge: 配置已加载，绑定货币 id = '{}'", BridgeConfig.currencyId());
        }
    }

    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == BridgeConfig.SPEC) {
            BridgeConfig.invalidate();
            LOGGER.info("QShop Confluence Bridge: 配置已重载，绑定货币 id = '{}'", BridgeConfig.currencyId());
        }
    }

    // ---- 游戏总线 ----

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BridgeCommand.register(event.getDispatcher());
    }

    /** 登录时把离线期间记下的绑定货币补发成钱币。 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!ConfluenceCurrencyBridge.active() || !BridgeConfig.offlinePayout()) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        long amount = PendingMoney.get(server).take(player.getUUID());
        if (amount <= 0L) {
            return;
        }
        ConfluenceCurrencyBridge.credit(player, amount);
        player.sendSystemMessage(Component.translatable("qshop_confluence.message.pending_paid",
                ConfluenceCurrencyFormat.toComponent(amount)));
        LOGGER.info("QShop Confluence Bridge: 向 {} 补发离线收益 {} 铜币",
                player.getGameProfile().getName(), amount);
    }

    /** 启动完成后确保绑定货币在 QShop 货币表里存在。 */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!ConfluenceSupport.qshopPresent() || !ConfluenceCurrencyBridge.active()) {
            return;
        }
        String currencyId = BridgeConfig.currencyId();
        try {
            Currency existing = CurrencyRegistry.get(currencyId);
            if (existing != null) {
                LOGGER.info("QShop Confluence Bridge: 货币 '{}'（{}）已绑定到 Confluence 钱币",
                        currencyId, existing.displayName);
                return;
            }
            if (!BridgeConfig.autoCreateCurrency()) {
                LOGGER.warn("QShop Confluence Bridge: QShop 货币表里没有 '{}'，且 autoCreateCurrency=false；"
                        + "请先创建该货币，否则商店里选不到它。", currencyId);
                return;
            }
            if (CurrencyRegistry.create(currencyId, BridgeConfig.autoCreateCurrencyName(), "#FFD700")) {
                LOGGER.info("QShop Confluence Bridge: 已自动创建 QShop 货币 '{}'", currencyId);
            } else {
                LOGGER.warn("QShop Confluence Bridge: 自动创建 QShop 货币 '{}' 失败", currencyId);
            }
        } catch (Throwable throwable) {
            LOGGER.warn("QShop Confluence Bridge: 检查 QShop 货币表失败", throwable);
        }
    }
}
