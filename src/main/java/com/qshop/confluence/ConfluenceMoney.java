package com.qshop.confluence;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.confluence.mod.util.PlayerUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Confluence money access, including its optional PortLib attachments via reflection. */
public final class ConfluenceMoney {

    private static final long[] DENOMINATIONS = {1_000_000L, 10_000L, 100L, 1L};
    private static final int FALLBACK_PLATINUM_STACK = 9_999;
    private static final String COIN_ITEM_CLASS = "org.confluence.mod.common.item.common.CoinItem";
    private static final String EXTRA_INVENTORY_CLASS = "org.confluence.mod.common.attachment.ExtraInventory";
    private static final String PIGGY_BANK_CLASS = "org.confluence.mod.common.attachment.PlayerPiggyBankContainer";
    private static final String[] COIN_IDS = {
            "confluence:platinum_coin",
            "confluence:gold_coin",
            "confluence:silver_coin",
            "confluence:copper_coin"
    };

    private ConfluenceMoney() {
    }

    /** PlayerUtils includes the coin slots and, optionally, the piggy bank. */
    public static long get(Player player, boolean withPiggyBank) {
        return player == null ? 0L : PlayerUtils.getMoney(player, withPiggyBank);
    }

    /** Coins carried in the normal inventory, Confluence coin slots, and cursor. */
    public static long getCarried(Player player) {
        if (player == null) {
            return 0L;
        }
        long inventoryCash = PlayerUtils.getMoney(player, false);
        return saturatedAdd(inventoryCash, stackValue(player.containerMenu.getCarried()));
    }

    /** Money stored in Confluence's piggy bank, when that feature is enabled. */
    public static long getPiggyBank(Player player) {
        Object container = piggyBank(player);
        if (container == null) {
            return 0L;
        }
        try {
            return Math.max(0L, ((Number) container.getClass().getMethod("getTotalMoney")
                    .invoke(container)).longValue());
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            return 0L;
        }
    }

    /** Reduces Confluence's piggy-bank total while preserving the remaining bank coins. */
    public static void setPiggyBank(Player player, long amount) {
        Object container = piggyBank(player);
        if (container == null) {
            return;
        }
        try {
            long current = ((Number) container.getClass().getMethod("getTotalMoney")
                    .invoke(container)).longValue();
            long target = Math.max(0L, amount);
            if (current > target) {
                container.getClass().getMethod("tryCostMoney", long.class)
                        .invoke(container, current - target);
            }
        } catch (ReflectiveOperationException ignored) {
            // The bridge remains usable without changing the optional piggy-bank attachment.
        }
    }

