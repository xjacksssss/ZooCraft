package com.zoocraft.shop;

import com.zoocraft.ZooCraft;
import com.zoocraft.zoo.Zoo;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which shops, toilets and bins visitors have recently failed to reach (Phase 2 spec section 8.5). Learned from
 * real path attempts, like exhibit reachability: a few failed routes mark a facility unreachable for a while, and
 * a successful visit clears the mark. Runtime only.
 */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class FacilityReach {
    private static final Map<UUID, Map<UUID, Long>> UNREACHABLE = new HashMap<>();

    private FacilityReach() {
    }

    public static void markUnreachable(Zoo zoo, UUID facilityId, long untilGameTime) {
        UNREACHABLE.computeIfAbsent(zoo.id(), k -> new HashMap<>()).put(facilityId, untilGameTime);
    }

    public static boolean isUnreachable(Zoo zoo, UUID facilityId, long now) {
        Map<UUID, Long> marks = UNREACHABLE.get(zoo.id());
        if (marks == null) {
            return false;
        }
        Long until = marks.get(facilityId);
        if (until == null) {
            return false;
        }
        if (until <= now) {
            marks.remove(facilityId);
            return false;
        }
        return true;
    }

    public static void clear(Zoo zoo, UUID facilityId) {
        Map<UUID, Long> marks = UNREACHABLE.get(zoo.id());
        if (marks != null) {
            marks.remove(facilityId);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        UNREACHABLE.clear();
    }
}
