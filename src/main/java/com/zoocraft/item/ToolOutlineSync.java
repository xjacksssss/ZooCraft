package com.zoocraft.item;

import com.zoocraft.ZooCraft;
import com.zoocraft.network.ZooOutlinesPayload;
import com.zoocraft.registry.ZooItems;
import com.zoocraft.shop.FacilityReach;
import com.zoocraft.zoo.Gate;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import com.zoocraft.zoo.ZooPermission;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Once a second, sends nearby zoo outlines to players holding a zoo tool, and clears them when they stop. */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class ToolOutlineSync {
    private static final int INTERVAL_TICKS = 20;
    private static final int CHUNK_RADIUS = 8;
    private static final Set<UUID> SHOWING = new HashSet<>();

    private ToolOutlineSync() {
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % INTERVAL_TICKS != 0) {
            return;
        }
        ZooManager zoos = ZooManager.get(event.getServer());
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            boolean holding = ZooItems.isZooTool(player.getMainHandItem()) || ZooItems.isZooTool(player.getOffhandItem());
            if (holding) {
                SHOWING.add(player.getUUID());
                PacketDistributor.sendToPlayer(player, outlinesFor(player, zoos));
            } else if (SHOWING.remove(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, ZooOutlinesPayload.NONE);
            }
        }
    }

    private static ZooOutlinesPayload outlinesFor(ServerPlayer player, ZooManager zoos) {
        long gameTime = player.serverLevel().getGameTime();
        List<Zoo> near = zoos.zoosNear(player.level(), player.blockPosition(), CHUNK_RADIUS);
        return new ZooOutlinesPayload(near.stream().limit(64).map(zoo -> new ZooOutlinesPayload.Outline(
                zoo.id(), zoo.name(), ZooManager.can(player, zoo, ZooPermission.EDIT), zoo.boundary(),
                zoo.gates().stream().map(g -> marker(zoo, g, gameTime)).toList(), facilities(zoo, gameTime))).toList());
    }

    /** Shop and toilet markers; anything visitors can't use or reach is drawn red (Phase 2 spec section 6). */
    public static ZooOutlinesPayload.FacilityMarkers facilities(Zoo zoo, long gameTime) {
        return new ZooOutlinesPayload.FacilityMarkers(
                zoo.shops().stream().map(s -> new ZooOutlinesPayload.ShopMarker(s.counter(), s.containers(),
                        s.open() && !zoo.isDetached(s.counter()) && !s.containers().isEmpty()
                                && !FacilityReach.isUnreachable(zoo, s.id(), gameTime))).toList(),
                zoo.toilets().stream().map(t -> new ZooOutlinesPayload.ToiletMarker(t.pos(),
                        !zoo.isDetached(t.pos()) && !FacilityReach.isUnreachable(zoo, t.id(), gameTime))).toList());
    }

    private static ZooOutlinesPayload.GateMarker marker(Zoo zoo, Gate gate, long gameTime) {
        return new ZooOutlinesPayload.GateMarker(gate.pos(), zoo.gateStatus(gate, gameTime).ordinal());
    }
}
