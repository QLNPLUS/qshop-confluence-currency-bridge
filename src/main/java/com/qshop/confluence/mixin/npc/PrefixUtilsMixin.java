package com.qshop.confluence.mixin.npc;

import com.qshop.confluence.ConfluenceSellBoxPrices;
import net.minecraft.world.item.ItemStack;
import org.confluence.mod.util.PrefixUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Uses linked QShop prices as the Goblin Tinker's reforge-cost base. */
@Mixin(value = PrefixUtils.class, remap = false)
public abstract class PrefixUtilsMixin {

    @Redirect(method = "getReforgeCost(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)I",
            at = @At(value = "INVOKE", target = "Lorg/confluence/mod/common/component/ValueComponent;"
                    + "getValue(Lnet/minecraft/world/item/ItemStack;I)I"),
            remap = false, require = 1)
    private static int qshop_confluence$useLinkedPrice(ItemStack stack, int nativeFallback) {
        return ConfluenceSellBoxPrices.reforgeBasePrice(stack, nativeFallback);
    }
}
