package com.qshop.confluence;

import com.mojang.logging.LogUtils;
import com.qshop.wallet.WalletImpl;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Initializes legacy player wallet data before QShop's native offline API mutates it. */
public final class OfflineWalletMigration {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FORGE_CAPS_KEY = "ForgeCaps";
    private static final String WALLET_CAPABILITY_KEY = "qshop:wallet";

    private OfflineWalletMigration() {
    }

    /**
     * Returns true when the file is already initialized or migration succeeded.
     * The first-login wrapper reconciles extra coin slots and piggy-bank money that
     * are not stored in the vanilla Inventory list used for this offline bootstrap.
     */
    public static synchronized boolean prepare(MinecraftServer server, UUID playerId, String currencyId) {
        if (server == null || playerId == null || currencyId == null || currencyId.isBlank()) {
            return false;
        }

        File file = server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                .resolve(playerId + ".dat").toFile();
        if (!file.isFile()) {
            return false;
        }

        try {
            CompoundTag playerData = NbtIo.readCompressed(file);
            CompoundTag forgeCaps = playerData.getCompound(FORGE_CAPS_KEY);
            WalletImpl wallet = new WalletImpl();
            wallet.deserializeNBT(forgeCaps.getCompound(WALLET_CAPABILITY_KEY));
            if (BridgeWalletData.initialized(wallet, currencyId)) {
                return true;
            }

            long cash = inventoryCash(playerData);
            long piggyBank = BridgeConfig.includePiggyBank() ? piggyBankCash(playerData) : 0L;
            double legacyFraction = ConfluenceCurrencyBridge.fractionOf(wallet.getBalance(currencyId));
            long pending = Math.max(0L, PendingMoney.get(server).peek(playerId));
            double balance = (double) cash + (double) piggyBank + legacyFraction + (double) pending;
            if (!Double.isFinite(balance)) {
                balance = ConfluenceCurrencyBridge.MAX_SUPPORTED_BALANCE;
            }
            wallet.setBalance(currencyId, Math.min(balance, ConfluenceCurrencyBridge.MAX_SUPPORTED_BALANCE));
            BridgeWalletData.markInitialized(wallet, currencyId, cash, piggyBank, pending);

            forgeCaps.put(WALLET_CAPABILITY_KEY, wallet.serializeNBT());
            playerData.put(FORGE_CAPS_KEY, forgeCaps);
            Path target = file.toPath();
            Path temp = target.resolveSibling(target.getFileName() + ".qshop-confluence.tmp");
            NbtIo.writeCompressed(playerData, temp.toFile());
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("QShop Confluence Bridge: 初始化离线玩家 {} 的余额失败；本次离线操作不会被接管",
                    playerId, exception);
            return false;
        }
    }

    private static long inventoryCash(CompoundTag playerData) {
        long total = cashInList(playerData.getList("Inventory", Tag.TAG_COMPOUND));
        CompoundTag attachments = playerData.getCompound("portlib:attachments");
        CompoundTag extraInventory = attachments.getCompound("confluence:extra_inventory");
        total = saturatedAdd(total, cashInList(extraInventory.getList("Coin", Tag.TAG_COMPOUND)));
        return total;
    }

    private static long piggyBankCash(CompoundTag playerData) {
        CompoundTag attachments = playerData.getCompound("portlib:attachments");
        return cashInList(attachments.getList("confluence:piggy_bank", Tag.TAG_COMPOUND));
    }

    private static long cashInList(ListTag inventory) {
        long total = 0L;
        for (int index = 0; index < inventory.size(); index++) {
            CompoundTag stack = inventory.getCompound(index);
            ResourceLocation itemId = ResourceLocation.tryParse(stack.getString("id"));
            if (itemId == null) {
                continue;
            }
            long value = coinValue(itemId);
            if (value == 0L) {
                continue;
            }
            long count = Math.max(0L, stack.getInt("Count"));
            if (count == 0L) {
                count = Math.max(0L, stack.getInt("count"));
            }
            try {
                total = Math.addExact(total, Math.multiplyExact(count, value));
            } catch (ArithmeticException ignored) {
                return Long.MAX_VALUE;
            }
        }
        return total;
    }

    private static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static long coinValue(ResourceLocation itemId) {
        if (!"confluence".equals(itemId.getNamespace())) {
            return 0L;
        }
        return switch (itemId.getPath()) {
            case "platinum_coin" -> 1_000_000L;
            case "gold_coin" -> 10_000L;
            case "silver_coin" -> 100L;
            case "copper_coin" -> 1L;
            default -> 0L;
        };
    }
}
