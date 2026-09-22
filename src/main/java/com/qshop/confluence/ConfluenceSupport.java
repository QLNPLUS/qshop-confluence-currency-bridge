package com.qshop.confluence;

/**
 * 模组存在性探测。
 *
 * <p><b>存在两条完全不同的探测路径，不要混用：</b></p>
 * <ul>
 *   <li>{@link #modPresent} — 给 Mixin 配置准备阶段用。它<em>不能</em>加载目标类：
 *       在 prepare 阶段把目标类定义出来，会让这个类在 Mixin 准备好之前就被加载，
 *       结果就是混入永远不会应用（而且不报错）。所以这里走资源查找/模组列表。</li>
 *   <li>{@link #classPresent} — 运行时用，直接类加载，结果会缓存。</li>
 * </ul>
 *
 * <p>Confluence / 出售箱都按“可选”处理：缺少时相关功能静默关闭，而不是启动崩溃。</p>
 */
public final class ConfluenceSupport {

    public static final String QSHOP_PROBE = "com/qshop/wallet/WalletCapability.class";
    public static final String SELLBOX_PROBE = "com/qshop/sellbox/PriceQuote.class";
    public static final String CONFLUENCE_PROBE = "org/confluence/mod/util/PlayerUtils.class";

    private static volatile Boolean confluenceLoaded;
    private static volatile Boolean qshopLoaded;
    private static volatile Boolean sellboxLoaded;

    private ConfluenceSupport() {
    }

    public static boolean isLoaded() {
        Boolean cached = confluenceLoaded;
        if (cached == null) {
            cached = classPresent("org.confluence.mod.util.PlayerUtils");
            confluenceLoaded = cached;
        }
        return cached;
    }

    public static boolean qshopPresent() {
        Boolean cached = qshopLoaded;
        if (cached == null) {
            cached = classPresent("com.qshop.wallet.WalletCapability");
            qshopLoaded = cached;
        }
        return cached;
    }

    public static boolean sellboxPresent() {
        Boolean cached = sellboxLoaded;
        if (cached == null) {
            cached = classPresent("com.qshop.sellbox.PriceQuote");
            sellboxLoaded = cached;
        }
        return cached;
    }

    /** 会加载类的探测，只允许在 Mixin 配置准备阶段之后调用。 */
    public static boolean classPresent(String className) {
        try {
            Class.forName(className, false, ConfluenceSupport.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 不会加载类的探测，可以安全地在 Mixin 配置准备阶段调用。
     *
     * <p>先查 Forge 的模组列表（最权威），拿不到时退回资源查找。</p>
     */
    public static boolean modPresent(String modId, String classResource) {
        Boolean byModList = modListCheck(modId);
        if (byModList != null) {
            return byModList;
        }
        return resourcePresent(classResource);
    }

    /** 只按资源路径查找 class 文件，不触发类加载。 */
    public static boolean resourcePresent(String classResource) {
        try {
            ClassLoader loader = ConfluenceSupport.class.getClassLoader();
            return loader != null && loader.getResource(classResource) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** @return 能确定时返回结果；模组列表尚未建立时返回 null。 */
    private static Boolean modListCheck(String modId) {
        try {
            net.minecraftforge.fml.ModList modList = net.minecraftforge.fml.ModList.get();
            if (modList != null) {
                return modList.getModContainerById(modId).isPresent();
            }
        } catch (Throwable ignored) {
            // ModList 还没建立，继续往下试
        }
        try {
            net.minecraftforge.fml.loading.LoadingModList loading = net.minecraftforge.fml.loading.LoadingModList.get();
            if (loading != null) {
                return loading.getModFileById(modId) != null;
            }
        } catch (Throwable ignored) {
            // 同上
        }
        return null;
    }
}
