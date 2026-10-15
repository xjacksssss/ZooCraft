package com.zoocraft.shop;

import com.zoocraft.ZooCraft;
import com.zoocraft.config.ZooConfig;
import com.zoocraft.core.shop.ProductCategory;
import com.zoocraft.zoo.Shop;
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

    private FacilityCensus() {
    }

    public static FacilitySnapshot cached(Zoo zoo) {
        return SNAPSHOTS.getOrDefault(zoo.id(), FacilitySnapshot.EMPTY);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int interval = ZooConfig.CENSUS_INTERVAL_SECONDS.get() * 20;
        long tick = server.getTickCount();
        for (Zoo zoo : ZooManager.get(server).all()) {
            // Offset from the animal census so the two don't land on the same tick.
            if ((zoo.shops().isEmpty() && zoo.toilets().isEmpty()) || Math.floorMod(tick + zoo.id().hashCode() * 7L + interval / 2, interval) != 0) {
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
        FacilitySnapshot snapshot = new FacilitySnapshot(now, List.copyOf(shops), toilets);
        SNAPSHOTS.put(zoo.id(), snapshot);
        return snapshot;
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SNAPSHOTS.clear();
    }
}
