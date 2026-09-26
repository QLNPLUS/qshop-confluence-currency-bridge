package com.qshop.confluence.mixin.inventory;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the current screen menu and hovered slot for NPC-specific inventory tooltips. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("menu")
    AbstractContainerMenu qshop_confluence$getMenu();

    @Accessor("hoveredSlot")
    Slot qshop_confluence$getHoveredSlot();
}
