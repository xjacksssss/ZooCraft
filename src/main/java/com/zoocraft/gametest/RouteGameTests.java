package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import com.zoocraft.core.visitor.PathRoute;
import com.zoocraft.visitor.PathRules;
import com.zoocraft.visitor.VisitorRouteGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/** Long walks routed along paths in the world: ramps followed, cliffs and fences respected (spec 8.4). */
@GameTestHolder(ZooCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RouteGameTests {
    private static void fill(GameTestHelper helper, int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    helper.setBlock(new BlockPos(x, y, z), state);
                }
            }
        }
    }

    /**
     * A terrace 3 blocks high (x 5-20, z 5-10) with a gravel path along its north edge, a ramp off its west end down to
     * a lower path that runs south and then east under the terrace's south cliff.
     */
    private static void buildTerrace(GameTestHelper helper) {
        fill(helper, 5, 2, 5, 20, 3, 10, Blocks.STONE.defaultBlockState());
        fill(helper, 5, 4, 5, 20, 4, 10, Blocks.GRASS_BLOCK.defaultBlockState());
        fill(helper, 5, 4, 5, 20, 4, 5, Blocks.GRAVEL.defaultBlockState());
        helper.setBlock(new BlockPos(4, 2, 5), Blocks.STONE);
        helper.setBlock(new BlockPos(4, 3, 5), Blocks.GRAVEL);
        helper.setBlock(new BlockPos(3, 2, 5), Blocks.GRAVEL);
        fill(helper, 2, 1, 5, 2, 1, 16, Blocks.GRAVEL.defaultBlockState());
        fill(helper, 2, 1, 16, 22, 1, 16, Blocks.GRAVEL.defaultBlockState());
    }

    /** The waypoint in world coordinates (relativePos doesn't undo a rotated test structure, absolutePos does). */
    private static Optional<BlockPos> route(GameTestHelper helper, BlockPos fromFeet, int targetX, int targetZ) {
        BlockPos start = helper.absolutePos(fromFeet);
        BlockPos target = helper.absolutePos(new BlockPos(targetX, 2, targetZ));
        return PathRoute.waypoint(new VisitorRouteGrid(helper.getLevel(), PathRules.ANYWHERE),
                        new PathRoute.Cell(start.getX(), start.getY(), start.getZ()), target.getX(), target.getZ(), 8, 2048)
                .map(c -> new BlockPos(c.x(), c.y(), c.z()));
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE)
    public static void aRouteFromATerraceFollowsThePathToTheRamp(GameTestHelper helper) {
        buildTerrace(helper);
        // From the terrace path's east end, the target lies below the south cliff: the way is west, to the ramp.
        Optional<BlockPos> waypoint = route(helper, new BlockPos(20, 5, 5), 20, 18);
        BlockPos expected = helper.absolutePos(new BlockPos(12, 5, 5));
        helper.assertTrue(waypoint.equals(Optional.of(expected)), "expected " + expected + " on the terrace path, got " + waypoint);
        // Further along, the route goes down the ramp: the waypoint is on the lower path.
        Optional<BlockPos> later = route(helper, new BlockPos(6, 5, 5), 20, 18);
        helper.assertTrue(later.isPresent() && later.get().getY() == helper.absolutePos(new BlockPos(0, 2, 0)).getY(),
                "expected a waypoint down the ramp, got " + later);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE)
    public static void aFenceAcrossThePathStopsTheRoute(GameTestHelper helper) {
        buildTerrace(helper);
        helper.setBlock(new BlockPos(2, 2, 9), Blocks.OAK_FENCE);
        // The only way down is fenced off below the ramp, so the paths lead nowhere nearer: walk straight instead.
        Optional<BlockPos> waypoint = route(helper, new BlockPos(20, 5, 5), 20, 18);
        helper.assertTrue(waypoint.isEmpty(), "routed through a fence: " + waypoint);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE)
    public static void aVisitorOnTheGrassIsLedToThePath(GameTestHelper helper) {
        buildTerrace(helper);
        // On the terrace's grass, three blocks from its path: the first waypoint is along that path, not off the cliff.
        Optional<BlockPos> waypoint = route(helper, new BlockPos(20, 5, 8), 20, 18);
        helper.assertTrue(waypoint.isPresent() && helper.getLevel().getBlockState(waypoint.get().below()).is(Blocks.GRAVEL)
                        && waypoint.get().getY() == helper.absolutePos(new BlockPos(0, 5, 0)).getY(),
                "expected a waypoint on the terrace path, got " + waypoint);
        helper.succeed();
    }
}
