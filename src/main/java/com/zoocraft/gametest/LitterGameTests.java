package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import com.zoocraft.config.ZooConfig;
import com.zoocraft.core.litter.LitterRules;
import com.zoocraft.litter.LitterEntity;
import com.zoocraft.litter.LitterTracker;
import com.zoocraft.litter.Littering;
import com.zoocraft.registry.ZooEntities;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import com.zoocraft.dashboard.DashboardService;
import com.zoocraft.dashboard.DashboardTab;
import com.zoocraft.feedback.CommentType;
import net.minecraft.network.chat.Component;
import com.zoocraft.shop.FacilityCensus;
import com.zoocraft.visitor.Visitor;
import com.zoocraft.visitor.VisitorPhase;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import com.zoocraft.litter.RubbishBinBlock;
import com.zoocraft.litter.RubbishBinBlockEntity;
import com.zoocraft.registry.ZooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Rubbish bins, litter and cleanliness (Phase 2 spec section 9). Things stand at relative y=2. */
@GameTestHolder(ZooCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LitterGameTests {
    private static final BlockPos BIN = new BlockPos(5, 2, 5);

    private static RubbishBinBlockEntity placeBin(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ZooBlocks.RUBBISH_BIN.get());
        return (RubbishBinBlockEntity) helper.getBlockEntity(pos);
    }

    private static Vec3 at(GameTestHelper helper, double x, double z) {
        BlockPos pos = helper.absolutePos(new BlockPos((int) x, 2, (int) z));
        return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    private static void clearLitter(Zoo zoo) {
        List.copyOf(LitterTracker.litterIn(zoo)).forEach(LitterEntity::discard);
    }

    // Visitor scenes use the large floor: zoo x 16..44, gate on the west edge, visitor at START, bin 6 blocks east.
    private static final BlockPos GATE = new BlockPos(16, 2, 24);
    private static final BlockPos START = new BlockPos(24, 2, 24);
    private static final BlockPos NEAR_BIN = new BlockPos(30, 2, 24);

    private record Scene(ServerPlayer owner, Zoo zoo, Visitor visitor) {
    }

    /** An open zoo with a visitor holding rubbish, kept interested so it doesn't go home during the test. */
    private static Scene rubbishScene(GameTestHelper helper, double litterRoll, Runnable beforeCensus) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ZooManager zoos = TestZoos.manager(helper);
        Zoo zoo = TestZoos.create(helper, owner, "Rubbish", TestZoos.rectangle(helper, 16, 4, 44, 44));
        TestShops.assertOk(helper, zoos.addGate(owner, helper.getLevel(), helper.absolutePos(GATE)));
        zoos.setPrice(owner, zoo, 0);
        zoos.setOpen(owner, zoo, true);
        beforeCensus.run();
        FacilityCensus.refresh(zoo, helper.getLevel());
        Visitor visitor = ZooEntities.VISITOR.get().create(helper.getLevel());
        BlockPos start = helper.absolutePos(START);
        visitor.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0, 0);
        visitor.begin(zoo, zoo.gates().getFirst(), UUID.randomUUID(), true);
        visitor.testSetNeeds(0, 0);
        visitor.testSetSouvenirRoll(0.99);
        visitor.testSetLitterRoll(litterRoll);
        helper.getLevel().addFreshEntity(visitor);
        visitor.testSetPhase(VisitorPhase.EXPLORING);
        visitor.testHoldRubbish();
        helper.onEachTick(() -> visitor.testSetInterest(100));
        return new Scene(owner, zoo, visitor);
    }

    private static void finish(GameTestHelper helper, Scene s) {
        s.visitor().discard();
        clearLitter(s.zoo());
        TestZoos.cleanUp(helper, s.owner(), s.zoo());
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1000)
    public static void visitorPutsRubbishInANearbyBin(GameTestHelper helper) {
        Scene s = rubbishScene(helper, 0, () -> placeBin(helper, NEAR_BIN));
        RubbishBinBlockEntity bin = (RubbishBinBlockEntity) helper.getBlockEntity(NEAR_BIN);
        helper.succeedWhen(() -> {
            helper.assertTrue(bin.fill() == 1, "bin fill is " + bin.fill());
            helper.assertTrue(s.visitor().getMainHandItem().isEmpty(), "still holding rubbish");
            helper.assertTrue(LitterTracker.litterIn(s.zoo()).isEmpty(), "dropped litter next to a bin");
            finish(helper, s);
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1000)
    public static void withNoBinRubbishBecomesLitter(GameTestHelper helper) {
        Scene s = rubbishScene(helper, 0, () -> { });
        helper.succeedWhen(() -> {
            helper.assertTrue(LitterTracker.litterIn(s.zoo()).size() == 1, "no litter yet");
            helper.assertTrue(s.visitor().getMainHandItem().isEmpty(), "still holding rubbish");
            finish(helper, s);
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1000)
    public static void someVisitorsTakeTheirRubbishHome(GameTestHelper helper) {
        Scene s = rubbishScene(helper, 0.99, () -> { });
        helper.runAfterDelay(800, () -> {
            helper.assertTrue(LitterTracker.litterIn(s.zoo()).isEmpty(), "a tidy visitor littered");
            helper.assertTrue(s.visitor().getMainHandItem().isEmpty(), "rubbish never put away");
            finish(helper, s);
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1000)
    public static void aFullBinIsNotUsed(GameTestHelper helper) {
        Scene s = rubbishScene(helper, 0, () -> {
            RubbishBinBlockEntity bin = placeBin(helper, NEAR_BIN);
            while (bin.deposit()) {
                // fill it up
            }
        });
        RubbishBinBlockEntity bin = (RubbishBinBlockEntity) helper.getBlockEntity(NEAR_BIN);
        helper.succeedWhen(() -> {
            helper.assertTrue(LitterTracker.litterIn(s.zoo()).size() == 1, "no litter yet");
            helper.assertTrue(bin.fill() == RubbishBinBlockEntity.capacity(), "a full bin took more");
            finish(helper, s);
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1000)
    public static void aBinRemovedOnTheWayIsHandledCalmly(GameTestHelper helper) {
        Scene s = rubbishScene(helper, 0.99, () -> placeBin(helper, NEAR_BIN));
        helper.runAfterDelay(15, () -> helper.setBlock(NEAR_BIN, Blocks.AIR));
        helper.succeedWhen(() -> {
            helper.assertTrue(s.visitor().getMainHandItem().isEmpty(), "still holding rubbish");
            helper.assertTrue(s.visitor().isAlive(), "visitor gone");
            finish(helper, s);
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 600)
    public static void visitorAmongLitterLowersCleanlinessAndComplainsOnce(GameTestHelper helper) {
        Scene s = rubbishScene(helper, 0.99, () -> { });
        for (int i = 0; i < 5; i++) {
            Littering.drop(helper.getLevel(), s.zoo(), at(helper, 23 + i % 3, 23 + i / 3), i);
        }
        BlockPos start = helper.absolutePos(START);
        helper.onEachTick(() -> s.visitor().teleportTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5));
        helper.succeedWhen(() -> {
            helper.assertTrue(s.zoo().stats().cleanliness() < 100, "cleanliness still " + s.zoo().stats().cleanliness());
            int complaints = s.zoo().stats().commentTally().getOrDefault(CommentType.RUBBISH_EVERYWHERE.key(), 0);
            helper.assertTrue(complaints == 1, "rubbish complaints: " + complaints);
            finish(helper, s);
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void overviewWarnsAboutFullBinsAndLitterHotspots(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Alerts", TestZoos.rectangle(helper, 2, 2, 29, 29));
        RubbishBinBlockEntity bin = placeBin(helper, BIN);
        while (bin.deposit()) {
            // fill it up
        }
        for (int i = 0; i < 4; i++) {
            Littering.drop(helper.getLevel(), zoo, at(helper, 20, 20), i);
        }
        FacilityCensus.refresh(zoo, helper.getLevel());
        List<String> alerts = DashboardService.snapshot(owner, zoo, DashboardTab.OVERVIEW).alerts().stream().map(c -> c.getString()).toList();
        BlockPos binAt = helper.absolutePos(BIN);
        String full = Component.translatable("zoocraft.alert.bin_full", binAt.getX(), binAt.getZ()).getString();
        helper.assertTrue(alerts.contains(full), "no full-bin alert in " + alerts);
        helper.assertTrue(alerts.stream().anyMatch(a -> a.startsWith("Litter is piling up near")), "no hotspot alert in " + alerts);
        clearLitter(zoo);
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void aZooSavedBeforeCleanlinessExistedStartsClean(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ZooManager zoos = TestZoos.manager(helper);
        Zoo zoo = TestZoos.create(helper, owner, "Old Clean", TestZoos.rectangle(helper, 2, 2, 29, 29));
        for (int i = 0; i < 20; i++) {
            zoos.recordCleanlinessSample(zoo, 0);
        }
        helper.assertTrue(zoo.stats().cleanliness() < 50, "samples did not lower cleanliness");
        Zoo reloaded = zoos.reloadForTest(zoo, true);
        helper.assertTrue(reloaded.stats().cleanliness() == 100, "an old zoo should load as clean, got " + reloaded.stats().cleanliness());
        TestZoos.cleanUp(helper, owner, reloaded);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void manyDropsStayWithinTheZooCap(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Messy", TestZoos.rectangle(helper, 2, 2, 29, 29));
        for (int i = 0; i < 100; i++) {
            Littering.drop(helper.getLevel(), zoo, at(helper, 5 + i % 20, 5 + (i * 7) % 20), i % 3);
        }
        List<LitterEntity> litter = LitterTracker.litterIn(zoo);
        helper.assertTrue(litter.size() <= ZooConfig.MAX_LITTER_PER_ZOO.get(), "too much litter: " + litter.size());
        helper.assertTrue(litter.stream().anyMatch(l -> l.pile() > 1), "no pile grew at the cap");
        helper.assertTrue(litter.stream().allMatch(l -> l.pile() <= LitterRules.MAX_PILE), "a pile grew past the maximum");
        clearLitter(zoo);
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void noLitterOutsideTheZooOrInWater(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Tidy", TestZoos.rectangle(helper, 2, 2, 14, 29));
        helper.assertTrue(Littering.drop(helper.getLevel(), zoo, at(helper, 20, 10), 0) == LitterRules.Outcome.NONE, "dropped outside the zoo");
        helper.setBlock(new BlockPos(8, 2, 8), Blocks.WATER);
        helper.assertTrue(Littering.drop(helper.getLevel(), zoo, at(helper, 8, 8), 0) == LitterRules.Outcome.NONE, "dropped into water");
        helper.assertTrue(LitterTracker.litterIn(zoo).isEmpty(), "litter appeared");
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void hittingLitterCollectsIt(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ServerPlayer passerBy = TestZoos.player(helper, "Passer-by");
        Zoo zoo = TestZoos.create(helper, owner, "Collect", TestZoos.rectangle(helper, 2, 2, 29, 29));
        Littering.drop(helper.getLevel(), zoo, at(helper, 10, 10), 1);
        LitterEntity litter = LitterTracker.litterIn(zoo).getFirst();
        litter.hurt(helper.getLevel().damageSources().playerAttack(passerBy), 1.0f);
        helper.assertTrue(litter.isRemoved(), "litter not collected");
        helper.assertTrue(zoo.stats().litterCollected() == 1, "collection not counted");
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void litterSurvivesSavingAndIsTrackedAgain(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Saved Litter", TestZoos.rectangle(helper, 2, 2, 29, 29));
        Littering.drop(helper.getLevel(), zoo, at(helper, 10, 10), 2);
        LitterEntity litter = LitterTracker.litterIn(zoo).getFirst();
        litter.setPile(3);
        CompoundTag saved = new CompoundTag();
        litter.saveWithoutId(saved);
        litter.discard();
        LitterEntity copy = ZooEntities.LITTER.get().create(helper.getLevel());
        copy.load(saved);
        helper.assertTrue(copy.pile() == 3 && copy.variant() == 2, "pile or variant lost");
        helper.getLevel().addFreshEntity(copy);
        helper.assertTrue(LitterTracker.litterIn(zoo).contains(copy), "reloaded litter not tracked");
        clearLitter(zoo);
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void litterLeftOutsideByABoundaryEditNoLongerCounts(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Shrinking", TestZoos.rectangle(helper, 2, 2, 29, 29));
        Littering.drop(helper.getLevel(), zoo, at(helper, 20, 20), 0);
        helper.assertTrue(LitterTracker.litterIn(zoo).size() == 1, "litter not dropped");
        LitterEntity litter = LitterTracker.litterIn(zoo).getFirst();
        TestZoos.manager(helper).editBoundary(owner, zoo, TestZoos.rectangle(helper, 2, 2, 14, 14));
        helper.assertTrue(LitterTracker.litterIn(zoo).isEmpty(), "litter outside the boundary still counted");
        litter.discard();
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void binFillsUpAndCanBeEmptied(GameTestHelper helper) {
        RubbishBinBlockEntity bin = placeBin(helper, BIN);
        int capacity = ZooConfig.BIN_CAPACITY.get();
        for (int i = 0; i < capacity; i++) {
            helper.assertTrue(bin.deposit(), "deposit " + i + " refused");
        }
        helper.assertFalse(bin.deposit(), "a full bin took more rubbish");
        helper.assertTrue(bin.full(), "bin should be full");
        helper.assertTrue(helper.getBlockState(BIN).getValue(RubbishBinBlock.FILL) == 3, "model should show a full bin");
        RubbishBinBlock.emptyBin(helper.getLevel(), helper.absolutePos(BIN), null);
        helper.assertTrue(bin.fill() == 0 && !bin.full(), "bin not emptied");
        helper.assertTrue(helper.getBlockState(BIN).getValue(RubbishBinBlock.FILL) == 0, "model should show an empty bin");
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void binFillSurvivesSaving(GameTestHelper helper) {
        RubbishBinBlockEntity bin = placeBin(helper, BIN);
        for (int i = 0; i < 7; i++) {
            bin.deposit();
        }
        CompoundTag saved = bin.saveWithFullMetadata(helper.getLevel().registryAccess());
        BlockEntity loaded = BlockEntity.loadStatic(helper.absolutePos(BIN), helper.getBlockState(BIN), saved, helper.getLevel().registryAccess());
        helper.assertTrue(loaded instanceof RubbishBinBlockEntity copy && copy.fill() == 7, "fill level lost");
        helper.succeed();
    }
}
