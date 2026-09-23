package com.qshop.confluence.mixin.inventory;

import com.qshop.confluence.BridgeEvents;
import com.qshop.confluence.ConfluenceMoney;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractContainerMenu.class, remap = false)
public abstract class AbstractContainerMenuMixin {

    @Inject(method = "clicked(IILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("HEAD"), require = 0)
    private void qshop_confluence$markCurrencySlotClick(
            int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        boolean currencyClick = ConfluenceMoney.isBridgeCurrencyStack(menu.getCarried());
        if (!currencyClick && slotId >= 0 && slotId < menu.slots.size()) {
            Slot slot = menu.slots.get(slotId);
            currencyClick = ConfluenceMoney.isBridgeCurrencyStack(slot.getItem());
        }
        if (!currencyClick && clickType == ClickType.SWAP
                && button >= 0 && button < serverPlayer.getInventory().items.size()) {
            currencyClick = ConfluenceMoney.isBridgeCurrencyStack(
                    serverPlayer.getInventory().getItem(button));
        }

        if (currencyClick) {
            BridgeEvents.markCurrencyInventoryDirty(serverPlayer);
        }
    }
}
