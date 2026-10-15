package com.zoocraft.visitor;

import com.zoocraft.core.perf.PerfSlot;
import com.zoocraft.perf.SimulationTimers;
import com.zoocraft.animal.AnimalCensus;
import com.zoocraft.animal.Exhibit;
import com.zoocraft.animal.Viewing;
import com.zoocraft.animal.ZooCensus;
import com.zoocraft.config.ZooConfig;
import com.zoocraft.core.choice.ExhibitChoice;
import com.zoocraft.core.choice.ExhibitOption;
import com.zoocraft.core.choice.SpotChoice;
import com.zoocraft.registry.ZooTags;
import com.zoocraft.core.visitor.StepRates;
import com.zoocraft.core.visitor.VisitorProfile;
import com.zoocraft.feedback.CommentType;
import com.zoocraft.zoo.Standing;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import com.zoocraft.core.litter.Cleanliness;
import com.zoocraft.litter.LitterEntity;
import com.zoocraft.litter.LitterTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Where admitted visitors go and what they see (spec sections 8.4 to 8.6). Reads only the cached census. Visitors
 * claim a place at the least crowded viewing spot and say so when an exhibit is too crowded (Phase 3 spec 7.2).
 */
final class VisitorExploration {
    private static final int WANDER_RADIUS = 20;
    private static final int ATTEMPTS = 10;
    private static final int RECENT_MEMORY = 3;
    private static final int MAX_ANIMALS_CHECKED = 8;
    private static final int MANY_SPECIES = 5;
    private static final int REPEATS_BEFORE_COMPLAINT = 3;
    private static final double INTEREST_FOR_NEW_SPECIES = 15;
    private static final long UNREACHABLE_TICKS = 60 * 20;
    /** How long a visitor waits at a full exhibit for a free spot before moving on. */
    static final long CROWD_WAIT_TICKS = 5 * 20;
    private static final long CROWD_RETRY_TICKS = 20;

    /**
     * What a visitor does on reaching its spot: linger for {@code linger} ticks, or walk on to {@code retarget}; with
     * {@code keepTarget} it stays aiming at the same spot (waiting for room).
     */
    record Arrival(long linger, @Nullable BlockPos retarget, boolean keepTarget) {
    }

    private VisitorExploration() {
    }

    static Optional<BlockPos> chooseTarget(Visitor visitor, Zoo zoo, ServerLevel level) {
        Optional<Visitor> leader = leaderOf(visitor);
        releaseSpot(visitor);
        visitor.crowdWaitSince = 0;
        if (leader.isPresent()) {
            Optional<BlockPos> near = randomSpot(visitor, zoo, level, leader.get().blockPosition(), 3);
            if (near.isPresent()) {
                visitor.currentExhibit = null; // following the leader, who holds the spot
                return near;
            }
        }
        ZooCensus census = AnimalCensus.of(zoo, level);
        if (census.animals().isEmpty()) {
            visitor.remarkOnce(CommentType.NOTHING_TO_SEE);
            visitor.changeInterest(-20);
            visitor.currentExhibit = null;
            return randomSpot(visitor, zoo, level, visitor.blockPosition(), WANDER_RADIUS);
        }
        long now = level.getGameTime();
        List<Exhibit> usable = census.exhibits().stream()
                .filter(e -> !e.isUnreachable(now) && !e.viewingSpots().isEmpty()).toList();
        if (!usable.isEmpty()) {
            // Phase 3 spec section 8.3: distance against the zoo's size, today's popularity and crowding.
            int[] views = new int[usable.size()];
            for (int i = 0; i < views.length; i++) {
                views[i] = usable.get(i).viewsToday(now);
            }
            int[] sorted = views.clone();
            Arrays.sort(sorted);
            int median = sorted[sorted.length / 2];
            double scale = ExhibitChoice.routeScale(zoo.shape().maxX() - zoo.shape().minX(), zoo.shape().maxZ() - zoo.shape().minZ());
            List<ExhibitOption> options = new ArrayList<>();
            for (int i = 0; i < usable.size(); i++) {
                Exhibit exhibit = usable.get(i);
                double distance = Math.sqrt(exhibit.centre().distanceToSqr(visitor.position()));
                options.add(new ExhibitOption(exhibit.species(), distance, visitor.recentExhibits.contains(exhibit.id()), scale, views[i],
                        median, exhibit.occupiedShare(now), exhibit.full(now)));
            }
            int index = ExhibitChoice.pick(options, visitor.seenSpecies(), visitor.profile() == VisitorProfile.ANIMAL_ENTHUSIAST,
                    visitor.getRandom().nextDouble());
            Exhibit chosen = usable.get(index);
            chosen.addAttempt(now);
            visitor.currentExhibit = chosen.id();
            BlockPos spot = takeSpot(visitor, level, chosen, now, false);
            if (spot != null) {
                return Optional.of(spot);
            }
        }
        visitor.currentExhibit = null;
        return randomSpot(visitor, zoo, level, visitor.blockPosition(), WANDER_RADIUS);
    }

