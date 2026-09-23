package com.qshop.confluence;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * QShop × Confluence 货币互通。
 *
 * <p>把一个 QShop 货币 id 绑定到 Confluence 的钱币系统上：</p>
 * <ul>
 *   <li>QShop 保存绑定货币的总余额，背包可容纳的部分显示为 Confluence 钱币；</li>
 *   <li>钱币超出当前背包容量的部分作为 QShop 储备，离线交易也走同一总账；</li>
 *   <li>钱币相关背包操作后同步到 QShop 总余额，并定期兜底检查；</li>
 *   <li>出售箱显示该货币的价格时改用 Confluence 面额格式。</li>
 * </ul>
 *
 * <p>本模组不修改 QShop 源码，全部通过 Mixin 接入 QShop 的钱包入口。</p>
 */
@Mod(QShopConfluenceMod.MODID)
public class QShopConfluenceMod {

    public static final String MODID = "qshop_confluence";
    public static final Logger LOGGER = LogUtils.getLogger();

    public QShopConfluenceMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(BridgeEvents::onConfigLoading);
        modEventBus.addListener(BridgeEvents::onConfigReloading);
        modContainer.registerConfig(ModConfig.Type.COMMON, BridgeConfig.SPEC,
                "qshop-confluence-common.toml");
        NeoForge.EVENT_BUS.register(BridgeEvents.class);
        logStartupState();
    }

    private void logStartupState() {
        boolean confluence = ConfluenceSupport.isLoaded();
        boolean qshop = ConfluenceSupport.qshopPresent();
        boolean sellbox = ConfluenceSupport.sellboxPresent();
        LOGGER.info("QShop Confluence Bridge: qshop={}, confluence={}, sellbox={}, currencyId='{}', includePiggyBank={}",
                qshop, confluence, sellbox, BridgeConfig.currencyId(), BridgeConfig.includePiggyBank());
        if (!confluence) {
            LOGGER.warn("QShop Confluence Bridge: 未检测到 Confluence，货币绑定不生效，配置的货币 id 会退化成普通 QShop 钱包货币。");
        }
        if (!sellbox) {
            LOGGER.info("QShop Confluence Bridge: 未检测到出售箱，价格格式兼容不会启用。");
        }
        verifyMixinTargetsLoaded();
    }

    /**
     * 主动加载混入目标类，让混入在启动阶段就应用；同时校验混入插件当初的判断。
     *
     * <p>插件在 Mixin prepare 阶段只能用不加载类的方式探测模组是否存在，万一那种探测
     * 失效（例如类加载器结构特殊），混入会被静默跳过。这里在 Mixin 已经就绪之后再用
     * 类加载复核一次，不一致就明确告警，而不是让玩家遇到"钱不生效"却查不出原因。</p>
     */
    private void verifyMixinTargetsLoaded() {
        checkTarget("com.qshop.wallet.WalletCapability");
        checkTarget("com.qshop.api.CurrencyService");
        if (ConfluenceSupport.sellboxPresent()) {
            checkTarget("com.qshop.sellbox.PriceQuote");
        }
    }

    private void checkTarget(String className) {
        try {
            Class.forName(className, false, QShopConfluenceMod.class.getClassLoader());
        } catch (Throwable throwable) {
            LOGGER.warn("QShop Confluence Bridge: 混入目标类 {} 无法加载，相关功能不会生效", className, throwable);
        }
    }
}
