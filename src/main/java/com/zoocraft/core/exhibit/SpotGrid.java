package com.zoocraft.core.exhibit;

/** Where visitors can stand, as seen by {@link ViewingSpots}. */
@FunctionalInterface
public interface SpotGrid {
    /** Visitor feet height on column (x, z) within 2 blocks of {@code nearY}, inside the zoo, or {@link FloorGrid#NO_FLOOR}. */
    int standY(int x, int z, int nearY);

    /** Whether the visitor would stand on a path block there. */
    default boolean path(int x, int y, int z) {
        return false;
    }
}
