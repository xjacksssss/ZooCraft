package com.zoocraft.core.exhibit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An exhibit's enclosure (Phase 3 spec section 8.1): the floor columns its animals can walk to, each with its feet
 * height, and whether the fill stopped by itself ({@code bounded}) or hit its cap (open land).
 */
public record Enclosure(boolean bounded, Map<Long, Integer> floor) {
    public static final Enclosure NONE = new Enclosure(false, Map.of());

    public static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    public static int x(long key) {
        return (int) (key >> 32);
    }

    public static int z(long key) {
        return (int) key;
    }

    public int area() {
        return floor.size();
    }

    public boolean contains(int x, int z) {
        return floor.containsKey(key(x, z));
    }

    public int feetY(int x, int z) {
        return floor.getOrDefault(key(x, z), FloorGrid.NO_FLOOR);
    }

    /** Floor cells, as {x, y, z}, with at least one of their four neighbours outside the enclosure. */
    public List<int[]> edge() {
        List<int[]> edge = new ArrayList<>();
        for (Map.Entry<Long, Integer> cell : floor.entrySet()) {
            int x = x(cell.getKey()), z = z(cell.getKey());
            if (!contains(x + 1, z) || !contains(x - 1, z) || !contains(x, z + 1) || !contains(x, z - 1)) {
                edge.add(new int[]{x, cell.getValue(), z});
            }
        }
        return edge;
    }

    /** Mean block-centre x of the floor. */
    public double centreX() {
        double sum = 0;
        for (long k : floor.keySet()) {
            sum += x(k) + 0.5;
        }
        return floor.isEmpty() ? 0 : sum / floor.size();
    }

    public double centreZ() {
        double sum = 0;
        for (long k : floor.keySet()) {
            sum += z(k) + 0.5;
        }
        return floor.isEmpty() ? 0 : sum / floor.size();
    }
}
