package com.zoocraft.core.exhibit;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewingSpotsTest {
    /** An 8 by 8 floor at x 0..7, z 0..7, fenced on the north, west and east; the south side is a low edge visitors can stand at. */
    private static Enclosure pen() {
        Map<Long, Integer> floor = new HashMap<>();
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                floor.put(Enclosure.key(x, z), 0);
            }
        }
        return new Enclosure(true, floor);
    }

    private static boolean fence(int x, int z) {
        return (x == -1 || x == 8) && z >= -1 && z <= 7 || z == -1 && x >= -1 && x <= 8;
    }

    /** Visitors stand anywhere but the floor and the fence; a path runs along z = 9. */
    private static final SpotGrid GRID = new SpotGrid() {
        @Override
        public int standY(int x, int z, int nearY) {
            return fence(x, z) || x >= 0 && x < 8 && z >= 0 && z < 8 ? FloorGrid.NO_FLOOR : 0;
        }

        @Override
        public boolean path(int x, int y, int z) {
            return z == 9;
        }
    };

    @Test
    void spotsAreOutsideTheEnclosureWithinTwoBlocksOfItsEdge() {
        List<int[]> spots = ViewingSpots.pick(pen(), GRID, 12);
        assertEquals(12, spots.size());
        for (int[] s : spots) {
            assertFalse(s[0] >= 0 && s[0] <= 7 && s[2] >= 0 && s[2] <= 7, "spot inside: " + Arrays.toString(s));
            assertFalse(fence(s[0], s[2]), "spot on the fence: " + Arrays.toString(s));
            int dx = Math.max(Math.max(-s[0], s[0] - 7), 0), dz = Math.max(Math.max(-s[2], s[2] - 7), 0);
            assertTrue(Math.max(dx, dz) <= 2, "spot too far from the edge: " + Arrays.toString(s));
        }
    }

    @Test
    void spotsSpreadRoundThePen() {
        List<int[]> spots = ViewingSpots.pick(pen(), GRID, 8);
        long north = spots.stream().filter(s -> s[2] < 0).count(), south = spots.stream().filter(s -> s[2] > 7).count();
        long west = spots.stream().filter(s -> s[0] < 0).count(), east = spots.stream().filter(s -> s[0] > 7).count();
        assertTrue(north > 0 && south > 0 && west > 0 && east > 0, "not spread: " + north + " " + south + " " + west + " " + east);
    }

    @Test
    void pathBlocksBeatNearerGrass() {
        List<int[]> spots = ViewingSpots.pick(pen(), GRID, 8);
        List<int[]> south = spots.stream().filter(s -> s[2] > 7 && s[0] >= 0 && s[0] <= 7).toList();
        assertFalse(south.isEmpty(), "no spot on the south side");
        assertTrue(south.stream().allMatch(s -> s[2] == 9), "grass at z = 8 beat the path at z = 9");
    }

    @Test
    void withoutPathsTheNearestRowWins() {
        List<int[]> spots = ViewingSpots.pick(pen(), (x, z, y) -> GRID.standY(x, z, y), 8);
        assertTrue(spots.stream().filter(s -> s[2] > 7 && s[0] >= 0 && s[0] <= 7).allMatch(s -> s[2] == 8));
    }

    @Test
    void noStandableGroundGivesNoSpots() {
        assertTrue(ViewingSpots.pick(pen(), (x, z, y) -> FloorGrid.NO_FLOOR, 12).isEmpty());
        assertTrue(ViewingSpots.pick(Enclosure.NONE, GRID, 12).isEmpty());
    }
}
