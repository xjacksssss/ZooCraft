package com.zoocraft.core.litter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Finds where litter is piling up, so the dashboard can say where to clean or add a bin (spec section 9.4). */
public final class LitterHotspots {
    /** One piece of litter: position and how many units (pile size) it counts for. */
    public record Point(int x, int z, int units) {
    }

    /** A grid cell with a lot of litter, given by its centre. */
    public record Hotspot(int centreX, int centreZ, int units) {
    }

    private LitterHotspots() {
    }

    public static List<Hotspot> find(List<Point> litter, int cellSize, int minUnits, int limit) {
        Map<Long, int[]> cells = new HashMap<>();
        for (Point p : litter) {
            int cx = Math.floorDiv(p.x(), cellSize);
            int cz = Math.floorDiv(p.z(), cellSize);
            cells.computeIfAbsent(((long) cx << 32) ^ (cz & 0xFFFFFFFFL), k -> new int[]{cx, cz, 0})[2] += p.units();
        }
        List<Hotspot> spots = new ArrayList<>();
        for (int[] cell : cells.values()) {
            if (cell[2] >= minUnits) {
                spots.add(new Hotspot(cell[0] * cellSize + cellSize / 2, cell[1] * cellSize + cellSize / 2, cell[2]));
            }
        }
        spots.sort(Comparator.comparingInt(Hotspot::units).reversed());
        return spots.size() > limit ? List.copyOf(spots.subList(0, limit)) : List.copyOf(spots);
    }
}
