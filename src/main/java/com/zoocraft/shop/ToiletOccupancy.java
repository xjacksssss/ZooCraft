package com.zoocraft.shop;

import com.zoocraft.ZooCraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who is using each toilet point (Phase 2 spec section 8.4): a simple availability model, not individual stalls.
 * Entries expire unless renewed, so a visitor that vanishes mid-visit never blocks a toilet for long. Runtime only.
 */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class ToiletOccupancy {
    /** An occupant not renewed for this long is dropped. */
    public static final long EXPIRY_TICKS = 10 * 20;

    private static final Map<UUID, Map<UUID, Long>> OCCUPANTS = new HashMap<>();

    private ToiletOccupancy() {
    }

    /**
     * Takes a place at the toilet, or renews one already held.
     *
     * @return false when the toilet is full
     */
    public static boolean tryOccupy(UUID toiletId, UUID visitorId, long now, int capacity) {
        Map<UUID, Long> occupants = OCCUPANTS.computeIfAbsent(toiletId, k -> new HashMap<>());
        occupants.values().removeIf(lastSeen -> now - lastSeen > EXPIRY_TICKS);
        if (!occupants.containsKey(visitorId) && occupants.size() >= capacity) {
            return false;
        }
        occupants.put(visitorId, now);
        return true;
    }

    public static void release(UUID toiletId, UUID visitorId) {
        Map<UUID, Long> occupants = OCCUPANTS.get(toiletId);
        if (occupants != null) {
            occupants.remove(visitorId);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        OCCUPANTS.clear();
    }
}
