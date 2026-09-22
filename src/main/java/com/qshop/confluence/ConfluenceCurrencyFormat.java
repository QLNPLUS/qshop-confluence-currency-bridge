package com.qshop.confluence;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Confluence 面额文本。
 *
 * <p>Confluence 的钱是一个以铜币为单位的整数，100 铜 = 1 银，100 银 = 1 金，
 * 100 金 = 1 铂金。这里把它拆成"n 铂金币 n 金币 n 银币 n 铜币"，
 * 币名直接取 Confluence 物品的翻译键，因此跟随客户端语言。</p>
 */
public final class ConfluenceCurrencyFormat {

    public static final long COPPER_PER_SILVER = 100L;
    public static final long COPPER_PER_GOLD = 100L * 100L;
    public static final long COPPER_PER_PLATINUM = 100L * 100L * 100L;

    /** 从大到小；与 Confluence 的 CoinItem 面额一一对应。 */
    private static final String[] UNIT_KEYS = {
            "block.confluence.platinum_coin",
            "block.confluence.gold_coin",
            "block.confluence.silver_coin",
            "block.confluence.copper_coin",
    };
    private static final long[] UNIT_VALUES = {
            COPPER_PER_PLATINUM,
            COPPER_PER_GOLD,
            COPPER_PER_SILVER,
            1L,
    };

    /** 极值保护：超过这个铜币数就不再是 long 能精确表示的范围。 */
    private static final double MAX_COPPER = 9.0E18D;

    private ConfluenceCurrencyFormat() {
    }

    /** double 金额 → 铜币整数（向下取整，负数与 NaN 归零）。 */
    public static long toCopper(double amount) {
        if (!(amount > 0.0D) || Double.isNaN(amount)) {
            return 0L;
        }
        if (amount >= MAX_COPPER) {
            return Long.MAX_VALUE;
        }
        return (long) Math.floor(amount);
    }

    /** 把铜币总额拆成 Confluence 面额文本（可实现本地化，用于聊天/提示消息）。 */
    public static Component toComponent(double amount) {
        long copper = toCopper(amount);
        MutableComponent result = Component.empty();
        long remaining = copper;
        boolean first = true;
        for (int i = 0; i < UNIT_VALUES.length; i++) {
            long unit = UNIT_VALUES[i];
            long count = remaining / unit;
            remaining -= count * unit;
            if (count <= 0) {
                continue;
            }
            if (!first) {
                result.append(Component.literal(" "));
            }
            first = false;
            result.append(Component.literal(Long.toString(count) + " "))
                    .append(Component.translatable(UNIT_KEYS[i]));
        }
        if (first) {
            // 总额为 0：只显示"0 铜币"
            result.append(Component.literal("0 ")).append(Component.translatable(UNIT_KEYS[3]));
        }
        return result;
    }

    /**
     * 纯文本版本，给只接受 String 的接口用（例如出售箱的 {@code PriceQuote#formattedPrice}）。
     * 在客户端调用会按客户端语言解析。
     */
    public static String toPlainString(double amount) {
        return toComponent(amount).getString();
    }
}
