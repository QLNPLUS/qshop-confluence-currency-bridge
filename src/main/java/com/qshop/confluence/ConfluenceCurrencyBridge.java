package com.qshop.confluence;

import com.mojang.logging.LogUtils;
import com.qshop.wallet.IWallet;
import com.qshop.wallet.WalletCapability;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

/** QShop's bound currency total, carried Confluence coins, and QShop reserve. */
public final class ConfluenceCurrencyBridge {

    public static final double MAX_SUPPORTED_BALANCE = Double.MAX_VALUE;
    private static final Logger LOGGER = LogUtils.getLogger();

    private ConfluenceCurrencyBridge() {
    }

    /** Feature is available only when both mods are loaded and a currency id is configured. */
    public static boolean active() {
        return ConfluenceSupport.isLoaded() && !BridgeConfig.currencyId().isEmpty();
    }

    public static boolean bound(String currencyId) {
        return active() && currencyId != null && !currencyId.isEmpty()
                && currencyId.equals(BridgeConfig.currencyId());
    }

    /** Previous bridge versions persisted only the fractional copper remainder. */
    public static double fractionOf(double storedValue) {
        if (!Double.isFinite(storedValue)) {
            return 0.0D;
        }
        double fraction = storedValue - Math.floor(storedValue);
        return fraction > 0.0D && fraction < 1.0D ? fraction : 0.0D;
    }

    /** Returns the total QShop balance after importing external inventory changes. */
    public static double readBalance(Player player, IWallet wallet, String currencyId) {
        if (player == null || wallet == null || currencyId == null) {
            return 0.0D;
        }
        initializeIfNeeded(player, wallet, currencyId);
        absorbLegacyPending(player, wallet, currencyId);
        normalizeCanonicalBalance(wallet, currencyId);
        reconcileExternalChanges(player, wallet, currencyId);
        rebalanceCash(player, wallet, currencyId);
        return sanitize(wallet.getBalance(currencyId));
    }

    /** Money held on the menu cursor cannot be spent by a wallet transaction. */
    public static double cursorFloor(Player player) {
        return (double) Math.max(0L,
                ConfluenceMoney.getCarried(player) - ConfluenceMoney.getInventoryCash(player));
    }

    /** Sets the canonical total, then mirrors all physically carryable value as coin items. */
    public static void writeBalance(Player player, IWallet wallet, String currencyId, double amount) {
        if (player == null || wallet == null || currencyId == null) {
            return;
        }
        initializeIfNeeded(player, wallet, currencyId);
        absorbLegacyPending(player, wallet, currencyId);
        reconcileExternalChanges(player, wallet, currencyId);

        double target = sanitize(amount);
        long cursorCash = ConfluenceMoney.getCarried(player) - ConfluenceMoney.getInventoryCash(player);
        target = Math.max(target, (double) Math.max(0L, cursorCash));
        wallet.setBalance(currencyId, target);
        rebalanceCash(player, wallet, currencyId);
    }

    /** Credits through the canonical wallet so full inventories never drop the payout. */
    public static boolean credit(ServerPlayer player, long copper) {
        if (player == null || copper <= 0L || !active()) {
            return false;
        }
        String currencyId = BridgeConfig.currencyId();
        IWallet wallet = WalletCapability.get(player);
        if (wallet != null) {
            wallet.add(currencyId, Math.min((double) copper, MAX_SUPPORTED_BALANCE));
            return true;
        }
        return false;
    }

    private static void initializeIfNeeded(Player player, IWallet wallet, String currencyId) {
        if (BridgeWalletData.initialized(wallet, currencyId)) {
            return;
        }

        long cash = ConfluenceMoney.getCarried(player);
        long piggy = BridgeConfig.includePiggyBank() ? ConfluenceMoney.getPiggyBank(player) : 0L;
        long pending = legacyPending(player);
        double oldFraction = fractionOf(wallet.getBalance(currencyId));
        double initial = (double) cash + (double) piggy + oldFraction + (double) pending;
        wallet.setBalance(currencyId, sanitize(initial));
        BridgeWalletData.markInitialized(wallet, currencyId, cash, piggy);
        if (pending > 0L) {
            clearLegacyPending(player);
        }
    }

