package com.zoocraft.shop;

import com.zoocraft.ZooCraft;
import com.zoocraft.core.crowd.Occupancy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.UUID;

/**
 * Who is using each toilet point and shop counter (Phase 2 spec section 8.4, Phase 3 spec section 7.3): an
 * availability model, not individual stalls or tills. Places expire unless renewed, so a visitor that vanishes never
 * blocks a facility for long, and removed visitors release theirs at once. Runtime only.
 */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class FacilityOccupancy {
    /** A place not renewed for this long is dropped. */
    public static final long EXPIRY_TICKS = 10 * 20;

    private static final Occupancy<UUID> PLACES = new Occupancy<>(EXPIRY_TICKS);

    private FacilityOccupancy() {
    }

    /**
     * Takes a place at the toilet or counter, or renews one already held.
     *
     * @return false when it is full
     */
    public static boolean tryOccupy(UUID facilityId, UUID visitorId, long now, int capacity) {
        return PLACES.claim(facilityId, visitorId, now, capacity);
    }

    public static void release(UUID facilityId, UUID visitorId) {
        PLACES.release(facilityId, visitorId);
    }

    /** Frees every place the visitor holds (it left or was removed). */
    public static void releaseAll(UUID visitorId) {
        PLACES.releaseAll(visitorId);
    }

    public static int count(UUID facilityId, long now) {
        return PLACES.count(facilityId, now);
    }

    /** The most visitors using any one facility. */
    public static int maxCount(long now) {
        return PLACES.maxCount(now);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        PLACES.clear();
    }
}
