package com.qshop.confluence.mixin.npc;

import java.util.List;

/** Client-side access to the active NPC and its buyback prices for contextual tooltips. */
public interface NPCTradeMenuAccessor {

    Object qshop_confluence$getNpc();

    List<?> qshop_confluence$getRefundablePurchases();
}
