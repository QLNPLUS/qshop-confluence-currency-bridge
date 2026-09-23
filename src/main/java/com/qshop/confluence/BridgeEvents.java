package com.qshop.confluence;

import com.mojang.logging.LogUtils;
import com.qshop.currency.Currency;
import com.qshop.currency.CurrencyRegistry;
import com.qshop.wallet.IWallet;
import com.qshop.wallet.WalletCapability;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Forge 总线事件：配置缓存失效、背包余额同步、货币条目自动创建、指令注册。
 */
public final class BridgeEvents {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int RECONCILE_FALLBACK_TICKS = 100;
    private static final Set<UUID> DIRTY_CURRENCY_PLAYERS = new HashSet<>();

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

    // ---- Forge 总线 ----

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BridgeCommand.register(event.getDispatcher());
    }

    /** 登录时迁移旧余额，并把离线期间的 QShop 变动同步到钱币和储备。 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!ConfluenceCurrencyBridge.active()) {
            return;
        }
        IWallet wallet = WalletCapability.get(player);
        if (wallet != null) {
            wallet.getBalance(BridgeConfig.currencyId());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        DIRTY_CURRENCY_PLAYERS.remove(event.getEntity().getUUID());
    }

    /** Queues a player for a balance reconciliation at the end of their current/next tick. */
    public static void markCurrencyInventoryDirty(ServerPlayer player) {
        if (player != null && ConfluenceCurrencyBridge.active()) {
            DIRTY_CURRENCY_PLAYERS.add(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onCurrencyItemPickup(EntityItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && ConfluenceMoney.isBridgeCurrencyStack(event.getItem().getItem())) {
            markCurrencyInventoryDirty(player);
        }
    }

    @SubscribeEvent
    public static void onCurrencyItemToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && ConfluenceMoney.isBridgeCurrencyStack(event.getEntity().getItem())) {
            markCurrencyInventoryDirty(player);
        }
    }

    @SubscribeEvent
    public static void onCurrencyRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player
                && ConfluenceMoney.isBridgeCurrencyStack(event.getItemStack())) {
            markCurrencyInventoryDirty(player);
        }
    }

    @SubscribeEvent
    public static void onCurrencyRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && ConfluenceMoney.isBridgeCurrencyStack(event.getItemStack())) {
            markCurrencyInventoryDirty(player);
        }
    }

    /**
     * QShop wallet changes are immediate. Player inventory scans happen after money-related
     * actions and as a low-frequency fallback for mutations that do not raise a Forge event.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || !ConfluenceCurrencyBridge.active()) {
            return;
        }
        boolean dirty = DIRTY_CURRENCY_PLAYERS.remove(player.getUUID());
        if (!dirty && player.tickCount % RECONCILE_FALLBACK_TICKS != 0) {
            return;
        }
        IWallet wallet = WalletCapability.get(player);
        if (wallet != null) {
            wallet.getBalance(BridgeConfig.currencyId());
        }
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
