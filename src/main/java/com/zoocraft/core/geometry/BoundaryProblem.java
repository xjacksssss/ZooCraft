package com.zoocraft.core.geometry;

import org.jetbrains.annotations.Nullable;

/**
 * Why a boundary was rejected.
 *
 * @param at       where the problem is, for self-intersections
 * @param otherZoo the zoo in the way, for overlaps
 * @param limit    the limit that was broken, for size and corner-count problems
 */
public record BoundaryProblem(Kind kind, @Nullable Vertex at, @Nullable String otherZoo, long limit) {
    public enum Kind {
        TOO_FEW_CORNERS,
        TOO_MANY_CORNERS,
        SELF_INTERSECTING,
        TOO_SMALL,
        TOO_LARGE,
        OVERLAPS
    }

    static BoundaryProblem of(Kind kind, long limit) {
        return new BoundaryProblem(kind, null, null, limit);
    }
}
