package com.zoocraft.animal;

import com.zoocraft.config.ZooConfig;
import com.zoocraft.core.crowd.Occupancy;
import com.zoocraft.core.exhibit.Enclosure;
import com.zoocraft.core.exhibit.ExhibitRating;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A group of nearby animals visitors can go and see. Detected automatically by the census; its identity, view counts,
 * enclosure and spot history carry over between censuses while its animals stay together. Visitors claim places at
 * its viewing spots (Phase 3 spec section 7.2).
 */
public final class Exhibit {
    /** An open exhibit's animals outside the area searched last time are searched again at most this often. */
    static final long OPEN_REFILL_TICKS = 60 * 20;
    /** A spot claim not renewed for this long is free again. */
    public static final long CLAIM_EXPIRY_TICKS = 10 * 20;
    public static final long DAY_TICKS = 24000L;

    /** What the exhibit's floor is like, for its rating (Phase 3 spec section 8.5). */
    public record Surroundings(int natural, boolean waterOrLeaves) {
    }

    private final UUID id;
    private List<Mob> animals = List.of();
    private Set<String> species = Set.of();
    private Vec3 centre = Vec3.ZERO;
    private List<BlockPos> viewingSpots = List.of();
    private long lastReached = -1;
    private int viewCount;
    private Enclosure enclosure = Enclosure.NONE;
    private Surroundings surroundings = new Surroundings(0, false);
    private long lastFill = Long.MIN_VALUE / 2;
    private boolean filled;
    /** The pens among the exhibit's regions (all of them when the exhibit is bounded): visitors can't stand inside. */
    private List<Enclosure> pens = List.of();
    private ExhibitRating.Result rating = ExhibitRating.Result.UNRATED;
    /** Animals wider than this count double for space (Phase 3 spec section 8.5). */
    private static final double LARGE_ANIMAL_WIDTH = 1.5;

    private final Occupancy<BlockPos> claims = new Occupancy<>(CLAIM_EXPIRY_TICKS);
    private final Map<BlockPos, Long> spotUnreachableUntil = new HashMap<>();
    private final Map<BlockPos, Long> spotReachedAt = new HashMap<>();
    // Today's counts (game days), for route choice and the rating.
    private long day = -1;
    private int viewsToday;
    private int attemptsToday;

    Exhibit(UUID id) {
        this.id = id;
    }

    /** An exhibit remembered from before a restart, with its view counts. */
    static Exhibit restored(UUID id, int views, int viewsToday, long day) {
        Exhibit exhibit = new Exhibit(id);
        exhibit.viewCount = views;
        exhibit.viewsToday = viewsToday;
        exhibit.day = day;
        return exhibit;
    }

    void update(List<Mob> animals, Set<String> species, Vec3 centre) {
        this.animals = List.copyOf(animals);
        this.species = Set.copyOf(species);
        this.centre = centre;
    }

    /** Spots sampled around the animals, used until a fill finds a bounded enclosure (and always for open exhibits). */
    void sampledSpots(List<BlockPos> spots) {
        this.viewingSpots = List.copyOf(spots);
    }

    /** A fill finished: a bounded enclosure brings its edge spots; an open one keeps the sampled spots. */
    void filled(Enclosure enclosure, List<Enclosure> pens, Surroundings surroundings, List<BlockPos> edgeSpots, long now) {
        this.enclosure = enclosure;
        this.pens = List.copyOf(pens);
        this.surroundings = surroundings;
        this.lastFill = now;
        this.filled = true;
        if (enclosure.bounded()) {
            this.viewingSpots = List.copyOf(edgeSpots);
        }
    }

    /**
     * New, or an animal has left its bounded enclosure (the pen changed), or an open exhibit's animals have wandered
     * beyond the area searched last time and the last search was a while ago.
     */
    boolean needsFill(long now) {
        if (!filled) {
            return true;
        }
        boolean outside = animals.stream().anyMatch(a -> !enclosure.contains(a.getBlockX(), a.getBlockZ()));
        return outside && (enclosure.bounded() || now - lastFill > OPEN_REFILL_TICKS);
    }

    public UUID id() {
        return id;
    }

    public List<Mob> animals() {
        return animals;
    }

    public Set<String> species() {
        return species;
    }

    public Vec3 centre() {
        return centre;
    }

    public List<BlockPos> viewingSpots() {
        return viewingSpots;
    }

    public Enclosure enclosure() {
        return enclosure;
    }

    public Surroundings surroundings() {
        return surroundings;
    }

