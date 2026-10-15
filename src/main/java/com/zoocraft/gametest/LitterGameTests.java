package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import com.zoocraft.config.ZooConfig;
import com.zoocraft.core.litter.LitterRules;
import com.zoocraft.litter.LitterEntity;
import com.zoocraft.litter.LitterTracker;
import com.zoocraft.litter.Littering;
import com.zoocraft.registry.ZooEntities;
import com.zoocraft.zoo.Zoo;
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
