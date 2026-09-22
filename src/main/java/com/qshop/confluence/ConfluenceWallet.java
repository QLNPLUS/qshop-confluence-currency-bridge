package com.qshop.confluence;

import com.qshop.wallet.IWallet;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * QShop 钱包的包装层：绑定货币走 Confluence 钱币，其它货币原样转发给底层钱包。
 *
 * <p>包装对象由一个 Mixin 注入 {@code WalletCapability.get(Player)} 的返回值产生，
 * 所以 QShop 所有读写余额的代码路径（交易、指令、FTB、KubeJS、商店界面同步）
 * 都会经过这里，不需要改 QShop 一行源码。</p>
 *
 * <p>注意 {@code add}/{@code take}/{@code has} 必须在这里自己实现：底层
 * {@code WalletImpl} 的同名方法只会读它自己的 map，绕过 <em>本包装</em> 的读写。</p>
 */
public final class ConfluenceWallet implements IWallet {

    private static final double EPSILON = 1e-9D;

    private final Player player;
    private final IWallet base;

    public ConfluenceWallet(Player player, IWallet base) {
        this.player = player;
        this.base = base;
    }

    private boolean bound(String currencyId) {
        return ConfluenceCurrencyBridge.bound(currencyId);
    }

    @Override
    public double getBalance(String currencyId) {
        if (!bound(currencyId)) {
            return base.getBalance(currencyId);
        }
        // 钱包里为绑定货币保存的是小数余量；整数部分无意义（见 fractionOf 注释）。
        return ConfluenceCurrencyBridge.readBalance(player, ConfluenceCurrencyBridge.fractionOf(base.getBalance(currencyId)));
    }

    @Override
    public void setBalance(String currencyId, double amount) {
        if (!bound(currencyId)) {
            base.setBalance(currencyId, amount);
            return;
        }
        ConfluenceCurrencyBridge.writeBalance(player, amount);
        // 余量写回底层钱包，跟随玩家数据持久化。
        base.setBalance(currencyId, ConfluenceCurrencyBridge.fractionOf(amount));
    }

    @Override
    public double add(String currencyId, double amount) {
        double next = getBalance(currencyId) + amount;
        setBalance(currencyId, next);
        return next;
    }

    @Override
    public boolean take(String currencyId, double amount) {
        double balance = getBalance(currencyId);
        if (balance + EPSILON < amount) {
            return false;
        }
        setBalance(currencyId, balance - amount);
        return true;
    }

    @Override
    public boolean has(String currencyId, double amount) {
        return getBalance(currencyId) + EPSILON >= amount;
    }

    @Override
    public Map<String, Double> snapshot() {
        Map<String, Double> snapshot = new LinkedHashMap<>(base.snapshot());
        String boundId = BridgeConfig.currencyId();
        if (boundId != null && !boundId.isEmpty() && bound(boundId)) {
            snapshot.put(boundId, getBalance(boundId));
        }
        return snapshot;
    }

    @Override
    public int getLimitCount(String key, String period) {
        return base.getLimitCount(key, period);
    }

    @Override
    public void addLimitCount(String key, int amount, String period) {
        base.addLimitCount(key, amount, period);
    }

    @Override
    public void clearLimitCount(String key) {
        base.clearLimitCount(key);
    }

    @Override
    public void copyFrom(IWallet other) {
        IWallet source = other instanceof ConfluenceWallet wallet ? wallet.base : other;
        base.copyFrom(source);
    }

    /** 底层钱包实例（调试 / 诊断用）。 */
    public IWallet unwrap() {
        return base;
    }
}
