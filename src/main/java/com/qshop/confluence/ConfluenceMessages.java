package com.qshop.confluence;

import com.qshop.currency.CurrencyRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 出售箱出售/补发消息的价格改写。
 *
 * <p>出售箱的服务端消息是
 * {@code Component.translatable(key, ..., 价格字符串, 货币显示名)}，
 * 消息里只有货币的<em>显示名</em>，所以这里拿绑定货币的显示名做匹配。
 * 匹配不上就原样返回，绝不猜。</p>
 */
public final class ConfluenceMessages {

    private ConfluenceMessages() {
    }

    /**
     * 把 {@code Component.translatable(key, args)} 里绑定货币的价格换成 Confluence 面额文本。
     *
     * @param key         调用方原本的翻译键
     * @param args        调用方原本的参数
     * @param expectedKey 只有这个键的消息才改写
     */
    public static MutableComponent rewrite(String key, Object[] args, String expectedKey) {
        if (!BridgeConfig.sellboxPriceFormat() || !expectedKey.equals(key)) {
            return Component.translatable(key, args);
        }
        if (!ConfluenceCurrencyBridge.active()) {
            return Component.translatable(key, args);
        }
        if (args == null || args.length < 2) {
            return Component.translatable(key, args);
        }
        Object last = args[args.length - 1];
        if (!(last instanceof String currencyLabel) || !isBoundLabel(currencyLabel)) {
            return Component.translatable(key, args);
        }
        Object priceArg = args[args.length - 2];
        Double price = parsePrice(priceArg);
        if (price == null) {
            return Component.translatable(key, args);
        }
        Object[] rewritten = args.clone();
        rewritten[rewritten.length - 2] = ConfluenceCurrencyFormat.toComponent(price);
        rewritten[rewritten.length - 1] = "";
        return Component.translatable(key, rewritten);
    }

    private static boolean isBoundLabel(String label) {
        String boundId = BridgeConfig.currencyId();
        if (label.equals(boundId)) {
            return true;
        }
        try {
            String expected = CurrencyRegistry.displayName(boundId);
            return expected != null && !expected.isBlank() && expected.equals(label);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Double parsePrice(Object priceArg) {
        if (priceArg instanceof Number number) {
            return number.doubleValue();
        }
        if (priceArg instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