    static Optional<BlockPos> randomSpot(Visitor visitor, Zoo zoo, ServerLevel level, BlockPos around, int radius) {
        for (int i = 0; i < ATTEMPTS; i++) {
            int x = around.getX() + visitor.getRandom().nextInt(radius * 2 + 1) - radius;
            int z = around.getZ() + visitor.getRandom().nextInt(radius * 2 + 1) - radius;
            // Never inside a pen: visitors can't get in, and each try costs a path search.
            if (!zoo.shape().containsBlock(x, z) || !level.isLoaded(new BlockPos(x, 0, z)) || AnimalCensus.insidePen(zoo, x, z)) {
                continue;
            }
            Optional<BlockPos> feet = Standing.findInColumn(level, x, around.getY(), z, 4);
            if (feet.isPresent() && VisitorController.goodSpot(level, zoo, feet.get())) {
                return feet;
            }
        }
        return Optional.empty();
    }

    /**
     * The least occupied reachable spot of the exhibit (spec 7.2), claimed when there is room. When every spot is full
     * the least crowded is still returned, unclaimed, so the visitor can go and wait there; with {@code freeOnly} it
     * returns null instead.
     */
    static @Nullable BlockPos takeSpot(Visitor visitor, ServerLevel level, Exhibit exhibit, long now, boolean freeOnly) {
        int capacity = ZooConfig.VIEWING_SPOT_CAPACITY.get();
        List<BlockPos> spots = exhibit.viewingSpots();
        List<SpotChoice.Spot> options = new ArrayList<>(spots.size());
        for (BlockPos spot : spots) {
            options.add(new SpotChoice.Spot(exhibit.claimCount(spot, now), capacity, level.getBlockState(spot.below()).is(ZooTags.VISITOR_PATHS),
                    exhibit.spotUnreachable(spot, now), Math.sqrt(spot.distSqr(visitor.blockPosition()))));
        }
        int index = SpotChoice.pick(options);
        if (index < 0) {
            return null;
        }
        BlockPos spot = spots.get(index);
        if (exhibit.claims().claim(spot, visitor.getUUID(), now, capacity)) {
            visitor.claimedSpot = spot;
            visitor.spotExhibit = exhibit;
            return spot;
        }
        return freeOnly ? null : spot;
    }

    /** Every exploring step: keeps the visitor's place at its spot while it walks there and lingers. */
    static void renewSpot(Visitor visitor, long now) {
        if (visitor.claimedSpot != null && visitor.spotExhibit != null) {
            visitor.spotExhibit.claims().claim(visitor.claimedSpot, visitor.getUUID(), now, Integer.MAX_VALUE);
        }
    }

    /** Frees the visitor's place at its viewing spot, if it holds one. */
    static void releaseSpot(Visitor visitor) {
        if (visitor.claimedSpot != null && visitor.spotExhibit != null) {
            visitor.spotExhibit.claims().release(visitor.claimedSpot, visitor.getUUID());
        }
        visitor.claimedSpot = null;
        visitor.spotExhibit = null;
    }

    /** The visitor's group leader, if it has one that is still exploring. */
    static Optional<Visitor> leaderOf(Visitor visitor) {
        if (visitor.isLeader() || visitor.groupId() == null || visitor.zooId() == null) {
            return Optional.empty();
        }
        for (Visitor other : VisitorTracker.visitors(visitor.zooId())) {
            if (other != visitor && other.isLeader() && visitor.groupId().equals(other.groupId())
                    && other.phase() == VisitorPhase.EXPLORING && !other.isRemoved()) {
                return Optional.of(other);
            }
        }
        return Optional.empty();
    }

