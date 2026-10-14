package com.zoocraft.zoo;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;

/** Whether a villager-sized visitor can stand at a position. Doors and fence gates count as passable. */
public final class Standing {
    private Standing() {
    }

    public static boolean isStandable(BlockGetter level, BlockPos feet) {
        BlockPos belowPos = feet.below();
        BlockState below = level.getBlockState(belowPos);
        if (below.getCollisionShape(level, belowPos).isEmpty()
                || below.is(BlockTags.FENCES) || below.is(BlockTags.WALLS) || below.is(BlockTags.FENCE_GATES)) {
            return false;
        }
        return isPassable(level, feet, true) && isPassable(level, feet.above(), false);
    }

    /** Searches up to {@code range} blocks above and below for a standable position in the same column. */
    public static Optional<BlockPos> findStandable(BlockGetter level, BlockPos near, int range) {
        if (isStandable(level, near)) {
            return Optional.of(near);
        }
        for (int dy = 1; dy <= range; dy++) {
            if (isStandable(level, near.above(dy))) {
                return Optional.of(near.above(dy));
            }
            if (isStandable(level, near.below(dy))) {
                return Optional.of(near.below(dy));
            }
        }
        return Optional.empty();
    }

    private static boolean isPassable(BlockGetter level, BlockPos pos, boolean feet) {
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.DOORS) || state.is(BlockTags.FENCE_GATES)) {
            return true;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return true;
        }
        // Thin things such as carpet and snow layers can be walked over.
        return feet && shape.max(net.minecraft.core.Direction.Axis.Y) <= 0.1875;
    }
}
