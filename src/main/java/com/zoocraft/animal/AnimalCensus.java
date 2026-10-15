package com.zoocraft.animal;

import com.zoocraft.ZooCraft;
import com.zoocraft.config.ZooConfig;
import com.zoocraft.core.exhibit.AnimalPoint;
import com.zoocraft.core.exhibit.ExhibitGrouping;
import com.zoocraft.registry.ZooTags;
import com.zoocraft.zoo.BoundaryVertex;
import com.zoocraft.zoo.Gate;
import com.zoocraft.zoo.SavedExhibit;
import com.zoocraft.zoo.Standing;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooAlerts;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Counts each open zoo's animals every {@code censusIntervalSeconds} with one entity query, groups them into
 * exhibits and caches the result. Visitors only read this cache (spec section 8.3).
 */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class AnimalCensus {
    /** Animals within this distance of each other (chained) share an exhibit. */
    public static final double LINK_DISTANCE = 8.0;
    /** How far above and below the zoo's corners and gates animals are looked for. */
    private static final int SEARCH_BELOW = 48;
    private static final int SEARCH_ABOVE = 64;
    private static final int MAX_ANIMALS = 512;
    private static final int MAX_SPOTS = 6;
    private static final int SPOT_SAMPLES = 16;

    private static final Map<UUID, ZooCensus> CENSUS = new HashMap<>();
    private static final Map<UUID, List<Exhibit>> EXHIBITS = new HashMap<>();

    private AnimalCensus() {
    }

    /** The latest census, refreshed now if it is missing or stale. */
    public static ZooCensus of(Zoo zoo, ServerLevel level) {
        ZooCensus census = CENSUS.get(zoo.id());
        long maxAge = ZooConfig.CENSUS_INTERVAL_SECONDS.get() * 40L;
        if (census == null || level.getGameTime() - census.takenAt() > maxAge) {
            census = refresh(zoo, level);
        }
        return census;
    }

    /** The cached census without refreshing it (for read-only views). */
    public static ZooCensus cached(Zoo zoo) {
        return CENSUS.getOrDefault(zoo.id(), ZooCensus.EMPTY);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int interval = ZooConfig.CENSUS_INTERVAL_SECONDS.get() * 20;
        long tick = server.getTickCount();
        for (Zoo zoo : ZooManager.get(server).all()) {
            if (Math.floorMod(tick + zoo.id().hashCode() * 7L, interval) != 0) {
                continue;
            }
            ServerLevel level = server.getLevel(zoo.dimension());
            if (level != null && (zoo.open() || CENSUS.containsKey(zoo.id()))) {
                refresh(zoo, level);
            }
        }
    }

    public static ZooCensus refresh(Zoo zoo, ServerLevel level) {
        List<Mob> animals = new ArrayList<>(level.getEntitiesOfClass(Mob.class, searchBox(zoo, level),
                mob -> AnimalRecognition.isExhibitAnimal(mob) && zoo.contains(mob.blockPosition())));
        if (animals.size() > MAX_ANIMALS) {
            animals = animals.subList(0, MAX_ANIMALS);
        }
        Map<UUID, String> speciesOf = new HashMap<>();
        Map<String, Component> names = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<UUID, Mob> byId = new HashMap<>();
        List<AnimalPoint> points = new ArrayList<>();
        for (Mob mob : animals) {
            String species = AnimalRecognition.species(mob.getType()).toString();
            speciesOf.put(mob.getUUID(), species);
            names.putIfAbsent(species, AnimalRecognition.speciesName(mob.getType()));
            counts.merge(species, 1, Integer::sum);
            byId.put(mob.getUUID(), mob);
            points.add(new AnimalPoint(mob.getUUID(), species, mob.getX(), mob.getY(), mob.getZ()));
        }

        List<List<AnimalPoint>> groups = ExhibitGrouping.group(points, LINK_DISTANCE);
        List<Exhibit> previous;
        List<Set<UUID>> previousIds;
        if (EXHIBITS.containsKey(zoo.id())) {
            previous = EXHIBITS.get(zoo.id());
            previousIds = previous.stream().map(e -> idsOf(e.animals())).toList();
        } else {
            // First count since loading: match against the exhibits saved with the zoo, by their animals' UUIDs.
            List<SavedExhibit> saved = zoo.reports().exhibits();
            previous = saved.stream().map(s -> Exhibit.restored(s.id(), s.views())).toList();
            previousIds = saved.stream().map(s -> (Set<UUID>) new HashSet<>(s.animals())).toList();
        }
        List<Set<UUID>> currentIds = groups.stream().map(g -> {
            Set<UUID> ids = new HashSet<>();
            g.forEach(p -> ids.add(p.id()));
            return ids;
        }).toList();
        int[] match = ExhibitGrouping.match(previousIds, currentIds);

        List<Exhibit> exhibits = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            Exhibit exhibit = match[i] >= 0 ? previous.get(match[i]) : new Exhibit(UUID.randomUUID());
            List<Mob> members = groups.get(i).stream().map(p -> byId.get(p.id())).toList();
            Set<String> species = new HashSet<>();
            double x = 0, y = 0, z = 0;
            for (AnimalPoint p : groups.get(i)) {
                species.add(p.species());
                x += p.x();
                y += p.y();
                z += p.z();
            }
            int n = groups.get(i).size();
            Vec3 centre = new Vec3(x / n, y / n, z / n);
            exhibit.update(members, species, centre, viewingSpots(level, zoo, centre));
            exhibits.add(exhibit);
        }
        EXHIBITS.put(zoo.id(), exhibits);
        ZooManager.get(level.getServer()).recordExhibits(zoo, exhibits.stream()
                .map(e -> new SavedExhibit(e.id(), e.animals().stream().map(Mob::getUUID).toList(), e.viewCount())).toList());

        ZooCensus census = new ZooCensus(level.getGameTime(), List.copyOf(animals), Map.copyOf(speciesOf), List.copyOf(exhibits),
                Map.copyOf(names), Map.copyOf(counts));
        CENSUS.put(zoo.id(), census);
        if (animals.isEmpty()) {
            ZooAlerts.raise(zoo, ZooAlerts.NO_ANIMALS);
        } else {
            ZooAlerts.clear(zoo, ZooAlerts.NO_ANIMALS);
        }
        return census;
    }

    private static Set<UUID> idsOf(List<Mob> mobs) {
        Set<UUID> ids = new HashSet<>();
        mobs.forEach(m -> ids.add(m.getUUID()));
        return ids;
    }

    private static AABB searchBox(Zoo zoo, ServerLevel level) {
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BoundaryVertex v : zoo.boundary()) {
            minY = Math.min(minY, v.y());
            maxY = Math.max(maxY, v.y());
        }
        for (Gate gate : zoo.gates()) {
            minY = Math.min(minY, gate.pos().getY());
            maxY = Math.max(maxY, gate.pos().getY());
        }
        minY = Math.max(level.getMinBuildHeight(), minY - SEARCH_BELOW);
        maxY = Math.min(level.getMaxBuildHeight(), maxY + SEARCH_ABOVE);
        return new AABB(zoo.shape().minX(), minY, zoo.shape().minZ(), zoo.shape().maxX() + 1, maxY, zoo.shape().maxZ() + 1);
    }

    /**
     * Up to six places a visitor could stand to see the exhibit: inside the zoo, within viewing distance of its
     * centre, preferring path blocks. Whether they can actually be reached is learned from real visits.
     */
    static List<BlockPos> viewingSpots(ServerLevel level, Zoo zoo, Vec3 centre) {
        int radius = ZooConfig.VIEWING_RADIUS.get();
        List<BlockPos> candidates = new ArrayList<>();
        for (int i = 0; i < SPOT_SAMPLES; i++) {
            double angle = Math.PI * 2 * i / SPOT_SAMPLES;
            double distance = 2.5 + (radius - 3.0) * ((i % 4) / 3.0);
            int x = (int) Math.floor(centre.x + Math.cos(angle) * distance);
            int z = (int) Math.floor(centre.z + Math.sin(angle) * distance);
            if (!zoo.shape().containsBlock(x, z) || !level.isLoaded(new BlockPos(x, 0, z))) {
                continue;
            }
            Optional<BlockPos> feet = Standing.findInColumn(level, x, (int) Math.floor(centre.y), z, 3);
            if (feet.isPresent() && level.getFluidState(feet.get()).isEmpty() && Math.abs(feet.get().getY() - centre.y) <= 3) {
                candidates.add(feet.get());
            }
        }
        candidates.sort(Comparator.comparingInt((BlockPos p) -> level.getBlockState(p.below()).is(ZooTags.VISITOR_PATHS) ? 0 : 1)
                .thenComparingDouble(p -> -p.distToCenterSqr(centre)));
        return candidates.size() > MAX_SPOTS ? List.copyOf(candidates.subList(0, MAX_SPOTS)) : List.copyOf(candidates);
    }

    /** GameTests only: forgets the zoo's census, as after a restart. */
    public static void forgetForTest(Zoo zoo) {
        CENSUS.remove(zoo.id());
        EXHIBITS.remove(zoo.id());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        CENSUS.clear();
        EXHIBITS.clear();
    }
}
