package com.qshop.confluence.mixin.npc;

import com.qshop.confluence.compat.NPCBuybackAccess;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Accesses Confluence's private buyback record while preserving NPC refund pricing. */
@Mixin(targets = "org.confluence.mod.common.entity.npc.trade.NPCTradeMenu$Buyback", remap = false)
public abstract class NPCTradeBuybackMixin implements NPCBuybackAccess {

    @Shadow @Final private ItemStack stack;
    @Shadow @Final private long price;

    @Override
    @Unique
    public ItemStack qshop_confluence$getStack() {
        return stack;
    }

    @Override
    @Unique
    public long qshop_confluence$getPrice() {
        return price;
    }
}
