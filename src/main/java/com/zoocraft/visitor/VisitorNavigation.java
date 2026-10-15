package com.zoocraft.visitor;

import com.zoocraft.config.ZooConfig;
import com.zoocraft.core.perf.PerfSlot;
import com.zoocraft.perf.MissedTargets;
import com.zoocraft.perf.SimulationTimers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;
import net.tslat.smartbrainlib.api.core.navigation.SmoothGroundNavigation;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/** SmartBrainLib's smooth ground navigation with the visitor node evaluator, timed for {@code /zoo debug perf}. */
public class VisitorNavigation extends SmoothGroundNavigation {
    public VisitorNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new VisitorNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        this.nodeEvaluator.setCanOpenDoors(true);
        return createSmoothPathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    @Override
    public void tick() {
        long timer = SimulationTimers.start();
        try {
            super.tick();
        } finally {
            SimulationTimers.stop(PerfSlot.VISITOR_NAVIGATION, zooId(), timer);
        }
    }

    @Override
    protected @Nullable Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange) {
        if (path != null && !path.isDone() && targets.contains(getTargetPos())) {
            return path; // vanilla reuses the current path: not a plan
        }
        long timer = SimulationTimers.start();
        Path result = null;
        try {
            // The node budget was sized for the attribute's range when this navigation was built; scale it to the setting.
            float range = ZooConfig.VISITOR_FOLLOW_RANGE.get();
            setMaxVisitedNodesMultiplier(range / 48.0f);
            result = super.createPath(targets, regionOffset, offsetUpward, accuracy, range);
            return result;
        } finally {
            SimulationTimers.stop(PerfSlot.VISITOR_PATHFINDING, zooId(), timer);
            if (!targets.isEmpty()) {
                BlockPos target = targets.iterator().next();
                if (result == null || !result.canReach()) {
                    SimulationTimers.count(PerfSlot.VISITOR_PATH_SHORT, zooId());
                    MissedTargets.record(target);
                    noteMiss(target);
                } else {
                    misses.remove(target);
                }
            }
        }
    }

    /** Recent targets a plan couldn't reach although they were near enough for one search, with how often in a row. */
    private final java.util.LinkedHashMap<BlockPos, Integer> misses = new java.util.LinkedHashMap<>();
    private static final int MISSES_TRACKED = 4;
    /** Targets nearer than this share of the search range should be reachable in one plan. */
    private static final double NEAR_SHARE = 0.75;

    private void noteMiss(BlockPos target) {
        double near = ZooConfig.VISITOR_FOLLOW_RANGE.get() * NEAR_SHARE;
        if (mob.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) > near * near) {
            return; // a far target gets a partial path by design
        }
        misses.merge(target.immutable(), 1, Integer::sum);
        while (misses.size() > MISSES_TRACKED) {
            misses.remove(misses.keySet().iterator().next());
        }
    }

    /** How many plans in a row couldn't reach {@code target} although it was near enough for one search. */
    public int missesInARow(BlockPos target) {
        return misses.getOrDefault(target, 0);
    }

    public void forgetMisses(BlockPos target) {
        misses.remove(target);
    }

    private @Nullable Path shortcutPath;
    private int shortcutNode = -1;
    private int shortcutAttempts;

    /**
     * SmartBrainLib tries to skip ahead along the path on every tick, tracing the visitor's box through every block
     * to each later node. The answer only changes when the visitor reaches a new node or gets a new path, so in
     * {@link ShortcutMode#EVERY_NODE} it only tries then.
     */
    @Override
    public boolean attemptShortcut(int targetNode, Vec3 entityPosition) {
        ShortcutMode mode = ZooConfig.PATH_SHORTCUT_MODE.get();
        if (mode == ShortcutMode.OFF || path == null || path.isDone()) {
            return false;
        }
        if (mode == ShortcutMode.EVERY_NODE && path == shortcutPath && path.getNextNodeIndex() == shortcutNode) {
            return false;
        }
        shortcutAttempts++;
        boolean skipped = super.attemptShortcut(targetNode, entityPosition);
        shortcutPath = path;
        shortcutNode = path.getNextNodeIndex();
        return skipped;
    }

    /** Real shortcut attempts so far, for tests. */
    public int shortcutAttempts() {
        return shortcutAttempts;
    }

    private @Nullable UUID zooId() {
        return mob instanceof Visitor visitor ? visitor.zooId() : null;
    }
}
