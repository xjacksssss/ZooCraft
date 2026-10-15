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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Where admitted visitors go and what they see (spec sections 8.4 to 8.6). Reads only the cached census.
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

    private VisitorExploration() {
    }

    static Optional<BlockPos> chooseTarget(Visitor visitor, Zoo zoo, ServerLevel level) {
        Optional<Visitor> leader = leaderOf(visitor);
        if (leader.isPresent()) {
            Optional<BlockPos> near = randomSpot(visitor, zoo, level, leader.get().blockPosition(), 3);
            if (near.isPresent()) {
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
            List<ExhibitOption> options = new ArrayList<>();
            for (Exhibit exhibit : usable) {
                double distance = Math.sqrt(exhibit.centre().distanceToSqr(visitor.position()));
                options.add(new ExhibitOption(exhibit.species(), distance, visitor.recentExhibits.contains(exhibit.id())));
            }
            int index = ExhibitChoice.pick(options, visitor.seenSpecies(), visitor.profile() == VisitorProfile.ANIMAL_ENTHUSIAST,
                    visitor.getRandom().nextDouble());
            Exhibit chosen = usable.get(index);
            visitor.currentExhibit = chosen.id();
            List<BlockPos> spots = chosen.viewingSpots();
            // Spots are sorted best first; favour the first few but spread visitors out a little.
            return Optional.of(spots.get(visitor.getRandom().nextInt(Math.min(3, spots.size()))));
        }
        visitor.currentExhibit = null;
        return randomSpot(visitor, zoo, level, visitor.blockPosition(), WANDER_RADIUS);
    }

    static Optional<BlockPos> randomSpot(Visitor visitor, Zoo zoo, ServerLevel level, BlockPos around, int radius) {
        for (int i = 0; i < ATTEMPTS; i++) {
            int x = around.getX() + visitor.getRandom().nextInt(radius * 2 + 1) - radius;
            int z = around.getZ() + visitor.getRandom().nextInt(radius * 2 + 1) - radius;
            if (!zoo.shape().containsBlock(x, z) || !level.isLoaded(new BlockPos(x, 0, z))) {
                continue;
            }
            Optional<BlockPos> feet = Standing.findInColumn(level, x, around.getY(), z, 4);
            if (feet.isPresent() && VisitorController.goodSpot(level, zoo, feet.get())) {
                return feet;
            }
        }
        return Optional.empty();
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
                    exhibit.addView();
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
    static void onArrived(Visitor visitor, Zoo zoo, long now) {
        if (visitor.currentExhibit == null) {
            return;
        }
        AnimalCensus.cached(zoo).exhibit(visitor.currentExhibit).ifPresent(exhibit -> {
            exhibit.markReached(now);
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
    }

    /** The visitor could not reach the spot it chose. */
    static void onUnreachable(Visitor visitor, Zoo zoo) {
        if (visitor.currentExhibit == null) {
            return;
        }
        long now = visitor.level().getGameTime();
        AnimalCensus.cached(zoo).exhibit(visitor.currentExhibit).ifPresent(e -> e.markUnreachable(now + UNREACHABLE_TICKS));
        visitor.remark(CommentType.CANT_REACH_EXHIBIT);
        visitor.currentExhibit = null;
    }
}
