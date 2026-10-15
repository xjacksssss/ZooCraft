package com.zoocraft.chunk;

import com.zoocraft.core.perf.PerfSlot;
import com.zoocraft.perf.SimulationTimers;
import com.zoocraft.ZooCraft;
import com.zoocraft.config.ZooConfig;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps a zoo's chunks loaded when its owner or a manager turns on "keep loaded" (spec section 7.8), using
 * NeoForge's ticket system. Covers the boundary plus a two-chunk margin for arrivals, capped by config.
 */
public final class ZooChunkLoader {
    private static final int MARGIN_CHUNKS = 2;
    private static final int UPDATE_TICKS = 100;

    /** Tickets are rebuilt from zoo settings on every start, so stale ones are dropped on load. */
    private static final TicketController CONTROLLER = new TicketController(ZooCraft.id("zoo"),
            (level, helper) -> helper.getEntityTickets().keySet().forEach(helper::removeAllTickets));

    private static final Map<UUID, Set<Long>> FORCED = new HashMap<>();

    private ZooChunkLoader() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterTicketControllersEvent event) -> event.register(CONTROLLER));
        NeoForge.EVENT_BUS.addListener(ZooChunkLoader::onServerTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> FORCED.clear());
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % UPDATE_TICKS != 0) {
            return;
        }
        long timer = SimulationTimers.start();
        try {
            update(event.getServer());
        } finally {
            SimulationTimers.stop(PerfSlot.CHUNK_LOADER, null, timer);
        }
    }

    /** Brings forced chunks in line with every zoo's current settings. */
    public static void update(MinecraftServer server) {
        ZooManager zoos = ZooManager.get(server);
        Set<UUID> seen = new HashSet<>();
        for (Zoo zoo : zoos.all()) {
            seen.add(zoo.id());
            ServerLevel level = server.getLevel(zoo.dimension());
            if (level == null) {
                continue;
            }
            Set<Long> wanted = zoo.keepLoaded() && ZooConfig.ALLOW_ZOO_CHUNK_LOADING.get() ? wantedChunks(zoo) : Set.of();
            apply(level, zoo.id(), wanted);
        }
        for (UUID gone : Set.copyOf(FORCED.keySet())) {
            if (!seen.contains(gone)) {
                // Zoo deleted: release its chunks in every level.
                for (ServerLevel level : server.getAllLevels()) {
                    apply(level, gone, Set.of());
                }
                FORCED.remove(gone);
            }
        }
    }

    static Set<Long> wantedChunks(Zoo zoo) {
        int limit = ZooConfig.MAX_LOADED_CHUNKS_PER_ZOO.get();
        Set<Long> chunks = new LinkedHashSet<>(zoo.shape().coveredChunks());
        int minCx = (zoo.shape().minX() >> 4) - MARGIN_CHUNKS, maxCx = (zoo.shape().maxX() >> 4) + MARGIN_CHUNKS;
        int minCz = (zoo.shape().minZ() >> 4) - MARGIN_CHUNKS, maxCz = (zoo.shape().maxZ() >> 4) + MARGIN_CHUNKS;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                chunks.add(ChunkPos.asLong(cx, cz));
            }
        }
        Set<Long> capped = new LinkedHashSet<>();
        for (long chunk : chunks) {
            if (capped.size() >= limit) {
                break;
            }
            capped.add(chunk);
        }
        return capped;
    }

    private static void apply(ServerLevel level, UUID zooId, Set<Long> wanted) {
        Set<Long> current = FORCED.computeIfAbsent(zooId, k -> new HashSet<>());
        for (long chunk : Set.copyOf(current)) {
            if (!wanted.contains(chunk)) {
                CONTROLLER.forceChunk(level, zooId, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false, true);
                current.remove(chunk);
            }
        }
        for (long chunk : wanted) {
            if (current.add(chunk)) {
                CONTROLLER.forceChunk(level, zooId, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true, true);
            }
        }
    }
}
