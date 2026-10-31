package com.qshop.confluence;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.qshop.wallet.IWallet;
import com.qshop.wallet.WalletCapability;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /qshopconfluence} —— 自检与调试指令。
 *
 * <p>{@code /qshopconfluence status} 会同时打印 Confluence 身上的钱和 QShop 读到的余额，
 * 两者一致就说明绑定生效。</p>
 */
public final class BridgeCommand {

    private BridgeCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("qshopconfluence")
                .requires(source -> source.hasPermission(2))
                .executes(BridgeCommand::status)
                .then(Commands.literal("status").executes(BridgeCommand::status))
                .then(Commands.literal("set")
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                .executes(BridgeCommand::set))));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String currencyId = BridgeConfig.currencyId();
        source.sendSuccess(() -> Component.translatable("qshop_confluence.command.status_header",
                currencyId), false);
        source.sendSuccess(() -> Component.literal("qshop=" + ConfluenceSupport.qshopPresent()
                + " confluence=" + ConfluenceSupport.isLoaded()
                + " sellbox=" + ConfluenceSupport.sellboxPresent()
                + " includePiggyBank=" + BridgeConfig.includePiggyBank()
                + " sellboxFormat=" + BridgeConfig.sellboxPriceFormat()), false);

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 1;
        }
        if (!ConfluenceCurrencyBridge.active()) {
            source.sendSuccess(() -> Component.translatable("qshop_confluence.command.confluence_missing"), false);
            return 0;
        }
        long money = ConfluenceMoney.get(player, BridgeConfig.includePiggyBank());
        IWallet wallet = WalletCapability.get(player);
        double balance = wallet == null ? Double.NaN : wallet.getBalance(currencyId);
        source.sendSuccess(() -> Component.translatable("qshop_confluence.command.balance",
                ConfluenceCurrencyFormat.toComponent(money), balance), false);
        source.sendSuccess(() -> Component.literal("dev: confluenceCopper=" + money
                + " qshopBalance=" + balance
                + " piggyBankIncluded=" + BridgeConfig.includePiggyBank()), false);
        IWallet raw = wallet instanceof ConfluenceWallet wrapped ? wrapped.unwrap() : wallet;
        double cashMirror = raw.getBalance(BridgeWalletData.cashKey(currencyId));
        double piggyMirror = raw.getBalance(BridgeWalletData.piggyKey(currencyId));
        double version = raw.getBalance(BridgeWalletData.versionKey(currencyId));
        source.sendSuccess(() -> Component.literal("ledger: initialized="
                + BridgeWalletData.initialized(raw, currencyId)
                + " cashMirror=" + cashMirror + " piggyMirror=" + piggyMirror
                + " version=" + version
                + " reserve=" + (balance - cashMirror - piggyMirror)
                + " skipDeathRetention=" + BridgeConfig.skipDeathRetention()), false);
        source.sendSuccess(() -> Component.literal("state: health=" + player.getHealth()
                + " isDeadOrDying=" + player.isDeadOrDying()
                + " carriedCopper=" + ConfluenceMoney.getCarried(player)
                + " coinSlots=" + ConfluenceMoney.countCoinSlots(player)), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        double amount = DoubleArgumentType.getDouble(context, "amount");
        IWallet wallet = WalletCapability.get(player);
        if (wallet == null) {
            return 0;
        }
        wallet.setBalance(BridgeConfig.currencyId(), amount);
        double after = wallet.getBalance(BridgeConfig.currencyId());
        context.getSource().sendSuccess(() -> Component.translatable("qshop_confluence.command.set",
                ConfluenceCurrencyFormat.toComponent(after)), true);
        return 1;
    }
}
