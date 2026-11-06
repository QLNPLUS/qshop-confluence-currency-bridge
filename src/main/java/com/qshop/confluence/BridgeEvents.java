package com.qshop.confluence;

import com.mojang.logging.LogUtils;
import com.qshop.currency.Currency;
import com.qshop.currency.CurrencyRegistry;
import com.qshop.wallet.IWallet;
import com.qshop.wallet.WalletCapability;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Iterator;

/**
 * Forge 总线事件：配置缓存失效、背包余额同步、货币条目自动创建、指令注册。
 */
public final class BridgeEvents {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int RECONCILE_FALLBACK_TICKS = 100;
    private static final Set<UUID> DIRTY_CURRENCY_PLAYERS = new HashSet<>();
    private static final Map<UUID, double[]> DEATH_SNAPSHOT = new HashMap<>();

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


    /**
     * Confluence drops its coins inside {@link LivingDeathEvent}. Running at LOWEST guarantees we
     * see the inventory after that deduction, and reduces QShop's total by exactly the amount the
     * coins have lost. Without this hook the balance only catches up on the next inventory scan.
     *
     * {@code receiveCanceled = true} keeps the hook alive when another mod prevents the death
     * (PlayerRevive's bleeding state cancels it at HIGHEST). Confluence registers through PortLib
     * with {@code receiveCanceled = false}, so a cancelled death drops nothing and this reconcile is
     * a no-op; a mod that cancels later (LOW/LOWEST, e.g. totem style) would otherwise leave the
     * already-dropped coins unaccounted for until the next shop interaction.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void snapshotCashBeforeCoinDrop(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ConfluenceCurrencyBridge.active()) {
            return;
        }
        IWallet wallet = WalletCapability.get(player);
        if (wallet == null) {
            return;
        }
        // Read the raw total: a reconciling read here would itself apply a stale-mirror phantom credit
        // before the drop is even measured.
        IWallet raw = wallet instanceof ConfluenceWallet wrapped ? wrapped.unwrap() : wallet;
        DEATH_SNAPSHOT.put(player.getUUID(), new double[]{
                ConfluenceMoney.getCarried(player),
                raw.getBalance(BridgeConfig.currencyId()),
                BridgeConfig.includePiggyBank() ? ConfluenceMoney.getPiggyBank(player) : 0L});
    }

    /**
     * Confluence 的 {@code playerDropsMoney=false} 只关掉它自己的钱币掉落；原版死亡战利品仍可能带走
     * 主背包/钱币槽里的钱币。这里把这类掉落拦下来，但**不再放回玩家身上**：实测某些模组会“复制”
     * 而非移走钱币（同额同时存在于钱币槽和掉落物里），放回会把钱翻倍。
     *
     * <p>只把“确实从玩家身上消失”的那部分按钱包余额记回（钱包是唯一真相），因此既不会掉钱，
     * 也不会凭空多出钱。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !ConfluenceCurrencyBridge.active()
                || ConfluenceSupport.playerDropsMoney()) {
            return;
        }
        long intercepted = 0L;
        for (Iterator<ItemEntity> iterator = event.getDrops().iterator(); iterator.hasNext(); ) {
            ItemEntity drop = iterator.next();
            ItemStack stack = drop.getItem();
            if (!ConfluenceMoney.isBridgeCurrencyStack(stack)) {
                continue;
            }
            intercepted += ConfluenceMoney.copperValue(stack);
            iterator.remove();
        }
        if (intercepted <= 0L) {
            return;
        }
        IWallet wallet = WalletCapability.get(player);
        if (wallet == null) {
            return;
        }
        IWallet raw = wallet instanceof ConfluenceWallet wrapped ? wrapped.unwrap() : wallet;
        String currencyId = BridgeConfig.currencyId();
        long carried = ConfluenceMoney.getCarried(player);
        long mirror = ConfluenceCurrencyFormat.toCopper(
                raw.getBalance(BridgeWalletData.cashKey(currencyId)));
        long missing = Math.max(0L, mirror - carried);
        if (missing > 0L) {
            ConfluenceCurrencyBridge.credit(player, missing);
        }
        LOGGER.info("QShop Confluence Bridge: {} 死亡时 Confluence 配置为不掉钱，已拦截钱币掉落 {} 铜"
                        + "（实际从身上消失 {} 铜，已记回钱包；未消失的部分是重复掉落，直接丢弃）",
                player.getName().getString(), intercepted, missing);
    }

    /**
     * Reconciles after Confluence has dropped its coins.
     *
     * <p>{@code MONITOR} is the last bus priority, and that matters: Confluence 1.2.8 drops at
     * {@code NORMAL}, but 1.2.11 registers {@code dropMoneyOnDeath} at {@code LOWEST} (PortLib), the same
     * priority the bridge used to reconcile at - so the listener order decided whether the drop was seen
     * at all. Reconciling at {@code MONITOR} always runs after any drop, whatever the Confluence version.</p>
     */
    @SubscribeEvent(priority = EventPriority.MONITOR, receiveCanceled = true)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ConfluenceCurrencyBridge.active()) {
            return;
        }
        reconcileAndReport(player, event.isCanceled() ? "死亡被其它模组取消（倒地）" : "死亡",
                event.isCanceled());
    }

    /**
     * Death-preventing mods also finish the kill when a downed player logs out
     * ({@code PlayerRevive} does this in a HIGHEST {@link PlayerEvent.PlayerLoggedOutEvent} handler),
     * so the coin drop can land while the player data is about to be written. Reconciling here keeps
     * the saved wallet in step with the drop instead of resurrecting the money on the next login.
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        DIRTY_CURRENCY_PLAYERS.remove(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player && ConfluenceCurrencyBridge.active()
                && player.isDeadOrDying()) {
            reconcileAndReport(player, "登出时被击杀", false);
        }
        DEATH_SNAPSHOT.remove(event.getEntity().getUUID());
    }

    /**
     * QShop copies the player's wallet during {@link PlayerEvent.Clone}. Reconciling again at LOWEST
     * (after that copy) makes the respawned player's cash mirror match their empty/kept inventory,
     * so a death can never leave the pre-death total behind.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath() && event.getEntity() instanceof ServerPlayer player) {
            reconcileBoundBalance(player);
        }
    }

    private static void reconcileBoundBalance(ServerPlayer player) {
        if (player == null || !ConfluenceCurrencyBridge.active()) {
            return;
        }
        // Reconcile only: death/respawn must not pull the reserve back out as fresh coins, otherwise a
        // player whose balance exceeds the coin slots would get the dropped coins back instantly.
        ConfluenceCurrencyBridge.reconcileOnly(player);
    }

    /** Current canonical total for the bound currency; reading it also performs a reconciliation. */
    private static double currentTotal(ServerPlayer player) {
        IWallet wallet = WalletCapability.get(player);
        return wallet == null ? Double.NaN : wallet.getBalance(BridgeConfig.currencyId());
    }

    /** Reconciles, then reports the physical/canonical change so a server log can pin down a death. */
    private static void reconcileAndReport(ServerPlayer player, String cause, boolean alwaysReport) {
        double[] snapshot = DEATH_SNAPSHOT.remove(player.getUUID());
        if (snapshot != null) {
            repairLedgerBaseline(player, snapshot);
        }
        long copperBefore = snapshot != null ? (long) snapshot[0] : ConfluenceMoney.getCarried(player);
        double totalBefore = snapshot != null ? snapshot[1] : currentTotal(player);
        reconcileBoundBalance(player);
        long copperAfter = ConfluenceMoney.getCarried(player);
        double totalAfter = currentTotal(player);
        if (alwaysReport || copperBefore != copperAfter || Double.compare(totalBefore, totalAfter) != 0) {
            LOGGER.info("QShop Confluence Bridge: {} {}：携带钱币 {} → {} 铜（掉落 {}），QShop 总额 {} → {}",
                    player.getName().getString(), cause, copperBefore, copperAfter,
                    copperBefore - copperAfter, totalBefore, totalAfter);
        }
    }

    /**
     * The drop must be measured against the coins that were really in the slots a moment ago.
     * If a stale ledger mirror disagrees with that observation, the difference would silently vanish
     * (no deduction) and the following rebalance would mint the very coins Confluence just dropped.
     */
    private static void repairLedgerBaseline(ServerPlayer player, double[] snapshot) {
        IWallet wallet = WalletCapability.get(player);
        if (wallet == null) {
            return;
        }
        IWallet raw = wallet instanceof ConfluenceWallet wrapped ? wrapped.unwrap() : wallet;
        String currencyId = BridgeConfig.currencyId();
        double cashMirror = raw.getBalance(BridgeWalletData.cashKey(currencyId));
        if (Double.compare(cashMirror, snapshot[0]) != 0) {
            LOGGER.info("QShop Confluence Bridge: {} 的钱币镜像 {} 与实际携带 {} 不符，按实际值修正后再对账",
                    player.getName().getString(), cashMirror, (long) snapshot[0]);
            raw.setBalance(BridgeWalletData.cashKey(currencyId), snapshot[0]);
        }
        double piggyMirror = raw.getBalance(BridgeWalletData.piggyKey(currencyId));
        if (Double.compare(piggyMirror, snapshot[2]) != 0) {
            raw.setBalance(BridgeWalletData.piggyKey(currencyId), snapshot[2]);
        }
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
        // Reconcile only: the passive fallback must never re-materialise reserve, or a respawned player
        // whose balance exceeds the coin slots would receive the coins they just dropped.
        ConfluenceCurrencyBridge.reconcileOnly(player);
    }

    /** 启动完成后确保绑定货币在 QShop 货币表里存在。 */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!ConfluenceSupport.qshopPresent() || !ConfluenceCurrencyBridge.active()) {
            return;
        }
        LOGGER.info("QShop Confluence Bridge: 死亡掉落策略 playerDropsMoney={}（桥接据此同步钱币掉落）",
                ConfluenceSupport.playerDropsMoney());
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