    private static void absorbLegacyPending(Player player, IWallet wallet, String currencyId) {
        double absorbed = wallet.getBalance(BridgeWalletData.absorbedPendingKey(currencyId));
        if (absorbed <= 0.0D) {
            return;
        }
        long expected = ConfluenceCurrencyFormat.toCopper(absorbed);
        long pending = legacyPending(player);
        if (pending > 0L) {
            clearLegacyPending(player);
            if (pending > expected) {
                wallet.add(currencyId, (double) (pending - expected));
            }
        }
        wallet.setBalance(BridgeWalletData.absorbedPendingKey(currencyId), 0.0D);
    }

    private static void reconcileExternalChanges(Player player, IWallet wallet, String currencyId) {
        long cash = ConfluenceMoney.getCarried(player);
        long piggy = BridgeConfig.includePiggyBank() ? ConfluenceMoney.getPiggyBank(player) : 0L;
        long previousCash = ConfluenceCurrencyFormat.toCopper(
                wallet.getBalance(BridgeWalletData.cashKey(currencyId)));
        long previousPiggy = ConfluenceCurrencyFormat.toCopper(
                wallet.getBalance(BridgeWalletData.piggyKey(currencyId)));
        long cashDelta = difference(cash, previousCash);
        long piggyDelta = difference(piggy, previousPiggy);
        if (cashDelta != 0L || piggyDelta != 0L) {
            double updated = wallet.getBalance(currencyId) + (double) cashDelta + (double) piggyDelta;
            wallet.setBalance(currencyId, sanitize(updated));
        }
        wallet.setBalance(BridgeWalletData.cashKey(currencyId), cash);
        wallet.setBalance(BridgeWalletData.piggyKey(currencyId), piggy);
    }

    private static void normalizeCanonicalBalance(IWallet wallet, String currencyId) {
        double current = wallet.getBalance(currencyId);
        double normalized = sanitize(current);
        if (Double.compare(current, normalized) != 0) {
            wallet.setBalance(currencyId, normalized);
        }
    }

    private static void rebalanceCash(Player player, IWallet wallet, String currencyId) {
        long total = ConfluenceCurrencyFormat.toCopper(wallet.getBalance(currencyId));
        long piggy = BridgeConfig.includePiggyBank() ? ConfluenceMoney.getPiggyBank(player) : 0L;
        if (BridgeConfig.includePiggyBank() && piggy > total) {
            // A QShop withdrawal can spend piggy-bank value too. Keep the physical bank
            // from becoming larger than the canonical total.
            ConfluenceMoney.setPiggyBank(player, total);
            piggy = total;
        }
        long availableForCash = Math.max(0L, total - piggy);
        long carried = ConfluenceMoney.rebalanceCarried(player, availableForCash);
        wallet.setBalance(BridgeWalletData.cashKey(currencyId), carried);
        wallet.setBalance(BridgeWalletData.piggyKey(currencyId), piggy);
    }

    private static long legacyPending(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return 0L;
        }
        MinecraftServer server = serverPlayer.getServer();
        return server == null ? 0L : Math.max(0L, PendingMoney.get(server).peek(player.getUUID()));
    }

    private static void clearLegacyPending(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        MinecraftServer server = serverPlayer.getServer();
        if (server == null) {
            return;
        }
        PendingMoney.get(server).take(player.getUUID());
    }

    private static long difference(long current, long previous) {
        try {
            return Math.subtractExact(current, previous);
        } catch (ArithmeticException ignored) {
            return current >= previous ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private static double sanitize(double amount) {
        if (Double.isNaN(amount) || amount <= 0.0D) {
            return 0.0D;
        }
        if (!Double.isFinite(amount)) {
            LOGGER.warn("QShop Confluence Bridge: 收到无法表示的余额 {}，限制到上限 {}", amount,
                    MAX_SUPPORTED_BALANCE);
            return MAX_SUPPORTED_BALANCE;
        }
        if (amount > MAX_SUPPORTED_BALANCE) {
            LOGGER.warn("QShop Confluence Bridge: 余额 {} 超出支持范围，限制到上限 {}", amount,
                    MAX_SUPPORTED_BALANCE);
            return MAX_SUPPORTED_BALANCE;
        }
        return amount;
    }

}
