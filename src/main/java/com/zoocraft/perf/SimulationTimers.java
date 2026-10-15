package com.zoocraft.perf;

import com.zoocraft.ZooCraft;
import com.zoocraft.core.perf.PerfSlot;
import com.zoocraft.core.perf.TimerSlots;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * ZooCraft's own cost per tick, server-wide and per zoo (Phase 3 spec sections 4.2 and 6.1). Always on: a timed call
 * costs two {@code nanoTime} reads and a few array writes. Server thread only.
 */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class SimulationTimers {
    private static final int SLOTS = PerfSlot.values().length;
    private static final int TOTAL = PerfSlot.ZOOCRAFT_TOTAL.ordinal();
    private static final int PRUNE_TICKS = 600;

    private static TimerSlots global = new TimerSlots(SLOTS, TimerSlots.WINDOW);
    private static final Map<UUID, TimerSlots> ZOOS = new HashMap<>();
    private static long tickStart;

    private SimulationTimers() {
    }

    /** Starts timing; pair with {@link #stop} in a {@code finally} block. */
    public static long start() {
        global.open();
        return System.nanoTime();
    }

    public static void stop(PerfSlot slot, @Nullable UUID zooId, long start) {
        long nanos = System.nanoTime() - start;
        boolean outermost = global.depth() == 1;
        global.close(slot.ordinal(), nanos, TOTAL);
        if (zooId != null) {
            TimerSlots zoo = ZOOS.computeIfAbsent(zooId, id -> new TimerSlots(SLOTS, TimerSlots.WINDOW));
            zoo.add(slot.ordinal(), nanos);
            if (outermost) {
                zoo.add(TOTAL, nanos);
            }
        }
    }

    /** Counts an event without timing it (count-only slots such as {@link PerfSlot#VISITOR_PATH_SHORT}). */
    public static void count(PerfSlot slot, @Nullable UUID zooId) {
        global.add(slot.ordinal(), 0);
        if (zooId != null) {
            ZOOS.computeIfAbsent(zooId, id -> new TimerSlots(SLOTS, TimerSlots.WINDOW)).add(slot.ordinal(), 0);
        }
    }

    public static TimerSlots global() {
        return global;
    }

    public static Optional<TimerSlots> forZoo(UUID zooId) {
        return Optional.ofNullable(ZOOS.get(zooId));
    }

    /** Drops the timers of zoos that no longer exist. */
    public static void prune(Set<UUID> liveZoos) {
        ZOOS.keySet().retainAll(liveZoos);
    }

    public static void resetForTest() {
        global = new TimerSlots(SLOTS, TimerSlots.WINDOW);
        ZOOS.clear();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onTickStart(ServerTickEvent.Pre event) {
        tickStart = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onTickEnd(ServerTickEvent.Post event) {
        global.add(PerfSlot.SERVER_TICK.ordinal(), System.nanoTime() - tickStart);
        global.endTick();
        ZOOS.values().forEach(TimerSlots::endTick);
        if (event.getServer().getTickCount() % PRUNE_TICKS == 0) {
            Set<UUID> live = new HashSet<>();
            for (Zoo zoo : ZooManager.get(event.getServer()).all()) {
                live.add(zoo.id());
            }
            prune(live);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        resetForTest();
    }
}
