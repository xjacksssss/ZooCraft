package com.zoocraft.shop;

import com.zoocraft.ZooCraft;
import com.zoocraft.config.ZooConfig;
import com.zoocraft.core.shop.ProductCategory;
import com.zoocraft.litter.RubbishBinBlockEntity;
import com.zoocraft.zoo.BoundaryVertex;
import com.zoocraft.zoo.Shop;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooAlerts;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Checks each zoo's shops on the census timer: containers that have gone are unlinked and reported, and the stock
 * is summarised into a {@link FacilitySnapshot} for visitors to read (Phase 2 spec section 4.4).
 */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class FacilityCensus {
    /** How long a "stock container removed" alert stays on the dashboard: one in-game day. */
    private static final long REMOVED_ALERT_TICKS = 24000L;
    private static final Map<UUID, FacilitySnapshot> SNAPSHOTS = new HashMap<>();
    private static final java.util.Set<UUID> FROZEN = new java.util.HashSet<>();

    private FacilityCensus() {
    }

    public static FacilitySnapshot cached(Zoo zoo) {
        return SNAPSHOTS.getOrDefault(zoo.id(), FacilitySnapshot.EMPTY);
    }

    /** The snapshot, taken now if there isn't one yet (just after a server start), so visitors never plan from nothing. */
    public static FacilitySnapshot of(Zoo zoo, ServerLevel level) {
        FacilitySnapshot snapshot = SNAPSHOTS.get(zoo.id());
        return snapshot != null ? snapshot : refresh(zoo, level);
    }

    /** GameTests only: stops the timer refreshing this zoo, so a deliberately stale snapshot stays stale. */
    public static void freezeForTest(Zoo zoo) {
        FROZEN.add(zoo.id());
    }

    /** GameTests only: drops the zoo's snapshot, as if the server had just started. */
    public static void forgetForTest(Zoo zoo) {
        SNAPSHOTS.remove(zoo.id());
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int interval = ZooConfig.CENSUS_INTERVAL_SECONDS.get() * 20;
        long tick = server.getTickCount();
        for (Zoo zoo : ZooManager.get(server).all()) {
            // Offset from the animal census so the two don't land on the same tick.
            // Every zoo is checked, because bins are found by looking rather than registered.
            if (FROZEN.contains(zoo.id()) || Math.floorMod(tick + zoo.id().hashCode() * 7L + interval / 2, interval) != 0) {
                continue;
            }
            ServerLevel level = server.getLevel(zoo.dimension());
            if (level != null) {
                refresh(zoo, level);
            }
        }
    }

    public static FacilitySnapshot refresh(Zoo zoo, ServerLevel level) {
        ZooManager zoos = ZooManager.get(level.getServer());
        long now = level.getGameTime();
        List<FacilitySnapshot.ShopState> shops = new ArrayList<>();
        for (Shop shop : zoo.shops()) {
            for (BlockPos pos : shop.containers()) {
                if (level.isLoaded(pos) && !ContainerPositions.hasItemHandler(level, pos)) {
                    zoos.unlinkMissing(zoo, shop, pos);
                    ZooAlerts.raiseUntil(zoo, "container_removed:" + pos.asLong(),
                            Component.translatable("zoocraft.alert.container_removed", pos.getX(), pos.getY(), pos.getZ()),
                            now + REMOVED_ALERT_TICKS);
                }
            }
            List<FacilitySnapshot.Product> products = new ArrayList<>();
            for (ShopStock.StockLine line : ShopStock.read(level, shop).lines()) {
                if (line.category() != ProductCategory.UNSELLABLE) {
                    products.add(new FacilitySnapshot.Product(line.itemId(), line.category(), line.count(),
                            ProductCatalog.priceFor(shop, line.itemId())));
                }
            }
            boolean usable = shop.open() && !zoo.isDetached(shop.counter());
            shops.add(new FacilitySnapshot.ShopState(shop.id(), shop.counter(), usable, List.copyOf(products)));
        }
        List<FacilitySnapshot.ToiletState> toilets = zoo.toilets().stream()
                .map(t -> new FacilitySnapshot.ToiletState(t.id(), t.pos(), !zoo.isDetached(t.pos()))).toList();
        FacilitySnapshot snapshot = new FacilitySnapshot(now, List.copyOf(shops), toilets, findBins(zoo, level));
        SNAPSHOTS.put(zoo.id(), snapshot);
        return snapshot;
    }

    /** Bins inside the boundary, from the block entities of the zoo's loaded chunks. Unloaded chunks are skipped. */
    private static List<FacilitySnapshot.BinState> findBins(Zoo zoo, ServerLevel level) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BoundaryVertex v : zoo.boundary()) {
            minX = Math.min(minX, v.x());
            maxX = Math.max(maxX, v.x());
            minZ = Math.min(minZ, v.z());
            maxZ = Math.max(maxZ, v.z());
        }
        List<FacilitySnapshot.BinState> bins = new ArrayList<>();
        int capacity = RubbishBinBlockEntity.capacity();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof RubbishBinBlockEntity bin && zoo.contains(bin.getBlockPos())) {
                        bins.add(new FacilitySnapshot.BinState(bin.getBlockPos().immutable(), bin.fill(), capacity));
                    }
                }
            }
        }
        return List.copyOf(bins);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SNAPSHOTS.clear();
        FROZEN.clear();
    }
}
