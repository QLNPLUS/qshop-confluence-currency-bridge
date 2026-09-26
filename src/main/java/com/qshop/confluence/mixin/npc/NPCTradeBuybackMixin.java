package com.qshop.confluence.mixin.npc;

import com.qshop.confluence.compat.NPCBuybackAccess;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accesses Confluence's private buyback record while preserving NPC refund pricing. */
@Mixin(targets = "org.confluence.mod.common.entity.npc.trade.NPCTradeMenu$Buyback", remap = false)
public interface NPCTradeBuybackMixin extends NPCBuybackAccess {

    @Override
    @Accessor("stack")
    ItemStack qshop_confluence$getStack();

    @Override
    @Accessor("price")
    long qshop_confluence$getPrice();
}
