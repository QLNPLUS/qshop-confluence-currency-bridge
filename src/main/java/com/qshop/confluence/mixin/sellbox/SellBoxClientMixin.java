package com.qshop.confluence.mixin.sellbox;

import com.qshop.confluence.BridgeConfig;
import com.qshop.confluence.ConfluenceCurrencyBridge;
import com.qshop.confluence.ConfluenceCurrencyFormat;
import com.qshop.sellbox.client.SellBoxClient;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import org.confluence.mod.util.ClientUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把绑定货币的 tooltip 金额交给 Confluence 自己格式化，并隐藏 QShop 追加的货币名，
 * 避免重复显示币种。
 */
@Mixin(value = SellBoxClient.class, remap = false)
public abstract class SellBoxClientMixin {

    @Inject(method = "currencyDisplayName", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void qshop_confluence$blankCurrencyLabel(String currencyId, CallbackInfoReturnable<String> cir) {
        if (!BridgeConfig.sellboxPriceFormat()) {
            return;
        }
        if (ConfluenceCurrencyBridge.bound(currencyId)) {
            cir.setReturnValue("");
        }
    }

    @Inject(method = "onItemTooltip", at = @At("RETURN"), remap = false, require = 0)
    private static void qshop_confluence$formatPriceLikeConfluence(ItemTooltipEvent event, CallbackInfo ci) {
        if (!BridgeConfig.sellboxPriceFormat()) {
            return;
        }

        var tooltip = event.getToolTip();
        Component rewrittenLine = null;
        for (int i = tooltip.size() - 1; i >= 0; i--) {
            Component line = tooltip.get(i);
            if (!(line.getContents() instanceof TranslatableContents contents)
                    || !"qshop_sellbox.tooltip.price".equals(contents.getKey())) {
                continue;
            }

            Object[] args = contents.getArgs();
            if (args.length < 2 || !(args[1] instanceof String currencyLabel) || !currencyLabel.isEmpty()) {
                continue;
            }

            Double unitPrice = parsePrice(args[0]);
            if (unitPrice == null) {
                continue;
            }

            // SellBox pays quote.price() * stack count; display that same total for this stack.
            long copper = ConfluenceCurrencyFormat.toCopper(unitPrice * event.getItemStack().getCount());
            rewrittenLine = Component.translatable("tooltip.price.sell")
                    .withStyle(ChatFormatting.GRAY)
                    .append(ClientUtils.formatPrice(copper));
            tooltip.set(i, rewrittenLine);
            break;
        }

        if (rewrittenLine == null) {
            return;
        }

        // Confluence already added its native sell-price row. Keep the Sell Box quote,
        // formatted as Confluence, and remove the duplicate native row.
        for (int i = tooltip.size() - 1; i >= 0; i--) {
            Component line = tooltip.get(i);
            if (line == rewrittenLine
                    || !(line.getContents() instanceof TranslatableContents contents)
                    || !"tooltip.price.sell".equals(contents.getKey())
                    || contents.getArgs().length != 0) {
                continue;
            }
            tooltip.remove(i);
        }
    }

    private static Double parsePrice(Object price) {
        if (price instanceof Number number) {
            return number.doubleValue();
        }
        if (price instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
