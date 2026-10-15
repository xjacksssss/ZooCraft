package com.zoocraft.visitor;

import com.zoocraft.core.perf.PerfSlot;
import com.zoocraft.perf.SimulationTimers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
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
        long timer = SimulationTimers.start();
        try {
            return super.createPath(targets, regionOffset, offsetUpward, accuracy, followRange);
        } finally {
            SimulationTimers.stop(PerfSlot.VISITOR_PATHFINDING, zooId(), timer);
        }
    }

    private @Nullable UUID zooId() {
        return mob instanceof Visitor visitor ? visitor.zooId() : null;
    }
}
