package com.zoocraft.litter;

import com.zoocraft.ZooCraft;
import com.zoocraft.zoo.Zoo;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * All loaded litter, kept from entity join and leave events, so caps and cleanliness never search the world
 * (Phase 2 spec section 9.3). Which zoo a piece belongs to is decided by where it lies now, so litter left outside
 * by a boundary edit stops counting for that zoo.
 */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class LitterTracker {
    private static final Set<LitterEntity> LOADED = new LinkedHashSet<>();

    private LitterTracker() {
    }

    public static List<LitterEntity> litterIn(Zoo zoo) {
        List<LitterEntity> inZoo = new ArrayList<>();
        for (LitterEntity litter : LOADED) {
            if (!litter.isRemoved() && litter.level().dimension().equals(zoo.dimension()) && zoo.contains(litter.blockPosition())) {
                inZoo.add(litter);
            }
        }
        return inZoo;
    }

    /** Loaded litter across the whole server. */
    public static int total() {
        return LOADED.size();
    }

    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof LitterEntity litter) {
            LOADED.add(litter);
        }
    }

    @SubscribeEvent
    static void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof LitterEntity litter) {
            LOADED.remove(litter);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        LOADED.clear();
    }
}
