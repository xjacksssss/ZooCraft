package com.zoocraft.animal;

import com.zoocraft.core.perf.PerfSlot;
import com.zoocraft.perf.SimulationTimers;
import com.zoocraft.ZooCraft;
import com.zoocraft.config.ZooConfig;
import com.zoocraft.core.exhibit.AnimalPoint;
import com.zoocraft.core.exhibit.Enclosure;
import com.zoocraft.core.exhibit.EnclosureFill;
import com.zoocraft.core.exhibit.FloorGrid;
import com.zoocraft.core.exhibit.SpotGrid;
import com.zoocraft.core.exhibit.ViewingSpots;
import com.zoocraft.core.exhibit.ExhibitGrouping;
import com.zoocraft.registry.ZooTags;
import com.zoocraft.zoo.SavedExhibit;
import com.zoocraft.zoo.Standing;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooAlerts;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Counts each open zoo's animals every {@code censusIntervalSeconds} with one entity query over the zoo's full height,
 * groups them into exhibits and caches the result. Visitors only read this cache (spec section 8.3). New exhibits, and
 * exhibits whose animals have left their enclosure, are queued for an enclosure fill; at most
 * {@code enclosureFillsPerTick} fills run per tick (Phase 3 spec section 8.1).
 */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class AnimalCensus {
    /** Animals within this distance of each other (chained) share an exhibit. */
    public static final double LINK_DISTANCE = 8.0;
    private static final int MAX_ANIMALS = 512;
    private static final int SPOT_SAMPLES = 16;

    private static final Map<UUID, ZooCensus> CENSUS = new HashMap<>();
    private static final Map<UUID, List<Exhibit>> EXHIBITS = new HashMap<>();

    private record FillJob(UUID zooId, UUID exhibitId) {
    }

    private static final LinkedHashSet<FillJob> PENDING = new LinkedHashSet<>();

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
        long timer = SimulationTimers.start();
        try {
            return doRefresh(zoo, level);
        } finally {
            SimulationTimers.stop(PerfSlot.ANIMAL_CENSUS, zoo.id(), timer);
        }
    }

    private static ZooCensus doRefresh(Zoo zoo, ServerLevel level) {
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
            previous = saved.stream().map(s -> Exhibit.restored(s.id(), s.views(), s.viewsToday(), s.day())).toList();
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
            exhibit.update(members, species, centre);
            if (!exhibit.enclosure().bounded()) {
                exhibit.sampledSpots(sampledSpots(level, zoo, centre, members.stream().map(Mob::position).toList()));
            }
            exhibit.rate(level.getGameTime());
            if (exhibit.needsFill(level.getGameTime())) {
                PENDING.add(new FillJob(zoo.id(), exhibit.id()));
            }
            exhibits.add(exhibit);
        }
        EXHIBITS.put(zoo.id(), exhibits);
        ZooManager.get(level.getServer()).recordExhibits(zoo, exhibits.stream()
                .map(e -> new SavedExhibit(e.id(), e.animals().stream().map(Mob::getUUID).toList(), e.viewCount(), e.savedViewsToday(),
                        e.savedDay())).toList());

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

    /** The zoo's columns from the bottom to the top of the world: animals count at any height (Phase 3 spec section 8.2). */
    private static AABB searchBox(Zoo zoo, ServerLevel level) {
        return new AABB(zoo.shape().minX(), level.getMinBuildHeight(), zoo.shape().minZ(), zoo.shape().maxX() + 1,
                level.getMaxBuildHeight(), zoo.shape().maxZ() + 1);
    }

    /** Runs queued enclosure fills, at most {@code max} of them, each timed in the enclosure slot. */
    public static void runPendingFills(MinecraftServer server, int max) {
        Iterator<FillJob> it = PENDING.iterator();
        for (int done = 0; done < max && it.hasNext(); done++) {
            FillJob job = it.next();
            it.remove();
            Optional<Zoo> zoo = ZooManager.get(server).byId(job.zooId());
            List<Exhibit> exhibits = EXHIBITS.get(job.zooId());
            ServerLevel level = zoo.map(z -> server.getLevel(z.dimension())).orElse(null);
            if (level == null || exhibits == null) {
                continue;
            }
            for (Exhibit exhibit : exhibits) {
                if (exhibit.id().equals(job.exhibitId())) {
                    long timer = SimulationTimers.start();
                    try {
                        fill(level, zoo.get(), exhibit);
                    } finally {
                        SimulationTimers.stop(PerfSlot.ENCLOSURE, job.zooId(), timer);
                    }
                }
            }
        }
    }

    /** Whether (x, z) is on the floor of one of the zoo's bounded enclosures: somewhere visitors can't walk to. */
    public static boolean insidePen(Zoo zoo, int x, int z) {
        List<Exhibit> exhibits = EXHIBITS.get(zoo.id());
        if (exhibits != null) {
            for (Exhibit exhibit : exhibits) {
                if (exhibit.enclosure().bounded() && exhibit.enclosure().contains(x, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int pendingFills() {
        return PENDING.size();
    }

    @SubscribeEvent
    static void onFillTick(ServerTickEvent.Post event) {
        if (!PENDING.isEmpty()) {
            runPendingFills(event.getServer(), ZooConfig.ENCLOSURE_FILLS_PER_TICK.get());
        }
    }

    /** Finds the exhibit's enclosure and, when it is bounded, viewing spots just outside its edge (spec 8.1). */
    private static void fill(ServerLevel level, Zoo zoo, Exhibit exhibit) {
        List<int[]> starts = new ArrayList<>();
        for (Mob animal : exhibit.animals()) {
            if (animal.isAlive()) {
                starts.add(new int[]{animal.getBlockX(), animal.getBlockY(), animal.getBlockZ()});
            }
        }
        Enclosure enclosure = EnclosureFill.fill(new LevelFloorGrid(level), starts, ZooConfig.MAX_ENCLOSURE_AREA.get());
        List<BlockPos> spots = new ArrayList<>();
        if (enclosure.bounded()) {
            for (int[] s : ViewingSpots.pick(enclosure, spotGrid(level, zoo), ZooConfig.MAX_VIEWING_SPOTS.get())) {
                spots.add(new BlockPos(s[0], s[1], s[2]));
            }
        }
        exhibit.filled(enclosure, surroundings(level, enclosure), spots, level.getGameTime());
        exhibit.rate(level.getGameTime());
    }

    /** Where visitors can stand: inside the zoo, out of fluids; path blocks underfoot count as paths. */
    private static SpotGrid spotGrid(ServerLevel level, Zoo zoo) {
        return new SpotGrid() {
            @Override
            public int standY(int x, int z, int nearY) {
                BlockPos near = new BlockPos(x, nearY, z);
                if (!zoo.shape().containsBlock(x, z) || !level.isLoaded(near)) {
                    return FloorGrid.NO_FLOOR;
                }
                return Standing.findStandable(level, near, 2).filter(p -> level.getFluidState(p).isEmpty())
                        .map(BlockPos::getY).orElse(FloorGrid.NO_FLOOR);
            }

            @Override
            public boolean path(int x, int y, int z) {
                return level.getBlockState(new BlockPos(x, y - 1, z)).is(ZooTags.VISITOR_PATHS);
            }
        };
    }

    /** Natural floor blocks under the enclosure, and whether there is water or leaves in or beside it. */
    private static Exhibit.Surroundings surroundings(ServerLevel level, Enclosure enclosure) {
        int natural = 0;
        boolean waterOrLeaves = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Map.Entry<Long, Integer> cell : enclosure.floor().entrySet()) {
            int x = Enclosure.x(cell.getKey()), z = Enclosure.z(cell.getKey()), y = cell.getValue();
            if (level.getBlockState(pos.set(x, y - 1, z)).is(ZooTags.NATURAL_FLOOR)) {
                natural++;
            }
            if (!waterOrLeaves) {
                waterOrLeaves = waterOrLeavesAt(level, pos, x, y, z) || waterOrLeavesAt(level, pos, x + 1, y, z)
                        || waterOrLeavesAt(level, pos, x - 1, y, z) || waterOrLeavesAt(level, pos, x, y, z + 1)
                        || waterOrLeavesAt(level, pos, x, y, z - 1);
            }
        }
        return new Exhibit.Surroundings(natural, waterOrLeaves);
    }

    private static boolean waterOrLeavesAt(ServerLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        for (int dy = -1; dy <= 0; dy++) {
            BlockState state = level.getBlockState(pos.set(x, y + dy, z));
            if (state.is(BlockTags.LEAVES) || state.getFluidState().is(Fluids.WATER) || state.getFluidState().is(Fluids.FLOWING_WATER)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Places a visitor could stand to see an exhibit without a bounded enclosure (Phase 1 spec section 8.3): inside the
     * zoo, sampled round each animal in turn, preferring path blocks. Whether they can be reached is learned from visits.
     */
    static List<BlockPos> sampledSpots(ServerLevel level, Zoo zoo, Vec3 centre, List<Vec3> around) {
        int radius = ZooConfig.VIEWING_RADIUS.get();
        List<BlockPos> candidates = new ArrayList<>();
        for (int i = 0; i < SPOT_SAMPLES && !around.isEmpty(); i++) {
            Vec3 animal = around.get(i % around.size());
            double angle = Math.PI * 2 * i / SPOT_SAMPLES;
            double distance = 2.5 + (radius - 3.0) * ((i % 4) / 3.0);
            int x = (int) Math.floor(animal.x + Math.cos(angle) * distance);
            int z = (int) Math.floor(animal.z + Math.sin(angle) * distance);
            if (!zoo.shape().containsBlock(x, z) || !level.isLoaded(new BlockPos(x, 0, z)) || insidePen(zoo, x, z)) {
                continue;
            }
            Optional<BlockPos> feet = Standing.findInColumn(level, x, (int) Math.floor(centre.y), z, 3);
            if (feet.isPresent() && level.getFluidState(feet.get()).isEmpty() && Math.abs(feet.get().getY() - centre.y) <= 3
                    && !candidates.contains(feet.get())) {
                candidates.add(feet.get());
            }
        }
        int max = ZooConfig.MAX_VIEWING_SPOTS.get();
        candidates.sort(Comparator.comparingInt((BlockPos p) -> level.getBlockState(p.below()).is(ZooTags.VISITOR_PATHS) ? 0 : 1)
                .thenComparingDouble(p -> -p.distToCenterSqr(centre)));
        return candidates.size() > max ? List.copyOf(candidates.subList(0, max)) : List.copyOf(candidates);
    }

    /** GameTests only: forgets the zoo's census, as after a restart. */
    public static void forgetForTest(Zoo zoo) {
        CENSUS.remove(zoo.id());
        EXHIBITS.remove(zoo.id());
        PENDING.removeIf(job -> job.zooId().equals(zoo.id()));
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        CENSUS.clear();
        EXHIBITS.clear();
        PENDING.clear();
    }
}
