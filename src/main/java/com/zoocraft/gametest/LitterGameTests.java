package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import com.zoocraft.config.ZooConfig;
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
