package com.qshop.confluence.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.qshop.confluence.ConfluenceMoney;
import com.qshop.confluence.QShopConfluenceMod;
import com.qshop.wallet.IWallet;
import com.qshop.wallet.WalletCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;

/**
 * Compatibility regression for death-preventing mods (PlayerRevive's bleeding state): a listener at
 * HIGHEST cancels {@link LivingDeathEvent} and restores a sliver of health instead of letting the
 * player respawn. Confluence registers its coin drop through PortLib at NORMAL with
 * {@code receiveCanceled = false}, so the coins must survive the cancelled death, and the wallet
 * must not move either.
 */
@GameTestHolder(QShopConfluenceMod.MODID)
@PrefixGameTestTemplate(false)
public final class BridgeDownedDeathGameTests {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Set while a test wants its entity to be knocked down instead of killed. */
    private static UUID downedTarget;

    private BridgeDownedDeathGameTests() {
    }

    /** Mirrors {@code ReviveEventServer#playerDied}: cancel at HIGHEST, then heal to a low health. */
    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class DownedSimulator {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onDeath(LivingDeathEvent event) {
            if (downedTarget == null || !event.getEntity().getUUID().equals(downedTarget)) {
                return;
            }
            event.setCanceled(true);
            event.getEntity().setHealth(1.0F);
        }
    }

    @GameTest(templateNamespace = "qshop_confluence", template = "empty")
    public static void downedDeathKeepsCoinsAndBalance(GameTestHelper helper) {
        FakePlayer player = spawn(helper, "bridge-downed");
        IWallet wallet = WalletCapability.get(player);
        helper.assertTrue(wallet != null, "FakePlayer did not receive the QShop wallet capability");
        wallet.setBalance("coins", 123_456.0D);
        helper.assertTrue(ConfluenceMoney.getCarried(player) == 123_456L,
                "bridge did not materialise coins: " + ConfluenceMoney.getCarried(player));

        long dropsBefore = countCoinDrops(helper);
        double balanceBefore = wallet.getBalance("coins");

        downedTarget = player.getUUID();
        player.setHealth(0.0F);
        LivingDeathEvent event = new LivingDeathEvent(player,
                helper.getLevel().damageSources().genericKill());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
        boolean dying = player.isDeadOrDying();
        downedTarget = null;

        LOGGER.info("qshop_confluence downed-sim: cancelled={} health={} isDeadOrDying={} carried={} "
                        + "balance={} drops={} -> {}",
                event.isCanceled(), player.getHealth(), dying, ConfluenceMoney.getCarried(player),
                wallet.getBalance("coins"), dropsBefore, countCoinDrops(helper));

        helper.assertTrue(event.isCanceled(), "the downed simulation did not cancel the death");
        helper.assertTrue(player.getHealth() > 0.0F, "downed player should keep bleeding health");
        helper.assertTrue(ConfluenceMoney.getCarried(player) == 123_456L,
                "a cancelled death must not drop Confluence coins, carried = "
                        + ConfluenceMoney.getCarried(player));
        helper.assertTrue(countCoinDrops(helper) == dropsBefore,
                "coin entities appeared for a cancelled death: " + countCoinDrops(helper));
        helper.assertTrue(wallet.getBalance("coins") == balanceBefore,
                "balance changed for a cancelled death: " + balanceBefore + " -> "
                        + wallet.getBalance("coins"));
        helper.succeed();
    }

    /**
     * PlayerRevive lets the bleed-out death through uncancelled ({@code doesByPass}), so the coins do
     * drop there; the bridge has to fold that loss into the wallet total immediately.
     */
    @GameTest(templateNamespace = "qshop_confluence", template = "empty")
    public static void fatalDeathAfterDownedReconcilesBalance(GameTestHelper helper) {
        FakePlayer player = spawn(helper, "bridge-bleed-out");
        IWallet wallet = WalletCapability.get(player);
        helper.assertTrue(wallet != null, "FakePlayer did not receive the QShop wallet capability");
        wallet.setBalance("coins", 123_456.0D);
        helper.assertTrue(ConfluenceMoney.getCarried(player) == 123_456L,
                "bridge did not materialise coins: " + ConfluenceMoney.getCarried(player));

        long dropsBefore = countCoinDrops(helper);

        downedTarget = player.getUUID();
        player.setHealth(0.0F);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(player,
                helper.getLevel().damageSources().genericKill()));
        downedTarget = null;
        helper.assertTrue(ConfluenceMoney.getCarried(player) == 123_456L,
                "downed phase lost coins: " + ConfluenceMoney.getCarried(player));

        player.setHealth(0.0F);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(player,
                helper.getLevel().damageSources().inFire()));
        long dropsAfter = countCoinDrops(helper);
        double balance = wallet.getBalance("coins");
        LOGGER.info("qshop_confluence bleed-out: carried={} balance={} drops={} -> {}",
                ConfluenceMoney.getCarried(player), balance, dropsBefore, dropsAfter);

        helper.assertTrue(ConfluenceMoney.getCarried(player) == 0L,
                "the fatal death should drop the coins, carried = " + ConfluenceMoney.getCarried(player));
        helper.assertTrue(dropsAfter > dropsBefore,
                "no coin drops were spawned for the fatal death (" + dropsBefore + " -> " + dropsAfter + ")");
        helper.assertTrue(balance == 0.0D,
                "wallet total must follow the dropped coins, was " + balance);
        helper.succeed();
    }

    private static long countCoinDrops(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(0, 0, 0));
        List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(origin).inflate(96.0D));
        long total = 0L;
        for (ItemEntity item : items) {
            ItemStack stack = item.getItem();
            if (ConfluenceMoney.isBridgeCurrencyStack(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static FakePlayer spawn(GameTestHelper helper, String name) {
        FakePlayer player = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), name));
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        return player;
    }
}
