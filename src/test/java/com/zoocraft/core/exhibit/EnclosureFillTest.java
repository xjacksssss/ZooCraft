package com.zoocraft.core.exhibit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnclosureFillTest {
    @Test
    void closedEightByEightPenIsBounded() {
        Enclosure e = EnclosureFill.fill(new GridFixture(GridFixture.pen(8, 8)), List.of(new int[]{4, 0, 4}), 1024);
        assertTrue(e.bounded());
        assertEquals(64, e.area());
        assertEquals(28, e.edge().size());
        assertTrue(e.contains(1, 1) && !e.contains(0, 0));
        assertEquals(5.0, e.centreX(), 1e-9);
    }

    @Test
    void twentyByTwentyPenIsBounded() {
        Enclosure e = EnclosureFill.fill(new GridFixture(GridFixture.pen(20, 20)), List.of(new int[]{3, 0, 3}), 1024);
        assertTrue(e.bounded());
        assertEquals(400, e.area());
    }

    @Test
    void lShapedPen() {
        GridFixture g = new GridFixture(
                "######",
                "#....#",
                "#....#",
                "#..###",
                "#..#",
                "####");
        Enclosure e = EnclosureFill.fill(g, List.of(new int[]{1, 0, 1}), 1024);
        assertTrue(e.bounded());
        assertEquals(12, e.area());
    }

    @Test
    void anOpenGateLetsTheFillEscapeToTheCap() {
        String[] rows = GridFixture.pen(8, 8);
        rows[0] = "####o#####";
        Enclosure e = EnclosureFill.fill(new GridFixture(rows), List.of(new int[]{4, 0, 4}), 200);
        assertFalse(e.bounded());
        assertEquals(200, e.area());
    }

    @Test
    void stepsOfOneBlockAreClimbedButTwoAreNot() {
        GridFixture step = new GridFixture(
                "#######",
                "#..1..#",
                "#..#..#",
                "#######");
        assertEquals(9, EnclosureFill.fill(step, List.of(new int[]{1, 0, 1}), 1024).area());
        GridFixture cliff = new GridFixture(
                "#######",
                "#..2..#",
                "#..#..#",
                "#######");
        assertEquals(4, EnclosureFill.fill(cliff, List.of(new int[]{1, 0, 1}), 1024).area());
    }

    @Test
    void aStartAboveTheFloorDropsToIt() {
        Enclosure e = EnclosureFill.fill(new GridFixture(GridFixture.pen(4, 4)), List.of(new int[]{2, 3, 2}), 1024);
        assertEquals(16, e.area(), "a flying or jumping animal still finds its pen");
        assertTrue(e.bounded());
    }

    @Test
    void severalStartsShareOneFill() {
        Enclosure e = EnclosureFill.fill(new GridFixture(GridFixture.pen(4, 4)), List.of(new int[]{1, 0, 1}, new int[]{4, 0, 4}), 1024);
        assertEquals(16, e.area());
    }

    @Test
    void noFloorAtAllIsOpen() {
        Enclosure e = EnclosureFill.fill((x, z, y) -> FloorGrid.NO_FLOOR, List.of(new int[]{0, 0, 0}), 1024);
        assertFalse(e.bounded());
        assertEquals(0, e.area());
    }

    @Test
    void eachRegionIsFilledOnItsOwn() {
        // Two pens side by side, plus an animal that has escaped onto the open floor outside them.
        GridFixture g = new GridFixture(
                "##########",
                "#...##...#",
                "#...##...#",
                "##########");
        List<Enclosure> regions = EnclosureFill.regions(g, List.of(new int[]{1, 0, 1}, new int[]{2, 0, 2}, new int[]{7, 0, 1},
                new int[]{20, 0, 20}), 200);
        assertEquals(3, regions.size(), "the two animals in the first pen share a region");
        assertTrue(regions.get(0).bounded() && regions.get(0).area() == 6);
        assertTrue(regions.get(1).bounded() && regions.get(1).area() == 6);
        assertFalse(regions.get(2).bounded());
        assertTrue(EnclosureFill.regions(g, List.of(), 200).isEmpty());
    }

    @Test
    void oneJobStaysWithinTwiceTheCap() {
        // Ten animals far apart on open land: without a budget each would start its own capped fill.
        List<int[]> starts = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            starts.add(new int[]{1000 * i, 0, 0});
        }
        List<Enclosure> regions = EnclosureFill.regions((x, z, y) -> 0, starts, 200);
        int cells = regions.stream().mapToInt(Enclosure::area).sum();
        assertTrue(cells <= 400, "filled " + cells + " cells for one job");
    }
}
