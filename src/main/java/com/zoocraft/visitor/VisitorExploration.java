package com.zoocraft.visitor;

import com.zoocraft.zoo.Zoo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Where admitted visitors go next. Group members stay near their leader; otherwise visitors wander to
 * standable spots inside the zoo near where they are.
 */
final class VisitorExploration {
    private static final int WANDER_RADIUS = 20;
    private static final int ATTEMPTS = 10;

    private VisitorExploration() {
    }

    static Optional<BlockPos> chooseTarget(Visitor visitor, Zoo zoo, ServerLevel level) {
        Optional<Visitor> leader = leaderOf(visitor);
        if (leader.isPresent()) {
            Optional<BlockPos> near = randomSpot(visitor, zoo, level, leader.get().blockPosition(), 3);
            if (near.isPresent()) {
                return near;
            }
        }
        return randomSpot(visitor, zoo, level, visitor.blockPosition(), WANDER_RADIUS);
    }

    static Optional<BlockPos> randomSpot(Visitor visitor, Zoo zoo, ServerLevel level, BlockPos around, int radius) {
        for (int i = 0; i < ATTEMPTS; i++) {
            int x = around.getX() + visitor.getRandom().nextInt(radius * 2 + 1) - radius;
            int z = around.getZ() + visitor.getRandom().nextInt(radius * 2 + 1) - radius;
            if (!zoo.shape().containsBlock(x, z) || !level.isLoaded(new BlockPos(x, 0, z))) {
                continue;
            }
            Optional<BlockPos> feet = com.zoocraft.zoo.Standing.findInColumn(level, x, around.getY(), z, 4);
            if (feet.isPresent() && VisitorController.goodSpot(level, zoo, feet.get())) {
                return feet;
            }
        }
        return Optional.empty();
    }

    /** The visitor's group leader, if it has one that is still exploring. */
    static Optional<Visitor> leaderOf(Visitor visitor) {
        if (visitor.isLeader() || visitor.groupId() == null || visitor.zooId() == null) {
            return Optional.empty();
        }
        for (Visitor other : VisitorTracker.visitors(visitor.zooId())) {
            if (other != visitor && other.isLeader() && visitor.groupId().equals(other.groupId())
                    && other.phase() == VisitorPhase.EXPLORING && !other.isRemoved()) {
                return Optional.of(other);
            }
        }
        return Optional.empty();
    }

    /** Called every step while exploring; M3 uses it for animal viewing. */
    static void observe(Visitor visitor, Zoo zoo, long now) {
    }

    /** Called when the visitor reaches its chosen spot. */
    static void onArrived(Visitor visitor, Zoo zoo, long now) {
    }

    /** Called when the visitor could not reach its chosen spot. */
    static void onUnreachable(Visitor visitor, Zoo zoo) {
    }
}
