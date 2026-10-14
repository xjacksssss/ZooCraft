package com.zoocraft.visitor;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.tslat.smartbrainlib.api.core.navigation.SmoothGroundNavigation;

/** SmartBrainLib's smooth ground navigation with the visitor node evaluator. */
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
}
