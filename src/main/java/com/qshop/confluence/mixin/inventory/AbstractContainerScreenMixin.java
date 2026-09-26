package com.qshop.confluence.mixin.inventory;

import com.qshop.confluence.compat.ContainerScreenAccess;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the current screen menu and hovered slot for NPC-specific inventory tooltips. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenMixin extends ContainerScreenAccess {

    @Override
    @Accessor("menu")
    AbstractContainerMenu qshop_confluence$getMenu();

    @Override
    @Accessor("hoveredSlot")
    Slot qshop_confluence$getHoveredSlot();
}
