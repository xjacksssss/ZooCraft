package com.zoocraft.animal;

import com.zoocraft.core.exhibit.Enclosure;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A group of nearby animals visitors can go and see. Detected automatically by the census; its identity,
 * view count and reachability carry over between censuses while its animals stay together.
 */
public final class Exhibit {
    /** An open exhibit's animals outside the area searched last time are searched again at most this often. */
    static final long OPEN_REFILL_TICKS = 60 * 20;

    /** What the exhibit's floor is like, for its rating (Phase 3 spec section 8.5). */
    public record Surroundings(int natural, boolean waterOrLeaves) {
    }

    private final UUID id;
    private List<Mob> animals = List.of();
    private Set<String> species = Set.of();
    private Vec3 centre = Vec3.ZERO;
    private List<BlockPos> viewingSpots = List.of();
    private long unreachableUntil;
    private long lastReached = -1;
    private int viewCount;
    private Enclosure enclosure = Enclosure.NONE;
    private Surroundings surroundings = new Surroundings(0, false);
    private long lastFill = Long.MIN_VALUE / 2;
    private boolean filled;

    Exhibit(UUID id) {
        this.id = id;
    }

    /** An exhibit remembered from before a restart, with its view count. */
    static Exhibit restored(UUID id, int views) {
        Exhibit exhibit = new Exhibit(id);
        exhibit.viewCount = views;
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
    void filled(Enclosure enclosure, Surroundings surroundings, List<BlockPos> edgeSpots, long now) {
        this.enclosure = enclosure;
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

    public Enclosure enclosure() {
        return enclosure;
    }

    public Surroundings surroundings() {
        return surroundings;
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

    public int viewCount() {
        return viewCount;
    }

    public void addView() {
        viewCount++;
    }

    /** A visitor could not get there: skip it for a while. */
    public void markUnreachable(long untilGameTime) {
        unreachableUntil = untilGameTime;
    }

    public boolean isUnreachable(long now) {
        return unreachableUntil > now;
    }

    public void markReached(long now) {
        lastReached = now;
        unreachableUntil = 0;
    }

    public long lastReached() {
        return lastReached;
    }

    /** Visitors can't get close enough: no viewing spots at all, or recent attempts failed. */
    public boolean viewingProblem(long now) {
        return viewingSpots.isEmpty() || isUnreachable(now);
    }
}