    /** Every step while exploring: look at nearby animals (about once a second). */
    static void observe(Visitor visitor, Zoo zoo, long now) {
        if (!(visitor.level() instanceof ServerLevel level)
                || !StepRates.due(now, visitor.lastViewingCheck, ZooConfig.VIEWING_INTERVAL_TICKS.get())) {
            return;
        }
        visitor.lastViewingCheck = now;
        ZooCensus census = AnimalCensus.cached(zoo);
        if (census.animals().isEmpty()) {
            return;
        }
        int radius = ZooConfig.VIEWING_RADIUS.get();
        double reachSq = (radius + 1.0) * (radius + 1.0);
        Vec3 feet = visitor.position();
        List<Mob> near = new ArrayList<>();
        for (Mob animal : census.animals()) {
            if (animal.isAlive() && animal.distanceToSqr(feet) <= reachSq + Viewing.MAX_VERTICAL * Viewing.MAX_VERTICAL) {
                near.add(animal);
            }
        }
        near.sort(Comparator.comparingDouble(a -> a.distanceToSqr(feet)));
        Vec3 eye = visitor.getEyePosition();
        for (int i = 0; i < Math.min(MAX_ANIMALS_CHECKED, near.size()); i++) {
            Mob animal = near.get(i);
            if (!Viewing.canSee(level, eye, feet, animal, radius)) {
                continue;
            }
            census.exhibitContaining(animal).ifPresent(exhibit -> {
                if (visitor.exhibitsViewed.add(exhibit.id())) {
                    exhibit.addView(now);
                    // A well-kept exhibit pleases a little, a poor one disappoints a little (spec 8.5).
                    switch (exhibit.rating().grade()) {
                        case EXCELLENT -> visitor.changeSatisfaction(1);
                        case POOR -> visitor.changeSatisfaction(-1);
                        default -> {
                        }
                    }
                }
            });
            String species = census.speciesOf(animal);
            if (!species.isEmpty() && visitor.seenSpecies.add(species)) {
                visitor.changeInterest(INTEREST_FOR_NEW_SPECIES);
                visitor.remark(CommentType.AMAZING_EXHIBIT);
                if (visitor.seenSpecies.size() >= MANY_SPECIES) {
                    visitor.remarkOnce(CommentType.MANY_ANIMALS);
                }
                visitor.getLookControl().setLookAt(animal);
            }
        }
    }

    private static final double LITTER_SIGHT = 8;
    private static final int LITTER_UNITS_THAT_BOTHER = 3;
    private static final int SIGHTINGS_BEFORE_COMPLAINT = 3;
    /** Phase 2 balance: -2 satisfaction and one sighting per 2 seconds near litter. */
    private static final double LITTER_LOSS_PER_SECOND = 1.0;
    private static final double SECONDS_PER_SIGHTING = 2.0;

    /**
     * Every 2 seconds while exploring: counts the litter around the visitor, feeds the zoo's cleanliness and, when
     * there is a lot of it, spoils the visit a little (Phase 2 spec section 9.4).
     */
    static void noticeLitter(Visitor visitor, Zoo zoo, long now) {
        int interval = ZooConfig.LITTER_CHECK_INTERVAL_TICKS.get();
        if (visitor.getServer() == null || !StepRates.due(now, visitor.lastLitterCheck, interval)) {
            return;
        }
        // Effects scale with the time covered, so the check interval and visitor step change cost, not balance.
        double seconds = StepRates.elapsedSeconds(now, visitor.lastLitterCheck, interval);
        visitor.lastLitterCheck = now;
        long timer = SimulationTimers.start();
        try {
            int units = 0;
            for (LitterEntity litter : LitterTracker.litterIn(zoo)) {
                if (litter.distanceToSqr(visitor) <= LITTER_SIGHT * LITTER_SIGHT) {
                    units += litter.pile();
                }
            }
            ZooManager.get(visitor.getServer()).recordCleanlinessSample(zoo, Cleanliness.sample(units));
            if (units >= LITTER_UNITS_THAT_BOTHER) {
                visitor.changeSatisfaction(-LITTER_LOSS_PER_SECOND * seconds);
                visitor.litterExposure += seconds;
                while (visitor.litterExposure >= SECONDS_PER_SIGHTING) {
                    visitor.litterExposure -= SECONDS_PER_SIGHTING;
                    visitor.litterSeen++;
                }
                if (visitor.litterSeen >= SIGHTINGS_BEFORE_COMPLAINT) {
                    int cellX = Math.floorDiv(visitor.getBlockX(), 16) * 16 + 8;
                    int cellZ = Math.floorDiv(visitor.getBlockZ(), 16) * 16 + 8;
                    visitor.remarkOnce(CommentType.RUBBISH_EVERYWHERE, cellX + "," + cellZ);
                }
            }
        } finally {
            SimulationTimers.stop(PerfSlot.LITTER, zoo.id(), timer);
        }
    }

