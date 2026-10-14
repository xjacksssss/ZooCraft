package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks that the GameTest harness and the shared floor template work. */
@GameTestHolder(ZooCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HarnessGameTests {
    @GameTest(template = GameTestTemplates.FLOOR)
    public static void floorTemplateLoads(GameTestHelper helper) {
        helper.spawn(EntityType.PIG, new BlockPos(5, 1, 5));
        helper.succeedWhenEntityPresent(EntityType.PIG, new BlockPos(5, 1, 5));
    }
}
