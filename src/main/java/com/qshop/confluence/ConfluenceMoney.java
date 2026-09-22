package com.qshop.confluence;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.confluence.mod.util.PlayerUtils;

/**
 * Confluence 钱币读写的唯一直接调用点。
 *
 * <p>这个类只在确认 Confluence 存在后才会被加载，因此这里可以直接引用
 * Confluence 的类，不需要反射。</p>
 *
 * <p>只依赖 Confluence 三个稳定入口：{@code getMoney} / {@code tryCostMoney} /
 * {@code decodeCoin}，1.20.1 与 1.21.1 分支的签名完全一致。</p>
 */
public final class ConfluenceMoney {

    private ConfluenceMoney() {
    }

    /** 玩家持有的铜币总额。withPiggyBank 为 true 时包含存钱罐。 */
    public static long get(Player player, boolean withPiggyBank) {
        return PlayerUtils.getMoney(player, withPiggyBank);
    }

    /**
     * 清空玩家身上的钱币。
     *
     * <p>Confluence 的 {@code tryCostMoney} 会先清掉背包与钱币栏里所有钱币，
     * 再把找零重新按面额发回，所以只要 cost 等于当前总额就等于"归零"。</p>
     */
    public static boolean clear(Player player, boolean withPiggyBank) {
        long have = PlayerUtils.getMoney(player, withPiggyBank);
        if (have <= 0L) {
            return true;
        }
        return PlayerUtils.tryCostMoney(player, have, withPiggyBank);
    }

    /** 按面额把钱币发到背包；背包放不下就掉在脚边（与 Confluence 行为一致）。 */
    public static void give(Player player, long amount) {
        if (amount <= 0L) {
            return;
        }
        for (var entry : PlayerUtils.decodeCoin(amount).copper2PlatinumEntries()) {
            int count = entry.getIntValue();
            if (count <= 0) {
                continue;
            }
            ItemStack stack = new ItemStack(entry.getKey(), count);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }
}
