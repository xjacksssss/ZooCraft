package com.zoocraft.visitor;

import com.zoocraft.ZooCraft;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Loaded visitors per zoo, kept up to date from entity join and leave events, so limits and group lookups never
 * scan the world. Also releases visitors when their zoo is deleted or its boundary changes.
 */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class VisitorTracker {
    private static final Map<UUID, Set<Visitor>> BY_ZOO = new HashMap<>();
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
        for (Visitor visitor : visitors(zoo.id())) {
            visitor.controller().onZooChanged();
        }
    }
}
