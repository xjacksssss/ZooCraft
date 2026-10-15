package com.zoocraft.visitor;

import com.zoocraft.core.perf.PerfSlot;
import com.zoocraft.perf.SimulationTimers;
import com.zoocraft.ZooCraft;
import com.zoocraft.config.ZooConfig;
import com.zoocraft.registry.ZooEntities;
import com.zoocraft.zoo.Gate;
import com.zoocraft.zoo.Standing;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooAlerts;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Brings visitor groups to open zoos (spec section 7.3). */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class VisitorSpawner {
    private static final int SPOT_ATTEMPTS = 6;
    private static final int FAILURES_BEFORE_ALERT = 5;
    private static final Map<UUID, Integer> FAILURES = new HashMap<>();

    private VisitorSpawner() {
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        long timer = SimulationTimers.start();
        try {
            spawnTick(event.getServer());
        } finally {
            SimulationTimers.stop(PerfSlot.SPAWNER, null, timer);
        }
    }

    private static void spawnTick(MinecraftServer server) {
        int interval = ZooConfig.SPAWN_INTERVAL_SECONDS.get() * 20;
        long tick = server.getTickCount();
        for (Zoo zoo : ZooManager.get(server).all()) {
            if (Math.floorMod(tick + zoo.id().hashCode(), interval) != 0 || !zoo.open()) {
                continue;
            }
            ServerLevel level = server.getLevel(zoo.dimension());
            if (level == null || level.random.nextDouble() >= spawnChance(zoo)) {
                continue;
            }
            trySpawnGroup(level, zoo);
        }
    }

    static double spawnChance(Zoo zoo) {
        return 0.35 + zoo.reputation() / 200.0;
    }

    static int rollGroupSize(RandomSource random) {
        int roll = random.nextInt(100);
        if (roll < 45) return 1;
        if (roll < 75) return 2;
        if (roll < 90) return 3;
        return 4;
    }

    /** Space left under both the per-zoo and server-wide limits. */
    static int capacity(Zoo zoo) {
        return Math.min(ZooConfig.MAX_VISITORS_PER_ZOO.get() - VisitorTracker.count(zoo.id()),
                ZooConfig.MAX_VISITORS_TOTAL.get() - VisitorTracker.total());
    }

    /**
     * Tries to spawn one group outside a random usable gate. Public so tests and the debug command can force it.
     *
     * @return the spawned visitors, empty when no group could be placed
     */
    public static List<Visitor> trySpawnGroup(ServerLevel level, Zoo zoo) {
        int capacity = capacity(zoo);
        long now = level.getGameTime();
        List<Gate> gates = new ArrayList<>(zoo.usableGates(now));
        gates.removeIf(g -> !level.isPositionEntityTicking(g.pos()));
        if (capacity <= 0 || gates.isEmpty()) {
            return List.of();
        }
        Gate gate = gates.get(level.random.nextInt(gates.size()));
        int size = Math.min(capacity, rollGroupSize(level.random));
        Optional<BlockPos> spot = findSpawnSpot(level, zoo, gate);
        if (spot.isEmpty()) {
            if (FAILURES.merge(zoo.id(), 1, Integer::sum) >= FAILURES_BEFORE_ALERT) {
                ZooAlerts.raise(zoo, ZooAlerts.NO_ROUTE_TO_GATES);
            }
            return List.of();
        }
        FAILURES.remove(zoo.id());
        ZooAlerts.clear(zoo, ZooAlerts.NO_ROUTE_TO_GATES);
        UUID groupId = UUID.randomUUID();
        List<Visitor> group = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Visitor visitor = ZooEntities.VISITOR.get().create(level);
            if (visitor == null) {
                break;
            }
            BlockPos at = spot.get();
            visitor.moveTo(at.getX() + 0.5 + (i == 0 ? 0 : level.random.nextDouble() - 0.5), at.getY(),
                    at.getZ() + 0.5 + (i == 0 ? 0 : level.random.nextDouble() - 0.5), level.random.nextFloat() * 360, 0);
            visitor.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
            visitor.begin(zoo, gate, groupId, i == 0);
            level.addFreshEntity(visitor);
            group.add(visitor);
        }
        return group;
    }

    /** A standable spot 16 to 32 blocks outside the gate, outside every zoo, from which the gate can be reached. */
    static Optional<BlockPos> findSpawnSpot(ServerLevel level, Zoo zoo, Gate gate) {
        double[] normal = zoo.shape().outwardNormalAt(gate.pos().getX() + 0.5, gate.pos().getZ() + 0.5);
        ZooManager zoos = ZooManager.get(level.getServer());
        for (int attempt = 0; attempt < SPOT_ATTEMPTS; attempt++) {
            double distance = 16 + level.random.nextInt(17);
            double lateral = level.random.nextInt(17) - 8;
            int x = (int) Math.floor(gate.pos().getX() + 0.5 + normal[0] * distance - normal[1] * lateral);
            int z = (int) Math.floor(gate.pos().getZ() + 0.5 + normal[1] * distance + normal[0] * lateral);
            BlockPos column = new BlockPos(x, gate.pos().getY(), z);
            if (!level.isPositionEntityTicking(column)) {
                continue;
            }
            Optional<BlockPos> found = Standing.findInColumn(level, x, gate.pos().getY(), z, 8);
            if (found.isEmpty()) {
                continue;
            }
            BlockPos feet = found.get();
            if (zoos.zooAt(level, feet).isPresent() || !level.getFluidState(feet).isEmpty()) {
                continue;
            }
            if (canWalk(level, zoo, gate, feet)) {
                return Optional.of(feet);
            }
        }
        return Optional.empty();
    }

    /** Uses a throwaway visitor to check that a path from the spot to the gate exists. */
    private static boolean canWalk(ServerLevel level, Zoo zoo, Gate gate, BlockPos from) {
        Visitor probe = ZooEntities.VISITOR.get().create(level);
        if (probe == null) {
            return false;
        }
        probe.moveTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, 0, 0);
        probe.setOnGround(true);
        probe.pathRules = VisitorController.rulesFor(VisitorPhase.ARRIVING, zoo);
        Path path = probe.getNavigation().createPath(gate.outside(), 1);
        probe.discard();
        return path != null && path.canReach();
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        FAILURES.clear();
        ZooAlerts.reset();
    }
}
