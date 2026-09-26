package com.qshop.confluence.compat;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/** Stable bridge interface for the current container screen and hovered slot. */
public interface ContainerScreenAccess {

    AbstractContainerMenu qshop_confluence$getMenu();

    Slot qshop_confluence$getHoveredSlot();
}