    /** Whether (x, z) is inside one of this exhibit's pens. */
    public boolean insidePen(int x, int z) {
        for (Enclosure pen : pens) {
            if (pen.contains(x, z)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ spot claims (spec 7.2)

    public Occupancy<BlockPos> claims() {
        return claims;
    }

    public int claimCount(BlockPos spot, long now) {
        return claims.count(spot, now);
    }

    /** Every spot holds {@code viewingSpotCapacity} visitors already. */
    public boolean full(long now) {
        if (viewingSpots.isEmpty()) {
            return false;
        }
        int capacity = ZooConfig.VIEWING_SPOT_CAPACITY.get();
        for (BlockPos spot : viewingSpots) {
            if (claims.count(spot, now) < capacity) {
                return false;
            }
        }
        return true;
    }

    /** Share of the places at this exhibit's spots that are taken, from 0 to 1. */
    public double occupiedShare(long now) {
        if (viewingSpots.isEmpty()) {
            return 0;
        }
        int capacity = ZooConfig.VIEWING_SPOT_CAPACITY.get();
        int taken = 0;
        for (BlockPos spot : viewingSpots) {
            taken += Math.min(capacity, claims.count(spot, now));
        }
        return (double) taken / (viewingSpots.size() * capacity);
    }

    // ------------------------------------------------------------------ reachability

    /** A visitor couldn't get to this spot: skip it for a while. */
    public void markSpotUnreachable(BlockPos spot, long untilGameTime) {
        spotUnreachableUntil.put(spot.immutable(), untilGameTime);
    }

    public boolean spotUnreachable(BlockPos spot, long now) {
        Long until = spotUnreachableUntil.get(spot);
        return until != null && until > now;
    }

    /** Every spot is marked unreachable. */
    public boolean isUnreachable(long now) {
        if (viewingSpots.isEmpty()) {
            return false;
        }
        for (BlockPos spot : viewingSpots) {
            if (!spotUnreachable(spot, now)) {
                return false;
            }
        }
        return true;
    }

    /** A visitor arrived at {@code spot}. */
    public void markReached(BlockPos spot, long now) {
        lastReached = now;
        spotUnreachableUntil.remove(spot);
        spotReachedAt.put(spot.immutable(), now);
    }

    public long lastReached() {
        return lastReached;
    }

    /** How many of the current spots a visitor has reached since {@code since}. */
    public int spotsReachedSince(long since) {
        int reached = 0;
        for (BlockPos spot : viewingSpots) {
            Long at = spotReachedAt.get(spot);
            if (at != null && at >= since) {
                reached++;
            }
        }
        return reached;
    }

    /**
     * Visitors can't get close enough: no viewing spots at all, every spot has recently failed, or visitors reach the
     * spots but don't see the animals from them.
     */
    public boolean viewingProblem(long now) {
        return viewingSpots.isEmpty() || isUnreachable(now) || rating.warnings().contains(ExhibitRating.Warning.CANT_SEE);
    }

    // ------------------------------------------------------------------ rating (spec 8.5)

    public ExhibitRating.Result rating() {
        return rating;
    }

    /** Rates the exhibit from its enclosure and the last day of visits. Called with every census. */
    void rate(long now) {
        double animalWeight = 0;
        for (Mob animal : animals) {
            animalWeight += animal.getBbWidth() > LARGE_ANIMAL_WIDTH ? 2 : 1;
        }
        int area = enclosure.area();
        // Before the floor has been searched, the surroundings are unknown: no penalty.
        double natural = area == 0 ? 1.0 : (double) surroundings.natural() / area;
        rating = ExhibitRating.rate(new ExhibitRating.Input(viewingSpots.size(), spotsReachedSince(now - DAY_TICKS), viewsToday(now) > 0,
                attemptsToday(now), species.size(), !enclosure.bounded(), area, animalWeight, natural, surroundings.waterOrLeaves()));
    }

    // ------------------------------------------------------------------ views

    public int viewCount() {
        return viewCount;
    }

    public void addView(long now) {
        roll(now);
        viewCount++;
        viewsToday++;
    }

    /** A visitor set off for this exhibit. */
    public void addAttempt(long now) {
        roll(now);
        attemptsToday++;
    }

    public int viewsToday(long now) {
        roll(now);
        return viewsToday;
    }

    public int attemptsToday(long now) {
        roll(now);
        return attemptsToday;
    }

    /** Today's views and game day as they stand, for saving. */
    int savedViewsToday() {
        return viewsToday;
    }

    long savedDay() {
        return day;
    }

    private void roll(long now) {
        long today = now / DAY_TICKS;
        if (today != day) {
            day = today;
            viewsToday = 0;
            attemptsToday = 0;
        }
    }
}
