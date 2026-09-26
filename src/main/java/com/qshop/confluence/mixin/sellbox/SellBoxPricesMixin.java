package com.qshop.confluence.mixin.sellbox;

import com.qshop.sellbox.SellBoxPrices;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Intentionally has no injections. Quote adjustment is applied at explicit sale/display
 * call sites; globally injecting into resolve would adjust those quotes a second time.
 */
@Mixin(value = SellBoxPrices.class, remap = false)
public abstract class SellBoxPricesMixin {
}
