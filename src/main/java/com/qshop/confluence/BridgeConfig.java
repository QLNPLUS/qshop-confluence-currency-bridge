package com.qshop.confluence;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 通用配置。值在配置加载/重载后缓存，避免每次读写余额都触碰配置系统，
 * 也避免 Mixin 配置准备阶段读取尚未加载的配置时抛异常。
 */
public final class BridgeConfig {

    public static final String DEFAULT_CURRENCY_ID = "coins";

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.ConfigValue<String> CURRENCY_ID;
    private static final ForgeConfigSpec.BooleanValue INCLUDE_PIGGY_BANK;
    private static final ForgeConfigSpec.BooleanValue OFFLINE_PAYOUT;
    private static final ForgeConfigSpec.BooleanValue SKIP_DEATH_RETENTION;
    private static final ForgeConfigSpec.BooleanValue SELLBOX_PRICE_FORMAT;
    private static final ForgeConfigSpec.BooleanValue AUTO_CREATE_CURRENCY;
    private static final ForgeConfigSpec.ConfigValue<String> AUTO_CREATE_NAME;

    private static volatile String currencyId;
    private static volatile Boolean includePiggyBank;
    private static volatile Boolean offlinePayout;
    private static volatile Boolean skipDeathRetention;
    private static volatile Boolean sellboxPriceFormat;
    private static volatile Boolean autoCreateCurrency;
    private static volatile String autoCreateName;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("QShop 货币 ↔ Confluence 钱币绑定设置",
                "QShop currency id bound to Confluence coin money").push("bridge");

        CURRENCY_ID = builder
                .comment("要绑定到 Confluence 钱币的 QShop 货币 id。",
                        "填 coins 就是默认的金币条目；也可以填自定义 id（配合 autoCreateCurrency 自动建条目）。",
                        "The QShop currency id bound to Confluence coin money.")
                .define("currencyId", DEFAULT_CURRENCY_ID,
                        value -> value instanceof String s && !s.isBlank());

        INCLUDE_PIGGY_BANK = builder
                .comment("余额是否包含存钱罐里的钱（和 Confluence NPC 交易一致）。",
                        "true  = 背包 + 钱币栏 + 存钱罐；",
                        "false = 只算身上的钱（背包 + 钱币栏），存钱罐里的存款商店不会动。",
                        "Whether the balance includes money stored in the piggy bank.")
                .define("includePiggyBank", true);

        OFFLINE_PAYOUT = builder
                .comment("玩家离线时（例如出售箱离线收益）记账，登录时以钱币形式补发。",
                        "关闭时离线入账会被丢弃。",
                        "Queue bound-currency earnings for offline players and pay them as coins on login.")
                .define("offlinePayout", true);

        SKIP_DEATH_RETENTION = builder
                .comment("忽略 QShop 的死亡货币扣除（death.loseCurrencyOnDeath）对该绑定货币的作用。",
                        "Confluence 自己已经处理死亡掉落钱币，两边同时扣会扣两次，所以默认忽略。",
                        "Skip QShop currency-on-death retention for the bound currency.")
                .define("skipDeathRetention", true);

        AUTO_CREATE_CURRENCY = builder
                .comment("启动时若 QShop 货币表里没有该 id，自动创建一个同名条目。",
                        "Create the QShop currency entry automatically when it is missing.")
                .define("autoCreateCurrency", true);

        AUTO_CREATE_NAME = builder
                .comment("自动创建货币条目时使用的显示名。",
                        "Display name used when auto-creating the QShop currency entry.")
                .define("autoCreateCurrencyName", "钱币",
                        value -> value instanceof String s && !s.isBlank());

        builder.pop();

        builder.comment("出售箱价格显示 / Sell box price display").push("sellbox");

        SELLBOX_PRICE_FORMAT = builder
                .comment("出售箱显示该绑定货币的价格时，改用 Confluence 面额格式",
                        "（例如 1234567 铜 → 1 铂金币 23 金币 45 银币 67 铜币）。",
                        "Show bound-currency prices in the sell box using Confluence coin denominations.")
                .define("confluencePriceFormat", true);

        builder.pop();

        SPEC = builder.build();
    }

    private BridgeConfig() {
    }

    /** 配置加载或重载后调用，丢弃缓存。 */
    public static void invalidate() {
        currencyId = null;
        includePiggyBank = null;
        offlinePayout = null;
        skipDeathRetention = null;
        sellboxPriceFormat = null;
        autoCreateCurrency = null;
        autoCreateName = null;
    }

    public static String currencyId() {
        String cached = currencyId;
        if (cached == null) {
            cached = read(CURRENCY_ID, DEFAULT_CURRENCY_ID);
            if (cached == null || cached.isBlank()) {
                cached = DEFAULT_CURRENCY_ID;
            }
            currencyId = cached;
        }
        return cached;
    }

    public static boolean includePiggyBank() {
        Boolean cached = includePiggyBank;
        if (cached == null) {
            cached = read(INCLUDE_PIGGY_BANK, Boolean.TRUE);
            includePiggyBank = cached;
        }
        return cached;
    }

    public static boolean offlinePayout() {
        Boolean cached = offlinePayout;
        if (cached == null) {
            cached = read(OFFLINE_PAYOUT, Boolean.TRUE);
            offlinePayout = cached;
        }
        return cached;
    }

    public static boolean skipDeathRetention() {
        Boolean cached = skipDeathRetention;
        if (cached == null) {
            cached = read(SKIP_DEATH_RETENTION, Boolean.TRUE);
            skipDeathRetention = cached;
        }
        return cached;
    }

    public static boolean sellboxPriceFormat() {
        Boolean cached = sellboxPriceFormat;
        if (cached == null) {
            cached = read(SELLBOX_PRICE_FORMAT, Boolean.TRUE);
            sellboxPriceFormat = cached;
        }
        return cached;
    }

    public static boolean autoCreateCurrency() {
        Boolean cached = autoCreateCurrency;
        if (cached == null) {
            cached = read(AUTO_CREATE_CURRENCY, Boolean.TRUE);
            autoCreateCurrency = cached;
        }
        return cached;
    }

    public static String autoCreateCurrencyName() {
        String cached = autoCreateName;
        if (cached == null) {
            cached = read(AUTO_CREATE_NAME, "钱币");
            if (cached == null || cached.isBlank()) {
                cached = "钱币";
            }
            autoCreateName = cached;
        }
        return cached;
    }

    /** 配置尚未加载时 get() 会抛异常，这里统一退化成默认值。 */
    private static <T> T read(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            T read = value.get();
            return read == null ? fallback : read;
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
