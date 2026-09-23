package com.qshop.confluence;

import com.qshop.wallet.IWallet;

/** Reserved QShop wallet entries used by the bridge's cash/reserve ledger. */
public final class BridgeWalletData {

    public static final String INTERNAL_PREFIX = "__qshop_confluence_bridge_v2.";
    private static final double VERSION = 2.0D;

    private BridgeWalletData() {
    }

    public static String versionKey(String currencyId) {
        return key(currencyId, "version");
    }

    public static String cashKey(String currencyId) {
        return key(currencyId, "cash");
    }

    public static String piggyKey(String currencyId) {
        return key(currencyId, "piggy");
    }

    /** Legacy offline payouts already merged while migrating an old player.dat file. */
    public static String absorbedPendingKey(String currencyId) {
        return key(currencyId, "absorbed_pending");
    }

    private static String key(String currencyId, String suffix) {
        return INTERNAL_PREFIX + currencyId + "." + suffix;
    }

    public static boolean initialized(IWallet wallet, String currencyId) {
        return wallet.getBalance(versionKey(currencyId)) == VERSION;
    }

    public static void markInitialized(IWallet wallet, String currencyId,
                                       long cash, long piggyBank, long absorbedPending) {
        wallet.setBalance(versionKey(currencyId), VERSION);
        wallet.setBalance(cashKey(currencyId), Math.max(0L, cash));
        wallet.setBalance(piggyKey(currencyId), Math.max(0L, piggyBank));
        if (absorbedPending > 0L) {
            wallet.setBalance(absorbedPendingKey(currencyId), absorbedPending);
        }
    }

    public static void markInitialized(IWallet wallet, String currencyId,
                                       long cash, long piggyBank) {
        markInitialized(wallet, currencyId, cash, piggyBank, 0L);
    }

    public static boolean isInternalKey(String key) {
        return key != null && key.startsWith(INTERNAL_PREFIX);
    }
}
