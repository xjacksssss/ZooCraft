package com.zoocraft.visitor;

import com.zoocraft.core.visitor.PathRoute;
import com.zoocraft.registry.ZooTags;
import com.zoocraft.zoo.Standing;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The world as {@link PathRoute} sees it for one visitor: standable blocks (two high, not on a fence, wall or gate, out
 * of fluids) that its path rules allow, in loaded chunks. Pens need no special case: their fences aren't floor.
 */
public final class VisitorRouteGrid implements PathRoute.Grid {
    private final ServerLevel level;
    private final PathRules rules;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public VisitorRouteGrid(ServerLevel level, PathRules rules) {
        this.level = level;
        this.rules = rules;
    }

    @Override
    public int stepTo(int x, int z, int fromY) {
        if (!rules.allows(x, z) || !level.isLoaded(cursor.set(x, fromY, z))) {
            return PathRoute.NO_FLOOR;
        }
        if (standable(x, fromY, z)) {
            return fromY;
        }
        if (standable(x, fromY + 1, z)) {
            return fromY + 1;
        }
        return standable(x, fromY - 1, z) ? fromY - 1 : PathRoute.NO_FLOOR;
    }

    private boolean standable(int x, int y, int z) {
        BlockPos feet = new BlockPos(x, y, z);
        return Standing.isStandable(level, feet) && level.getFluidState(feet).isEmpty();
    }

    @Override
    public int pathStepTo(int x, int z, int fromY) {
        for (int y : new int[]{fromY, fromY + 1, fromY - 1}) {
            if (path(x, y, z) && rules.allows(x, z) && standable(x, y, z)) {
                return y;
            }
        }
        return PathRoute.NO_FLOOR;
    }

    @Override
    public boolean path(int x, int y, int z) {
        return level.getBlockState(cursor.set(x, y - 1, z)).is(ZooTags.VISITOR_PATHS);
    }
}
