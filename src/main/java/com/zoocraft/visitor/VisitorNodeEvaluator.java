package com.zoocraft.visitor;

import com.zoocraft.config.ZooConfig;
import com.zoocraft.registry.ZooTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Vanilla walking, plus three visitor rules: stay on the allowed side of the zoo boundary, prefer blocks tagged
 * {@code zoocraft:visitor_paths}, and treat closed fence gates like doors that can be opened.
 */
public class VisitorNodeEvaluator extends WalkNodeEvaluator {

    private final Set<Node> adjusted = Collections.newSetFromMap(new IdentityHashMap<>());
    private PathRules rules = PathRules.ANYWHERE;
    /**
     * Extra cost for walking on anything that is not a path block, read once per search. Vanilla's A* weighs the
     * remaining distance at 1.5 per block: a step cost well above 1.5 makes searches across open ground flood sideways
     * and run out of nodes, and one at 1.5 or below makes the search ignore paths beside the straight line. 0.75 keeps
     * both working (Phase 3 spec section 3.4).
     */
    private float offPathCost = 0.75f;

    @Override
    public void prepare(PathNavigationRegion level, Mob mob) {
        super.prepare(level, mob);
        adjusted.clear();
        rules = mob instanceof Visitor visitor ? visitor.pathRules() : PathRules.ANYWHERE;
        offPathCost = ZooConfig.OFF_PATH_COST.get().floatValue();
    }

    @Override
    public void done() {
        super.done();
        adjusted.clear();
    }

    @Override
    protected Node findAcceptedNode(int x, int y, int z, int verticalDeltaLimit, double nodeFloorLevel, Direction direction, PathType pathType) {
        Node node = super.findAcceptedNode(x, y, z, verticalDeltaLimit, nodeFloorLevel, direction, pathType);
        if (node == null || node.costMalus < 0) {
            return node;
        }
        if (!rules.allows(node.x, node.z)) {
            return null;
        }
        if (adjusted.add(node)) {
            BlockState floor = currentContext.getBlockState(new BlockPos(node.x, node.y - 1, node.z));
            if (!floor.is(ZooTags.VISITOR_PATHS)) {
                node.costMalus += offPathCost;
            }
        }
        return node;
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        BlockState state = context.getBlockState(new BlockPos(x, y, z));
        if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
            return PathType.DOOR_WOOD_CLOSED;
        }
        return super.getPathType(context, x, y, z);
    }
}
