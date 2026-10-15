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
        if (!canUpdatePath()) {
            return null; // in the air or in a vehicle: vanilla doesn't search, so neither a plan nor a miss
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
                    // Far targets get partial paths by design; a short plan to a near one is a real miss.
                    if (mob.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) <= nearSq()) {
                        SimulationTimers.count(PerfSlot.VISITOR_PATH_SHORT_NEAR, zooId());
                    }
                    MissedTargets.record(target);
                    noteMiss(target, result);
                } else {
                    misses.remove(target);
                }
            }
        }
    }

    /** A target plans keep missing: how often in a row, and how close the visitor was when the run started. */
    private record Miss(int count, double distance) {
    }

    /** Recent targets a plan couldn't reach although they were near enough for one search. */
    private final java.util.LinkedHashMap<BlockPos, Miss> misses = new java.util.LinkedHashMap<>();
    private static final int MISSES_TRACKED = 4;
    /** Targets nearer than this share of the search range should be reachable in one plan. */
    private static final double NEAR_SHARE = 0.75;
    /** A path ending this close to the target is close enough to arrive (the exhibit arrival distance). */
    private static final double CLOSE_ENOUGH = 2.0;
    /** Getting at least this much closer since the last miss is progress, so the count starts again. */
    private static final double PROGRESS = 1.0;

    private static double nearSq() {
        double near = ZooConfig.VISITOR_FOLLOW_RANGE.get() * NEAR_SHARE;
        return near * near;
    }

    private void noteMiss(BlockPos target, @Nullable Path result) {
        if (result != null && result.getDistToTarget() <= CLOSE_ENOUGH) {
            misses.remove(target); // the visitor can get close enough to arrive
            return;
        }
        double near = ZooConfig.VISITOR_FOLLOW_RANGE.get() * NEAR_SHARE;
        double distance = Math.sqrt(mob.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5));
        if (distance > near) {
            return; // a far target gets a partial path by design
        }
        Miss previous = misses.remove(target);
        Miss next = previous == null || distance < previous.distance() - PROGRESS
                ? new Miss(1, distance)
                : new Miss(previous.count() + 1, previous.distance());
        misses.put(target.immutable(), next); // re-inserted, so the most recent targets are kept
        while (misses.size() > MISSES_TRACKED) {
            misses.remove(misses.keySet().iterator().next());
        }
    }

    /**
     * How many plans in a row couldn't reach {@code target} although it was near enough for one search, the path
     * didn't end close enough to arrive, and the visitor got no closer in between.
     */
    public int missesInARow(BlockPos target) {
        Miss miss = misses.get(target);
        return miss == null ? 0 : miss.count();
    }

    public void forgetMisses(BlockPos target) {
        misses.remove(target);
    }

    /** Called when the visitor heads somewhere new: old misses say nothing about the next walk. */
    public void forgetAllMisses() {
        misses.clear();
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
