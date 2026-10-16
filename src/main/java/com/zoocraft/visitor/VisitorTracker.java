package com.zoocraft.visitor;

import com.zoocraft.ZooCraft;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Loaded visitors per zoo, kept up to date from entity join and leave events, so limits and group lookups never
 * scan the world. Also releases visitors when their zoo is deleted or its boundary changes, and visitors outside the
 * zoo that are frozen in a chunk that no longer ticks (see {@link #onServerTick}).
 */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class VisitorTracker {
    private static final Map<UUID, Set<Visitor>> BY_ZOO = new HashMap<>();
    static final int FROZEN_SWEEP_TICKS = 100;
    private static boolean listenersAdded;

    private VisitorTracker() {
    }

    public static int count(UUID zooId) {
        Set<Visitor> set = BY_ZOO.get(zooId);
        return set == null ? 0 : set.size();
    }

    public static int total() {
        int total = 0;
        for (Set<Visitor> set : BY_ZOO.values()) {
            total += set.size();
        }
        return total;
    }

    public static List<Visitor> visitors(UUID zooId) {
        Set<Visitor> set = BY_ZOO.get(zooId);
        return set == null ? List.of() : List.copyOf(set);
    }

    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Visitor visitor && visitor.zooId() != null) {
            BY_ZOO.computeIfAbsent(visitor.zooId(), k -> Collections.newSetFromMap(new IdentityHashMap<>())).add(visitor);
        }
    }

    @SubscribeEvent
    static void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Visitor visitor) {
            // Chunk unloads mark the entity removed without calling remove(), so places are freed here too.
            visitor.releaseClaims();
        }
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Visitor visitor && visitor.zooId() != null) {
            Set<Visitor> set = BY_ZOO.get(visitor.zooId());
            if (set != null) {
                set.remove(visitor);
                if (set.isEmpty()) {
                    BY_ZOO.remove(visitor.zooId());
                }
            }
        }
    }

    /**
     * Visitors still arriving or queueing (not yet paid) or already on their way out stop moving when their chunk stops
     * ticking, for example when the only player nearby walks off, and would hold places under the visitor limits until
     * someone came back. They are released instead; nothing is lost, as arrivals haven't paid and leavers are done.
     * Admitted visitors inside the zoo are kept: they carry on when their chunk ticks again.
     */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % FROZEN_SWEEP_TICKS != 0 || BY_ZOO.isEmpty()) {
            return;
        }
        for (Set<Visitor> set : List.copyOf(BY_ZOO.values())) {
            for (Visitor visitor : List.copyOf(set)) {
                if (visitor.level() instanceof ServerLevel level
                        && frozenOutside(visitor.phase(), level.isPositionEntityTicking(visitor.blockPosition()))) {
                    visitor.releaseNow(null);
                }
            }
        }
    }

    static boolean frozenOutside(VisitorPhase phase, boolean ticking) {
        return !ticking && phase != VisitorPhase.EXPLORING && phase != VisitorPhase.LEAVING;
    }

    @SubscribeEvent
    static void onServerStarting(ServerAboutToStartEvent event) {
        BY_ZOO.clear();
        if (!listenersAdded) {
            listenersAdded = true;
            ZooManager.addRemoveListener(VisitorTracker::onZooRemoved);
            ZooManager.addBoundaryListener(VisitorTracker::onBoundaryChanged);
            ZooManager.addChangeListener(VisitorTracker::onZooChanged);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        BY_ZOO.clear();
    }

    private static void onZooRemoved(Zoo zoo) {
        for (Visitor visitor : visitors(zoo.id())) {
            visitor.releaseNow(null);
        }
    }

    private static void onBoundaryChanged(Zoo zoo) {
        GateQueues.forget(zoo);
        for (Visitor visitor : visitors(zoo.id())) {
            visitor.controller().onZooChanged();
            if (visitor.phase() == VisitorPhase.EXPLORING || visitor.phase() == VisitorPhase.LEAVING) {
                if (!zoo.contains(visitor.blockPosition())) {
                    visitor.controller().setPhase(VisitorPhase.EXITING);
                }
            }
        }
    }

    private static void onZooChanged(Zoo zoo) {
        GateQueues.forget(zoo);
        for (Visitor visitor : visitors(zoo.id())) {
            visitor.controller().onZooChanged();
        }
    }
}