    /** The visitor reached the spot it chose. */
    static Arrival onArrived(Visitor visitor, Zoo zoo, long now) {
        long linger = 100 + visitor.getRandom().nextInt(200);
        if (visitor.currentExhibit == null || !(visitor.level() instanceof ServerLevel level)) {
            return new Arrival(linger, null, false);
        }
        Optional<Exhibit> current = AnimalCensus.cached(zoo).exhibit(visitor.currentExhibit);
        if (current.isPresent() && visitor.claimedSpot == null) {
            // Every spot was full when it set off: take one that has come free, or wait a little, then move on.
            BlockPos free = takeSpot(visitor, level, current.get(), now, true);
            if (free != null) {
                visitor.crowdWaitSince = 0;
                return new Arrival(0, free, false);
            }
            if (visitor.crowdWaitSince == 0) {
                visitor.crowdWaitSince = now;
            }
            if (now - visitor.crowdWaitSince < CROWD_WAIT_TICKS) {
                return new Arrival(CROWD_RETRY_TICKS, null, true);
            }
            visitor.crowdWaitSince = 0;
            visitor.remarkOnce(CommentType.CROWDED, current.get().id().toString());
            visitor.recentExhibits.addFirst(visitor.currentExhibit);
            while (visitor.recentExhibits.size() > RECENT_MEMORY) {
                visitor.recentExhibits.removeLast();
            }
            visitor.currentExhibit = null;
            return new Arrival(0, null, false);
        }
        BlockPos spot = visitor.claimedSpot;
        current.ifPresent(exhibit -> {
            exhibit.markReached(spot, now);
            // Only coming back to an exhibit already visited counts as seeing the same animals again.
            if (!visitor.exhibitsArrived.add(exhibit.id())) {
                visitor.repeatVisits++;
                if (visitor.repeatVisits >= REPEATS_BEFORE_COMPLAINT) {
                    visitor.remarkOnce(CommentType.SAME_ANIMALS);
                    visitor.changeInterest(-10);
                }
            }
        });
        visitor.recentExhibits.addFirst(visitor.currentExhibit);
        while (visitor.recentExhibits.size() > RECENT_MEMORY) {
            visitor.recentExhibits.removeLast();
        }
        // Look at the exhibit's animals while lingering.
        AnimalCensus.cached(zoo).exhibit(visitor.currentExhibit).flatMap(e -> e.animals().stream().filter(Mob::isAlive).findFirst())
                .ifPresent(animal -> visitor.getLookControl().setLookAt(animal));
        return new Arrival(linger, null, false);
    }

    /** The visitor could not reach the spot it chose: that spot is skipped for a while (the exhibit once all are). */
    static void onUnreachable(Visitor visitor, Zoo zoo, BlockPos spot) {
        releaseSpot(visitor);
        if (visitor.currentExhibit == null) {
            return;
        }
        long now = visitor.level().getGameTime();
        AnimalCensus.cached(zoo).exhibit(visitor.currentExhibit).ifPresent(e -> e.markSpotUnreachable(spot, now + UNREACHABLE_TICKS));
        visitor.remark(CommentType.CANT_REACH_EXHIBIT);
        visitor.currentExhibit = null;
    }
}
