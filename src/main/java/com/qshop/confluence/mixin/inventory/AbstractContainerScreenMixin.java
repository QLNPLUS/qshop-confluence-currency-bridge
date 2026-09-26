package com.qshop.confluence.mixin.inventory;

import com.qshop.confluence.compat.ContainerScreenAccess;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Reads the current screen menu and hovered slot for NPC-specific inventory tooltips. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin implements ContainerScreenAccess {

    @Shadow @Final protected AbstractContainerMenu menu;
    @Shadow protected Slot hoveredSlot;

    @Override
    @Unique
    public AbstractContainerMenu qshop_confluence$getMenu() {
        return menu;
    }

    @Override
    @Unique
    public Slot qshop_confluence$getHoveredSlot() {
        return hoveredSlot;
    }
}
