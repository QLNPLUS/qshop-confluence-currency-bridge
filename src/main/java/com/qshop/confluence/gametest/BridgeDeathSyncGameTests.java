package com.qshop.confluence.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.qshop.api.CurrencyService;
import com.qshop.confluence.BridgeWalletData;
import com.qshop.confluence.ConfluenceMoney;
import com.qshop.confluence.ConfluenceWallet;
import com.qshop.confluence.QShopConfluenceMod;
import com.qshop.wallet.IWallet;
import com.qshop.wallet.WalletCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.confluence.mod.util.PlayerUtils;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;

/** Regression tests for the death -> coin drop -> wallet clone/respawn path. */
@GameTestHolder(QShopConfluenceMod.MODID)
@PrefixGameTestTemplate(false)
public final class BridgeDeathSyncGameTests {

    private static final Logger LOGGER = LogUtils.getLogger();
    private BridgeDeathSyncGameTests() {
    }

    @GameTest(templateNamespace = "qshop_confluence", template = "empty")
    public static void deathDropReducesWalletBalance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "bridge-death-old");
        FakePlayer dead = spawn(helper, profile);

        IWallet wallet = WalletCapability.get(dead);
        helper.assertTrue(wallet != null, "FakePlayer did not receive the QShop wallet capability");
        wallet.setBalance("coins", 123_456.0D);
        helper.assertTrue(ConfluenceMoney.getCarried(dead) == 123_456L,
                "bridge did not materialise coins: " + ConfluenceMoney.getCarried(dead));

        PlayerUtils.dropMoney(dead);
        helper.assertTrue(ConfluenceMoney.getCarried(dead) == 0L, "dropMoney left coins behind");

        FakePlayer respawned = new FakePlayer(level, profile);
        IWallet fresh = WalletCapability.get(respawned);
        helper.assertTrue(fresh != null, "Respawned FakePlayer did not receive the QShop wallet capability");
        fresh.copyFrom(wallet);

        double balance = fresh.getBalance("coins");
        helper.assertTrue(balance == 0.0D, "balance after death should be 0, was " + balance);
        helper.succeed();
    }

    @GameTest(templateNamespace = "qshop_confluence", template = "empty")
    public static void dyingPlayerDoesNotGetCoinsRebalanced(GameTestHelper helper) {
        FakePlayer dead = spawn(helper, new GameProfile(UUID.randomUUID(), "bridge-dying"));
        IWallet wallet = WalletCapability.get(dead);
        helper.assertTrue(wallet != null, "FakePlayer did not receive the QShop wallet capability");
        wallet.setBalance("coins", 50_000.0D);
        PlayerUtils.dropMoney(dead);
        helper.assertTrue(ConfluenceMoney.getCarried(dead) == 0L, "dropMoney left coins behind");

        dead.setHealth(0.0F);
        helper.assertTrue(dead.isDeadOrDying(), "test player should be dead");
        // A write that lands after the drop (e.g. QShop's death retention) must not
        // repopulate the corpse's inventory with fresh coins.
        wallet.setBalance("coins", 50_000.0D);
        helper.assertTrue(ConfluenceMoney.getCarried(dead) == 0L,
                "dead player received re-created coins: " + ConfluenceMoney.getCarried(dead));
        helper.succeed();
    }

    /** QShop's death retention must not scale the bridge's internal cash/version ledger. */
    @GameTest(templateNamespace = "qshop_confluence", template = "empty")
    public static void qshopDeathRetentionKeepsLedgerConsistent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer dead = spawn(helper, new GameProfile(UUID.randomUUID(), "bridge-retention-old"));
        IWallet wallet = WalletCapability.get(dead);
        helper.assertTrue(wallet != null, "FakePlayer did not receive the QShop wallet capability");
        wallet.setBalance("coins", 123_456.0D);
        PlayerUtils.dropMoney(dead);
        helper.assertTrue(ConfluenceMoney.getCarried(dead) == 0L, "dropMoney left coins behind");

        FakePlayer respawned = new FakePlayer(level, new GameProfile(dead.getUUID(), "bridge-retention-new"));
        IWallet fresh = WalletCapability.get(respawned);
        helper.assertTrue(fresh != null, "Respawned FakePlayer did not receive the QShop wallet capability");
        fresh.copyFrom(wallet);

        // Replays QShop's PlayerEvent.Clone death path: every wallet entry, including the
        // bridge's internal ledger keys, is set to (value * retention) through CurrencyService.
        IWallet beforeRetention = ((ConfluenceWallet) fresh).unwrap();
        helper.assertTrue(beforeRetention.getBalance(BridgeWalletData.cashKey("coins")) == 123_456.0D,
                "wallet mirror was not copied before death retention");
        Map<String, Double> raw = ((ConfluenceWallet) wallet).unwrap().snapshot();
        for (Map.Entry<String, Double> entry : raw.entrySet()) {
            CurrencyService.INSTANCE.set(respawned, entry.getKey(), entry.getValue() * 0.5D,
                    CurrencyService.SOURCE_DEATH, null);
        }

        IWallet ledger = ((ConfluenceWallet) fresh).unwrap();
        helper.assertTrue(ledger.getBalance(BridgeWalletData.versionKey("coins"))
                        == BridgeWalletData.version(),
                "death retention scaled the bridge version key");
        // Reading the bound currency during the replay reconciles the mirror down to the
        // dropped cash; the internal key itself must not have been scaled to 0.5 * 123456.
        helper.assertTrue(ledger.getBalance(BridgeWalletData.cashKey("coins")) == 0.0D,
                "cash mirror should follow the reconciled drop, was "
                        + ledger.getBalance(BridgeWalletData.cashKey("coins")));

        double balance = fresh.getBalance("coins");
        helper.assertTrue(balance == 0.0D,
                "after death retention the dropped coins must not be restored, balance was " + balance);
        helper.succeed();
    }

    /**
     * Value that no longer fits into the free coin slots stays in the wallet as reserve
     * ({@code ConfluenceMoney.rebalanceCarried} caps the physical mirror). A death drop may only
     * remove the part that was physically carried; today the reserve deliberately survives.
     */
    @GameTest(templateNamespace = "qshop_confluence", template = "empty")
    public static void reserveSurvivesDeathDrop(GameTestHelper helper) {
        FakePlayer player = spawn(helper, new GameProfile(UUID.randomUUID(), "bridge-reserve"));
        IWallet wallet = WalletCapability.get(player);
        helper.assertTrue(wallet != null, "FakePlayer did not receive the QShop wallet capability");
        wallet.setBalance("coins", 1.0E15);

        long carriedBefore = ConfluenceMoney.getCarried(player);
        double balanceBefore = wallet.getBalance("coins");
        helper.assertTrue(carriedBefore > 0L, "the bridge materialised no coins at all");
        helper.assertTrue((double) carriedBefore < balanceBefore,
                "the balance should not have been fully materialisable: carried=" + carriedBefore
                        + " balance=" + balanceBefore);

        player.setHealth(0.0F);
        MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(player,
                helper.getLevel().damageSources().genericKill()));

        long carriedAfter = ConfluenceMoney.getCarried(player);
        double balanceAfter = wallet.getBalance("coins");
        helper.assertTrue(carriedAfter < carriedBefore,
                "the fatal death removed no physical cash: " + carriedBefore + " -> " + carriedAfter);
        helper.assertTrue(balanceAfter == balanceBefore - (double) (carriedBefore - carriedAfter),
                "only the dropped physical cash may leave the total: " + balanceBefore + " -> "
                        + balanceAfter + " (dropped " + (carriedBefore - carriedAfter) + ")");
        helper.assertTrue(balanceAfter > 0.0D,
                "the reserve is expected to survive the drop, got " + balanceAfter);
        helper.succeed();
    }

    /**
     * Option-A semantics on a revive: the reserve that could not drop stays in the wallet, and once the
     * player is alive again the bridge re-materialises it as coin items — bounded by the free coin slots,
     * and the canonical total must not move while it does.
     */
    @GameTest(templateNamespace = "qshop_confluence", template = "empty")
    public static void reserveRefillsCoinSlotsAfterRevive(GameTestHelper helper) {
        FakePlayer player = spawn(helper, new GameProfile(UUID.randomUUID(), "bridge-revive-refill"));
        IWallet wallet = WalletCapability.get(player);
        helper.assertTrue(wallet != null, "FakePlayer did not receive the QShop wallet capability");
        wallet.setBalance("coins", 1.0E15);

        long carriedBefore = ConfluenceMoney.getCarried(player);
        double balanceBefore = wallet.getBalance("coins");
        helper.assertTrue((double) carriedBefore < balanceBefore,
                "the balance should not have been fully materialisable: carried=" + carriedBefore);

        player.setHealth(0.0F);
        MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(player,
                helper.getLevel().damageSources().genericKill()));

        long carriedAfterDeath = ConfluenceMoney.getCarried(player);
        double balanceAfterDeath = wallet.getBalance("coins");
        helper.assertTrue(carriedAfterDeath == 0L,
                "the fatal death left coins behind: " + carriedAfterDeath);
        helper.assertTrue(balanceAfterDeath == balanceBefore - (double) carriedBefore,
                "the dropped cash must leave the total exactly once: " + balanceBefore + " -> "
                        + balanceAfterDeath);

        // A PlayerRevive-style revive: health restored without respawning, coin slots still equipped.
        player.setHealth(20.0F);
        wallet.getBalance("coins");
        long carriedAfterRevive = ConfluenceMoney.getCarried(player);
        double totalAfterRevive = wallet.getBalance("coins");
        LOGGER.info("qshop_confluence revive-refill: carried {} -> {} -> {} 铜, 总额 {} -> {}",
                carriedBefore, carriedAfterDeath, carriedAfterRevive, balanceBefore, totalAfterRevive);

        helper.assertTrue(carriedAfterRevive > 0L,
                "the reserve was not re-materialised after the revive");
        helper.assertTrue(carriedAfterRevive <= carriedBefore,
                "more coins came back than the slots can hold: " + carriedAfterRevive + " > " + carriedBefore);
        helper.assertTrue((double) carriedAfterRevive < totalAfterRevive,
                "the whole reserve came back as items; part of the total must stay in the wallet");
        helper.assertTrue(totalAfterRevive == balanceAfterDeath,
                "re-materialising coins changed the total: " + balanceAfterDeath + " -> " + totalAfterRevive);
        helper.succeed();
    }
    /**
     * Regression for the reported symptom: coins drop as items, the player still ends up holding the
     * same coin stacks, and the shop total never moves. It happens when the wallet's cash mirror was
     * scaled away by an older bridge revision (QShop death retention): the drop is then measured
     * against a zero baseline, nothing is deducted, and the next rebalance mints the coins again.
     */
    @GameTest(templateNamespace = "qshop_confluence", template = "empty")
    public static void staleMirrorDoesNotDuplicateDroppedCoins(GameTestHelper helper) {
        FakePlayer player = spawn(helper, new GameProfile(UUID.randomUUID(), "bridge-stale-mirror"));
        IWallet wallet = WalletCapability.get(player);
        helper.assertTrue(wallet != null, "FakePlayer did not receive the QShop wallet capability");
        wallet.setBalance("coins", 9_999_000_000.0D);
        helper.assertTrue(ConfluenceMoney.getCarried(player) == 9_999_000_000L,
                "bridge did not materialise the platinum coins: " + ConfluenceMoney.getCarried(player));

        IWallet raw = ((ConfluenceWallet) wallet).unwrap();
        raw.setBalance(BridgeWalletData.cashKey("coins"), 0.0D);

        player.setHealth(0.0F);
        MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(player,
                helper.getLevel().damageSources().genericKill()));

        double balance = wallet.getBalance("coins");
        helper.assertTrue(balance == 0.0D,
                "the drop was not folded into the total because the mirror was stale: " + balance);

        // Survive: a revived/respawned player must not be handed the same coins back.
        player.setHealth(20.0F);
        wallet.getBalance("coins");
        helper.assertTrue(ConfluenceMoney.getCarried(player) == 0L,
                "the bridge re-minted the dropped coins: " + ConfluenceMoney.getCarried(player));
        helper.succeed();
    }

    /** An older ledger revision is re-anchored to the coins on hand without inventing a second copy. */
    @GameTest(templateNamespace = "qshop_confluence", template = "empty")
    public static void ledgerVersionReanchorKeepsBalance(GameTestHelper helper) {
        FakePlayer player = spawn(helper, new GameProfile(UUID.randomUUID(), "bridge-v2-ledger"));
        IWallet wallet = WalletCapability.get(player);
        helper.assertTrue(wallet != null, "FakePlayer did not receive the QShop wallet capability");
        wallet.setBalance("coins", 123_456.0D);
        helper.assertTrue(ConfluenceMoney.getCarried(player) == 123_456L,
                "bridge did not materialise coins: " + ConfluenceMoney.getCarried(player));

        IWallet raw = ((ConfluenceWallet) wallet).unwrap();
        raw.setBalance(BridgeWalletData.versionKey("coins"), 2.0D);
        raw.setBalance(BridgeWalletData.cashKey("coins"), 0.0D);

        double balance = wallet.getBalance("coins");
        helper.assertTrue(balance == 123_456.0D,
                "the ledger migration must not change the total (phantom credit?): " + balance);
        helper.assertTrue(raw.getBalance(BridgeWalletData.versionKey("coins"))
                        == BridgeWalletData.version(),
                "ledger version was not migrated");
        helper.assertTrue(raw.getBalance(BridgeWalletData.cashKey("coins")) == 123_456.0D,
                "cash mirror was not re-anchored: " + raw.getBalance(BridgeWalletData.cashKey("coins")));
        helper.assertTrue(ConfluenceMoney.getCarried(player) == 123_456L,
                "coins were duplicated while migrating: " + ConfluenceMoney.getCarried(player));
        helper.succeed();
    }

    /**
     * Confluence's {@code playerDropsMoney=false} must mean "no money on the ground": the bridge may not
     * put coins where vanilla death loot takes them, and Confluence's own drop stays disabled.
     */
    @GameTest(templateNamespace = "qshop_confluence", template = "empty")
    public static void keptMoneyPolicySuppressesCoinDrops(GameTestHelper helper) {
        ForgeConfigSpec.BooleanValue setting = playerDropsMoneySetting();
        helper.assertTrue(setting != null, "Confluence PLAYER_DROPS_MONEY was not found");
        boolean original = setting.get();
        try {
            setting.set(false);
            FakePlayer player = spawn(helper, new GameProfile(UUID.randomUUID(), "bridge-keep-money"));
            IWallet wallet = WalletCapability.get(player);
            helper.assertTrue(wallet != null, "FakePlayer did not receive the QShop wallet capability");
            wallet.setBalance("coins", 1_000_000.0D);

            long carriedBefore = ConfluenceMoney.getCarried(player);
            double balanceBefore = wallet.getBalance("coins");
            long dropsBefore = countCoinDrops(helper);
            helper.assertTrue(carriedBefore > 0L, "the bridge materialised no coins");

            player.setHealth(0.0F);
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(player,
                    helper.getLevel().damageSources().genericKill()));

            long dropsAfter = countCoinDrops(helper);
            long carriedAfter = ConfluenceMoney.getCarried(player);
            double balanceAfter = wallet.getBalance("coins");
            LOGGER.info("qshop_confluence keep-money: carried={}->{} balance={}->{} coinDrops={}->{}",
                    carriedBefore, carriedAfter, balanceBefore, balanceAfter, dropsBefore, dropsAfter);

            helper.assertTrue(dropsAfter == dropsBefore,
                    "coins dropped although playerDropsMoney=false: " + dropsBefore + " -> " + dropsAfter);
            helper.assertTrue(carriedAfter == carriedBefore,
                    "coins left the player although money must be kept: " + carriedBefore + " -> " + carriedAfter);
            helper.assertTrue(balanceAfter == balanceBefore,
                    "the wallet total moved although nothing was dropped: " + balanceBefore + " -> "
                            + balanceAfter);
        } finally {
            setting.set(original);
        }
        helper.succeed();
    }

    /** With the setting on (Confluence's default) the drop must still happen and reduce the total. */
    @GameTest(templateNamespace = "qshop_confluence", template = "empty")
    public static void droppedMoneyPolicyStillDrops(GameTestHelper helper) {
        ForgeConfigSpec.BooleanValue setting = playerDropsMoneySetting();
        helper.assertTrue(setting != null, "Confluence PLAYER_DROPS_MONEY was not found");
        boolean original = setting.get();
        try {
            setting.set(true);
            FakePlayer player = spawn(helper, new GameProfile(UUID.randomUUID(), "bridge-drop-money"));
            IWallet wallet = WalletCapability.get(player);
            helper.assertTrue(wallet != null, "FakePlayer did not receive the QShop wallet capability");
            wallet.setBalance("coins", 1_000_000.0D);

            long carriedBefore = ConfluenceMoney.getCarried(player);
            double balanceBefore = wallet.getBalance("coins");
            long dropsBefore = countCoinDrops(helper);

            player.setHealth(0.0F);
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(player,
                    helper.getLevel().damageSources().genericKill()));

            long dropsAfter = countCoinDrops(helper);
            double balanceAfter = wallet.getBalance("coins");
            LOGGER.info("qshop_confluence drop-money: carried={} balance={}->{} coinDrops={}->{}",
                    ConfluenceMoney.getCarried(player), balanceBefore, balanceAfter, dropsBefore, dropsAfter);

            helper.assertTrue(dropsAfter > dropsBefore,
                    "no coins dropped although playerDropsMoney=true: " + dropsBefore + " -> " + dropsAfter);
            helper.assertTrue(balanceAfter == balanceBefore - (double) carriedBefore,
                    "the dropped coins were not deducted: " + balanceBefore + " -> " + balanceAfter
                            + " (carried " + carriedBefore + ")");
        } finally {
            setting.set(original);
        }
        helper.succeed();
    }

    /**
     * Coins must only ever live in Confluence's coin slots. The ordinary inventory is ordinary items:
     * vanilla death loot drops them even when Confluence's {@code playerDropsMoney} is off, which is what
     * made a death drop money that the shop still counted.
     */
    @GameTest(templateNamespace = "qshop_confluence", template = "empty")
    public static void overflowStaysOutOfTheVanillaInventory(GameTestHelper helper) {
        FakePlayer player = spawn(helper, new GameProfile(UUID.randomUUID(), "bridge-overflow"));
        IWallet wallet = WalletCapability.get(player);
        helper.assertTrue(wallet != null, "FakePlayer did not receive the QShop wallet capability");
        wallet.setBalance("coins", 5.0E11);

        long carried = ConfluenceMoney.getCarried(player);
        long looseCoins = 0L;
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            looseCoins += ConfluenceMoney.copperValue(player.getInventory().items.get(slot));
        }
        double balance = wallet.getBalance("coins");
        LOGGER.info("qshop_confluence overflow: carried={} looseInventoryCoins={} balance={} reserved={}",
                carried, looseCoins, balance, balance - carried);

        helper.assertTrue(carried > 0L, "the bridge materialised no coins");
        helper.assertTrue((double) carried < balance, "the whole balance should not fit the coin slots");
        helper.assertTrue(looseCoins == 0L,
                "the bridge put coins into the ordinary inventory: " + looseCoins + " copper");
        helper.succeed();
    }


    private static ForgeConfigSpec.BooleanValue playerDropsMoneySetting() {
        try {
            Class<?> type = Class.forName("org.confluence.mod.common.CommonConfigs");
            return (ForgeConfigSpec.BooleanValue) type.getField("PLAYER_DROPS_MONEY").get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Item platinumCoin() {
        return BuiltInRegistries.ITEM.get(new ResourceLocation("confluence", "platinum_coin"));
    }

    private static long countCoinDrops(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(0, 0, 0));
        List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(origin).inflate(96.0D));
        long total = 0L;
        for (ItemEntity item : items) {
            if (ConfluenceMoney.isBridgeCurrencyStack(item.getItem())) {
                total += item.getItem().getCount();
            }
        }
        return total;
    }

    private static FakePlayer spawn(GameTestHelper helper, GameProfile profile) {
        FakePlayer player = new FakePlayer(helper.getLevel(), profile);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        return player;
    }
}
