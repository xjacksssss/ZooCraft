package com.zoocraft.visitor;

import com.mojang.datafixers.util.Pair;
import com.zoocraft.animal.AnimalCensus;
import com.zoocraft.animal.Exhibit;
import com.zoocraft.animal.Viewing;
import com.zoocraft.animal.ZooCensus;
import com.zoocraft.config.ZooConfig;
import com.zoocraft.core.visitor.LookChoice;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.tslat.smartbrainlib.api.core.behaviour.ExtendedBehaviour;
import net.tslat.smartbrainlib.object.FreePositionTracker;
import net.tslat.smartbrainlib.object.MemoryTest;
import net.tslat.smartbrainlib.util.BrainUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Picks where a visitor looks whenever its last look target expires (Phase 3 spec section 7.6): the animals of the
 * exhibit it is visiting, the way ahead or an animal it passes, now and then its group leader, and only a rare glance
 * at a nearby player. Animals come from the cached census, never from an entity search.
 */
public class ChooseLookTarget extends ExtendedBehaviour<Visitor> {
    private static final MemoryTest MEMORY_REQUIREMENTS = MemoryTest.builder(1).noMemory(MemoryModuleType.LOOK_TARGET);
    /** Path nodes ahead of the next one that a walking visitor looks towards. */
    private static final int NODES_AHEAD = 3;

    @Override
    protected List<Pair<MemoryModuleType<?>, MemoryStatus>> getMemoryRequirements() {
        return MEMORY_REQUIREMENTS;
    }

    @Override
    protected void start(Visitor visitor) {
        long now = visitor.level().getGameTime();
        int radius = ZooConfig.VIEWING_RADIUS.get();
        ZooCensus census = visitor.zoo().map(AnimalCensus::cached).orElse(ZooCensus.EMPTY);
        Path path = visitor.getNavigation().getPath();
        boolean walking = path != null && !path.isDone();
        Mob exhibitAnimal = visitor.currentExhibit == null ? null
                : census.exhibit(visitor.currentExhibit).map(e -> randomVisible(visitor, e.animals(), radius)).orElse(null);
        Mob passingAnimal = walking && exhibitAnimal == null ? passing(visitor, census, radius) : null;
        Player player = visitor.level().getNearestPlayer(visitor.getX(), visitor.getY(), visitor.getZ(), LookChoice.PLAYER_RANGE,
                p -> !p.isSpectator() && p.isAlive());
        Optional<Visitor> leader = VisitorExploration.leaderOf(visitor);

        LookChoice.Kind kind = LookChoice.choose(new LookChoice.Input(player != null, now >= visitor.playerLookAllowedAt,
                        exhibitAnimal != null, walking, passingAnimal != null, leader.isPresent()),
                visitor.getRandom().nextDouble(), visitor.getRandom().nextDouble());
        PositionTracker target = switch (kind) {
            case PLAYER -> {
                visitor.playerLookAllowedAt = now + LookChoice.playerCooldown(visitor.getRandom().nextDouble());
                yield new EntityTracker(player, true);
            }
            case EXHIBIT_ANIMAL -> new EntityTracker(exhibitAnimal, true);
            case PASSING_ANIMAL -> new EntityTracker(passingAnimal, true);
            case AHEAD -> ahead(visitor, path);
            case LEADER -> new EntityTracker(leader.orElseThrow(), true);
            case RANDOM -> randomDirection(visitor);
        };
        BrainUtils.setForgettableMemory(visitor, MemoryModuleType.LOOK_TARGET, target,
                LookChoice.duration(kind, visitor.getRandom().nextDouble()));
    }

    /** A random living animal from {@code animals} close enough to see. */
    private static @Nullable Mob randomVisible(Visitor visitor, List<Mob> animals, int radius) {
        List<Mob> near = new ArrayList<>();
        for (Mob animal : animals) {
            if (animal.isAlive() && close(visitor, animal, radius)) {
                near.add(animal);
            }
        }
        return near.isEmpty() ? null : near.get(visitor.getRandom().nextInt(near.size()));
    }

    /** An animal of an exhibit the visitor is walking past. */
    private static @Nullable Mob passing(Visitor visitor, ZooCensus census, int radius) {
        double reach = (radius + 4.0) * (radius + 4.0);
        for (Exhibit exhibit : census.exhibits()) {
            Vec3 c = exhibit.centre();
            double dx = c.x - visitor.getX(), dz = c.z - visitor.getZ();
            if (dx * dx + dz * dz <= reach) {
                Mob animal = randomVisible(visitor, exhibit.animals(), radius);
                if (animal != null) {
                    return animal;
                }
            }
        }
        return null;
    }

    private static boolean close(Entity visitor, Entity animal, int radius) {
        double dx = animal.getX() - visitor.getX(), dz = animal.getZ() - visitor.getZ();
        return dx * dx + dz * dz <= (double) radius * radius && Viewing.withinHeight(animal.getY() - visitor.getY());
    }

    private static PositionTracker ahead(Visitor visitor, Path path) {
        int index = Math.min(path.getNextNodeIndex() + NODES_AHEAD, path.getNodeCount() - 1);
        BlockPos node = path.getNodePos(index);
        return new BlockPosTracker(Vec3.atBottomCenterOf(node).add(0, 1.5, 0));
    }

    private static PositionTracker randomDirection(Visitor visitor) {
        double angle = visitor.getRandom().nextDouble() * Math.PI * 2;
        return new FreePositionTracker(visitor.getEyePosition().add(Math.cos(angle), 0, Math.sin(angle)));
    }
}
