package com.qshop.confluence.compat;

import java.util.List;

/** Client-side access to the active NPC and its buyback prices for contextual tooltips. */
public interface NPCTradeMenuAccess {

    Object qshop_confluence$getNpc();

    List<?> qshop_confluence$getRefundablePurchases();
}
