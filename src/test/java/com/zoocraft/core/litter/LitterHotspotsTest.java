package com.zoocraft.core.litter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LitterHotspotsTest {
    private static LitterHotspots.Point p(int x, int z, int units) {
        return new LitterHotspots.Point(x, z, units);
    }

    @Test
    void groupsByCellAndDropsSmallOnes() {
        List<LitterHotspots.Hotspot> spots = LitterHotspots.find(List.of(
                p(1, 1, 1), p(5, 5, 2),          // cell (0, 0): 3 units
                p(20, 1, 1), p(21, 2, 1)),       // cell (1, 0): 2 units, too few
                16, 3, 3);
        assertEquals(1, spots.size());
        assertEquals(3, spots.getFirst().units());
        assertEquals(8, spots.getFirst().centreX());
        assertEquals(8, spots.getFirst().centreZ());
    }

    @Test
    void sortsByUnitsAndRespectsTheLimit() {
        List<LitterHotspots.Hotspot> spots = LitterHotspots.find(List.of(
                p(1, 1, 3), p(17, 1, 5), p(33, 1, 4), p(49, 1, 6)), 16, 3, 3);
        assertEquals(3, spots.size());
        assertEquals(List.of(6, 5, 4), spots.stream().map(LitterHotspots.Hotspot::units).toList());
    }

    @Test
    void negativeCoordinatesUseFloorDivision() {
        List<LitterHotspots.Hotspot> spots = LitterHotspots.find(List.of(p(-1, -1, 2), p(-15, -2, 1), p(1, 1, 3)), 16, 3, 3);
        assertEquals(2, spots.size());
        assertTrue(spots.stream().anyMatch(s -> s.centreX() == -8 && s.centreZ() == -8), "cell (-1, -1) centre should be (-8, -8): " + spots);
    }
}
