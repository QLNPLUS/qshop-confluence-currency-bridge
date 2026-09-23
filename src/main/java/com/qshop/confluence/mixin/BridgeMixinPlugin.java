package com.qshop.confluence.mixin;

import com.qshop.confluence.ConfluenceSupport;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * 按目标模组是否存在决定是否应用混入。
 *
 * <p>出售箱与 Confluence 都是可选兼容，没装时针对它们的混入必须整体跳过，
 * 否则 Mixin 会因找不到目标类报错。</p>
 *
 * <p><b>这里绝对不能加载目标类。</b>Mixin 在 prepare 阶段就会调用
 * {@code shouldApplyMixin}，此时若用 {@code Class.forName} 探测，目标类会在 Mixin
 * 准备好应用混入之前就被定义出来，结果是混入静默地永远不会生效。
 * 因此改用 {@link ConfluenceSupport#modPresent}（模组列表 / 资源查找）。</p>
 */
public class BridgeMixinPlugin implements IMixinConfigPlugin {

    private static final String QSHOP_PREFIX = "com.qshop.confluence.mixin.qshop.";
    private static final String SELLBOX_PREFIX = "com.qshop.confluence.mixin.sellbox.";
    private static final String NPC_PREFIX = "com.qshop.confluence.mixin.npc.";
    private static final String INVENTORY_PREFIX = "com.qshop.confluence.mixin.inventory.";

    @Override
    public void onLoad(String mixinPackage) {
        // 无需初始化
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith(INVENTORY_PREFIX)) {
            return ConfluenceSupport.modPresent("confluence", ConfluenceSupport.CONFLUENCE_PROBE)
                    && ConfluenceSupport.modPresent("qshop", ConfluenceSupport.QSHOP_PROBE);
        }
        if (mixinClassName.startsWith(NPC_PREFIX)) {
            return ConfluenceSupport.modPresent("confluence", ConfluenceSupport.CONFLUENCE_PROBE)
                    && ConfluenceSupport.modPresent("qshop_sellbox", ConfluenceSupport.SELLBOX_PROBE)
                    && ConfluenceSupport.modPresent("qshop", ConfluenceSupport.QSHOP_PROBE);
        }
        if (mixinClassName.endsWith(".SellBoxSalePriceMixin")) {
            return ConfluenceSupport.modPresent("confluence", ConfluenceSupport.CONFLUENCE_PROBE)
                    && ConfluenceSupport.modPresent("qshop_sellbox", ConfluenceSupport.SELLBOX_PROBE)
                    && ConfluenceSupport.modPresent("qshop", ConfluenceSupport.QSHOP_PROBE);
        }
        if (mixinClassName.startsWith(SELLBOX_PREFIX)) {
            return ConfluenceSupport.modPresent("qshop_sellbox", ConfluenceSupport.SELLBOX_PROBE)
                    && ConfluenceSupport.modPresent("qshop", ConfluenceSupport.QSHOP_PROBE);
        }
        if (mixinClassName.startsWith(QSHOP_PREFIX)) {
            return ConfluenceSupport.modPresent("qshop", ConfluenceSupport.QSHOP_PROBE);
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // 无需处理
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 无需处理
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 无需处理
    }
}
