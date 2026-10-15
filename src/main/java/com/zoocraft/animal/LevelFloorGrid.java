package com.zoocraft.animal;

import com.zoocraft.core.exhibit.FloorGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The world as {@link FloorGrid} for animals (Phase 3 spec section 8.1): solid floor that isn't a fence, wall or fence
 * gate, under a feet block with no collision (air, plants, water, an open gate). Fences, walls, glass and closed
 * gates stop the fill; unloaded chunks count as no floor.
 */
final class LevelFloorGrid implements FloorGrid {
    private final ServerLevel level;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    LevelFloorGrid(ServerLevel level) {
        this.level = level;
    }

    @Override
    public int stepTo(int x, int z, int fromY) {
        if (floor(x, fromY, z)) {
            return fromY;
        }
        if (floor(x, fromY + 1, z)) {
            return fromY + 1;
        }
        return floor(x, fromY - 1, z) ? fromY - 1 : NO_FLOOR;
    }

    boolean floor(int x, int y, int z) {
        cursor.set(x, y - 1, z);
        if (!level.isLoaded(cursor)) {
            return false;
        }
        BlockState below = level.getBlockState(cursor);
        if (below.getCollisionShape(level, cursor).isEmpty() || below.is(BlockTags.FENCES) || below.is(BlockTags.WALLS)
                || below.is(BlockTags.FENCE_GATES)) {
            return false;
        }
        cursor.set(x, y, z);
        return level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty();
    }
}
