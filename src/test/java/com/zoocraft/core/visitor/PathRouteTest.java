package com.zoocraft.core.visitor;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathRouteTest {
    /**
     * Rows are z, characters x: '0'-'9' grass at that height, 'a'-'j' path at height 0-9, ' ' no floor. A step may go
     * at most one block up or down.
     */
    private record MapGrid(String... rows) implements PathRoute.Grid {
        private char at(int x, int z) {
            return z < 0 || z >= rows.length || x < 0 || x >= rows[z].length() ? ' ' : rows[z].charAt(x);
        }

        @Override
        public int stepTo(int x, int z, int fromY) {
            char c = at(x, z);
            if (c == ' ') {
                return PathRoute.NO_FLOOR;
            }
            int h = c >= 'a' ? c - 'a' : c - '0';
            return Math.abs(h - fromY) <= 1 ? h : PathRoute.NO_FLOOR;
        }

        @Override
        public boolean path(int x, int y, int z) {
            return at(x, z) >= 'a';
        }
    }

    /** A terrace at height 4 with a path along its top, a ramp down its west end, and a path below its cliff. */
    private static final MapGrid TERRACE = new MapGrid(
            "eeeeeeeeee",
            "d444444444",
            "c444444444",
            "b000000000",
            "aaaaaaaaaa",
            "0000000000",
            "0000000000");

    @Test
    void routesAlongTheTerraceToTheRampInsteadOfToTheCliffEdge() {
        Optional<PathRoute.Cell> waypoint = PathRoute.waypoint(TERRACE, new PathRoute.Cell(9, 4, 0), 9, 6, 8, 1000);
        assertEquals(Optional.of(new PathRoute.Cell(1, 4, 0)), waypoint);
    }

    @Test
    void theWholeRouteWhenItIsShorterThanTheWaypointDistance() {
        Optional<PathRoute.Cell> waypoint = PathRoute.waypoint(TERRACE, new PathRoute.Cell(9, 4, 0), 9, 6, 40, 1000);
        assertEquals(Optional.of(new PathRoute.Cell(9, 0, 4)), waypoint, "the path block nearest the target, below the cliff");
    }

    @Test
    void aVisitorOnTheGrassJoinsThePathsFirst() {
        Optional<PathRoute.Cell> waypoint = PathRoute.waypoint(TERRACE, new PathRoute.Cell(9, 4, 2), 9, 6, 8, 1000);
        // Two steps to the path at (9, 0), then six along it.
        assertEquals(Optional.of(new PathRoute.Cell(3, 4, 0)), waypoint);
    }

    @Test
    void aNearbyPathIsJoinedAndFollowed() {
        MapGrid grid = new MapGrid(
                "000000aaaaaaaaaa",
                "0000000000000000");
        // Seven steps to the path at (6, 0), then one along it towards the target at its far end.
        assertEquals(Optional.of(new PathRoute.Cell(7, 0, 0)),
                PathRoute.waypoint(grid, new PathRoute.Cell(0, 0, 1), 15, 1, 8, 1000));
    }

    @Test
    void aPathFurtherThanTheJoinSearchMeansWalkStraight() {
        MapGrid grid = new MapGrid(
                "0000000000000000aaaa",
                "00000000000000000000");
        // A budget of 25 cells doesn't reach a path 16 steps away.
        assertEquals(Optional.empty(), PathRoute.waypoint(grid, new PathRoute.Cell(0, 0, 1), 19, 1, 5, 25));
    }

    @Test
    void noPathsMeansWalkStraight() {
        MapGrid grid = new MapGrid("0000000000", "0000000000", "0000000000");
        assertEquals(Optional.empty(), PathRoute.waypoint(grid, new PathRoute.Cell(0, 0, 0), 9, 2, 8, 1000));
    }

    @Test
    void pathsThatLeadNowhereNearerMeanWalkStraight() {
        MapGrid grid = new MapGrid(
                "aaaaa00000",
                "0000000000",
                "0000000000");
        // Standing on the path's east end, with the target to the south-east: the path only leads away.
        assertEquals(Optional.empty(), PathRoute.waypoint(grid, new PathRoute.Cell(4, 0, 0), 9, 2, 8, 1000));
    }

    @Test
    void theSearchStopsAtItsBudget() {
        int[] checks = {0};
        PathRoute.Grid open = new PathRoute.Grid() {
            @Override
            public int stepTo(int x, int z, int fromY) {
                checks[0]++;
                return 0;
            }

            @Override
            public boolean path(int x, int y, int z) {
                return false;
            }
        };
        assertEquals(Optional.empty(), PathRoute.waypoint(open, new PathRoute.Cell(0, 0, 0), 500, 500, 24, 200));
        assertTrue(checks[0] <= 4 * 200 + 4, "floor checks: " + checks[0]);
    }
}
