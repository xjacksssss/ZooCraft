package com.zoocraft.animal;

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
    private final UUID id;
    private List<Mob> animals = List.of();
    private Set<String> species = Set.of();
    private Vec3 centre = Vec3.ZERO;
    private List<BlockPos> viewingSpots = List.of();
    private long unreachableUntil;
    private long lastReached = -1;
    private int viewCount;

    Exhibit(UUID id) {
        this.id = id;
    }

    void update(List<Mob> animals, Set<String> species, Vec3 centre, List<BlockPos> viewingSpots) {
        this.animals = List.copyOf(animals);
        this.species = Set.copyOf(species);
        this.centre = centre;
        this.viewingSpots = List.copyOf(viewingSpots);
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
