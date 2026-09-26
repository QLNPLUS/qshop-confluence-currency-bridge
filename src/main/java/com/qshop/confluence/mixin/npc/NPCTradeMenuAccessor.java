package com.qshop.confluence.mixin.npc;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Client-side access to the active NPC and its buyback prices for contextual tooltips. */
@Mixin(targets = "org.confluence.mod.common.entity.npc.trade.NPCTradeMenu", remap = false)
public interface NPCTradeMenuAccessor {

    @Accessor("npc")
    Object qshop_confluence$getNpc();

    @Accessor("refundablePurchases")
    List<?> qshop_confluence$getRefundablePurchases();
}
