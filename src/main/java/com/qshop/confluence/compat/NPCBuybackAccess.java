package com.qshop.confluence.compat;

import net.minecraft.world.item.ItemStack;

/** Stable bridge interface implemented by Confluence's NPC buyback Mixin. */
public interface NPCBuybackAccess {

    ItemStack qshop_confluence$getStack();

    long qshop_confluence$getPrice();
}
