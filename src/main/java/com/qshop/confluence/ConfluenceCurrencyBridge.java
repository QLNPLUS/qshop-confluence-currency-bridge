package com.qshop.confluence;

import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * 绑定货币的读写逻辑：QShop 的一个货币 id ↔ Confluence 身上的钱币。
 *
 * <p>QShop 的钱包是 {@code Map<String, Double>}，Confluence 的钱是一堆钱币物品。
 * 为了让两边"实时"一致，这里不镜像余额，而是每次读写都直接落到钱币物品上，
 * 钱包里只为绑定 id 保留一个 [0,1) 的小数余量（QShop 价格是 double，
 * Confluence 只能整铜币，余量这样累积不会丢失）。</p>
 */
public final class ConfluenceCurrencyBridge {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ConfluenceCurrencyBridge() {
    }

    /** 功能是否可用：Confluence 在场且配置了货币 id。 */
    public static boolean active() {
        return ConfluenceSupport.isLoaded() && !BridgeConfig.currencyId().isEmpty();
    }

    /** 该货币 id 是否被绑定到 Confluence 钱币。 */
    public static boolean bound(String currencyId) {
        return active() && currencyId != null && !currencyId.isEmpty()
                && currencyId.equals(BridgeConfig.currencyId());
    }

    /**
     * 从钱包里存的值取出小数余量。
     *
     * <p>整数部分一律丢弃：旧存档 / {@code copyFrom} 可能写进一个完整的余额，
     * 那种值应该被忽略，否则会被当成余量重复计入。</p>
     */
    public static double fractionOf(double storedValue) {
        if (!Double.isFinite(storedValue)) {
            return 0.0D;
        }
        double fraction = storedValue - Math.floor(storedValue);
        return fraction > 0.0D && fraction < 1.0D ? fraction : 0.0D;
    }

    /** 读余额：Confluence 身上的钱（含/不含存钱罐由配置决定）+ 小数余量。 */
    public static double readBalance(Player player, double storedFraction) {
        if (player == null) {
            return storedFraction;
        }
        boolean withPiggyBank = BridgeConfig.includePiggyBank();
        long money = ConfluenceMoney.get(player, withPiggyBank);
        return (double) money + storedFraction;
    }

    /** 写余额：把玩家身上的钱币调整到 amount 对应的铜币数（整数部分）。 */
    public static void writeBalance(Player player, double amount) {
        if (player == null) {
            return;
        }
        boolean withPiggyBank = BridgeConfig.includePiggyBank();
        long target = ConfluenceCurrencyFormat.toCopper(amount);
        long current = ConfluenceMoney.get(player, withPiggyBank);
        if (target == current) {
            return;
        }
        if (current > 0L && !ConfluenceMoney.clear(player, withPiggyBank)) {
            LOGGER.warn("QShop Confluence Bridge: 清空 {} 身上的钱币失败，余额可能不准", player.getName().getString());
        }
        if (target > 0L) {
            ConfluenceMoney.give(player, target);
        }
    }

    /** 给绑定货币加钱（离线结算补发等场景复用）。 */
    public static void credit(Player player, long copper) {
        if (player == null || copper <= 0L) {
            return;
        }
        ConfluenceMoney.give(player, copper);
    }
}
