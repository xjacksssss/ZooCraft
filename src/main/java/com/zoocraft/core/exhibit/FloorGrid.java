package com.zoocraft.core.exhibit;

/** The floor animals can walk on, as seen by {@link EnclosureFill}. */
@FunctionalInterface
public interface FloorGrid {
    int NO_FLOOR = Integer.MIN_VALUE;

    /**
     * Feet height on column (x, z) for an animal arriving from feet height {@code fromY}, stepping at most one block
     * up or down, or {@link #NO_FLOOR} when it can't stand there.
     */
    int stepTo(int x, int z, int fromY);
}