    /** Number of player inventory and Confluence coin slots that can hold coin stacks. */
    public static int countCoinSlots(Player player) {
        int slots = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (stack.isEmpty() || isCoin(stack)) {
                slots++;
            }
        }
        int extraSlots = extraSlotCount();
        for (int slot = 0; slot < extraSlots; slot++) {
            ItemStack stack = getExtraCoin(player, slot);
            if (stack != null && (stack.isEmpty() || isCoin(stack))) {
                slots++;
            }
        }
        return slots;
    }

    /**
     * Repack the carried coin mirror up to the value the current free coin slots
     * can represent. Leftover value remains in QShop's wallet as reserve.
     */
    public static long rebalanceCarried(Player player, long availableCopper) {
        long cursorCash = stackValue(player.containerMenu.getCarried());
        long desiredInventoryCash = Math.max(0L, availableCopper - cursorCash);
        int slots = countCoinSlots(player);
        long current = getCarried(player);
        long planned = planValue(desiredInventoryCash, slots);
        if (current == saturatedAdd(cursorCash, planned)) {
            return current;
        }

        clearInventoryCoins(player);
        insertPlannedCoins(player, desiredInventoryCash, slots);
        return getCarried(player);
    }

    /** Inventory and Confluence coin-slot cash, excluding the cursor stack. */
    public static long getInventoryCash(Player player) {
        return player == null ? 0L : PlayerUtils.getMoney(player, false);
    }

    private static long stackValue(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isCoin(stack)) {
            return 0L;
        }
        long denomination = denominationValue(stack.getItem());
        if (denomination <= 0L) {
            return 0L;
        }
        try {
            return Math.multiplyExact((long) stack.getCount(), denomination);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    /** True for the four Confluence coin denominations bridged into QShop. */
    public static boolean isBridgeCurrencyStack(ItemStack stack) {
        return stackValue(stack) > 0L;
    }

    private static long denominationValue(Item item) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (!"confluence".equals(itemId.getNamespace())) {
            return 0L;
        }
        String path = itemId.getPath();
        return switch (path) {
            case "platinum_coin" -> 1_000_000L;
            case "gold_coin" -> 10_000L;
            case "silver_coin" -> 100L;
            case "copper_coin" -> 1L;
            default -> 0L;
        };
    }

    private static boolean isCoin(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (Class<?> type = stack.getItem().getClass(); type != null; type = type.getSuperclass()) {
            if (COIN_ITEM_CLASS.equals(type.getName())) {
                return true;
            }
        }
        return denominationValue(stack.getItem()) > 0L;
    }

    private static long planValue(long amount, int availableSlots) {
        long remaining = amount;
        long value = 0L;
        int slots = availableSlots;
        Item[] items = coinItems();
        for (int denomination = 0; denomination < DENOMINATIONS.length && slots > 0; denomination++) {
            if (items[denomination] == null) {
                continue;
            }
            int stackLimit = stackLimit(items[denomination], denomination);
            long maxCount = (long) slots * stackLimit;
            long count = Math.min(remaining / DENOMINATIONS[denomination], maxCount);
            if (count <= 0L) {
                continue;
            }
            long stackCount = (count + stackLimit - 1L) / stackLimit;
            slots -= (int) stackCount;
            long denominationValue = count * DENOMINATIONS[denomination];
            value = saturatedAdd(value, denominationValue);
            remaining -= denominationValue;
        }
        return value;
    }

    private static void clearInventoryCoins(Player player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (isCoin(stack)) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        for (int slot = 0; slot < extraSlotCount(); slot++) {
            if (isCoin(getExtraCoin(player, slot))) {
                setExtraCoin(player, slot, ItemStack.EMPTY);
            }
        }
    }

    private static void insertPlannedCoins(Player player, long amount, int availableSlots) {
        Item[] items = coinItems();
        int extraSlot = 0;
        int slots = availableSlots;
        long remaining = amount;
        for (int denomination = 0; denomination < DENOMINATIONS.length && slots > 0; denomination++) {
            Item item = items[denomination];
            if (item == null) {
                continue;
            }
            int stackLimit = stackLimit(item, denomination);
            long count = Math.min(remaining / DENOMINATIONS[denomination], (long) slots * stackLimit);
            if (count <= 0L) {
                continue;
            }
            long stackCount = (count + stackLimit - 1L) / stackLimit;
            slots -= (int) stackCount;
            remaining -= count * DENOMINATIONS[denomination];

            long left = count;
            while (left > 0L) {
                int stackSize = (int) Math.min(left, stackLimit);
                ItemStack stack = new ItemStack(item, stackSize);
                boolean placed = false;
                while (extraSlot < extraSlotCount()) {
                    int candidate = extraSlot++;
                    ItemStack extraStack = getExtraCoin(player, candidate);
                    if (extraStack == null || extraStack.isEmpty()) {
                        setExtraCoin(player, candidate, stack);
                        placed = true;
                        break;
                    }
                }
                if (!placed) {
                    player.getInventory().add(stack);
                }
                left -= stackSize;
            }
        }
    }

    private static int stackLimit(Item item, int denomination) {
        if (item == null) {
            return denomination == 0 ? FALLBACK_PLATINUM_STACK : 100;
        }
        int max = new ItemStack(item).getMaxStackSize();
        return Math.max(1, denomination == 0 ? Math.min(FALLBACK_PLATINUM_STACK, max) : Math.min(100, max));
    }

    private static Item[] coinItems() {
        Item[] items = new Item[COIN_IDS.length];
        for (int index = 0; index < COIN_IDS.length; index++) {
            ResourceLocation id = ResourceLocation.tryParse(COIN_IDS[index]);
            if (id != null) {
                Item item = BuiltInRegistries.ITEM.get(id);
                items[index] = item == Items.AIR ? null : item;
            }
        }
        return items;
    }

    private static int extraSlotCount() {
        try {
            Class<?> type = Class.forName(EXTRA_INVENTORY_CLASS);
            Field field = type.getField("SIZE_COINS");
            return Math.max(0, field.getInt(null));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return 0;
        }
    }

    private static Object extraInventory(Player player) {
        try {
            Class<?> type = Class.forName(EXTRA_INVENTORY_CLASS);
            Method of = type.getMethod("of", net.minecraft.world.entity.LivingEntity.class);
            return of.invoke(null, player);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static ItemStack getExtraCoin(Player player, int slot) {
        Object extra = extraInventory(player);
        if (extra == null) {
            return null;
        }
        try {
            return (ItemStack) extra.getClass().getMethod("getCoins", int.class).invoke(extra, slot);
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            return null;
        }
    }

    private static void setExtraCoin(Player player, int slot, ItemStack stack) {
        Object extra = extraInventory(player);
        if (extra == null) {
            return;
        }
        try {
            extra.getClass().getMethod("setCoins", int.class, ItemStack.class).invoke(extra, slot, stack);
        } catch (ReflectiveOperationException ignored) {
            // The ordinary inventory remains a valid fallback.
        }
    }

    private static Object piggyBank(Player player) {
        try {
            Class<?> type = Class.forName(PIGGY_BANK_CLASS);
            return type.getMethod("of", Player.class).invoke(null, player);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
