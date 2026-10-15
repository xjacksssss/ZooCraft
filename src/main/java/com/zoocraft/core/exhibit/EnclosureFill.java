package com.zoocraft.core.exhibit;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds an exhibit's enclosure with a bounded flood fill (Phase 3 spec section 8.1, decision 4.4): from the animals'
 * floor blocks across floor they could walk to, at most one block up or down per step, stopping at {@code maxArea}
 * columns. A fill that stops by itself is a bounded enclosure; one that hits the cap is open land.
 */
public final class EnclosureFill {
    /** How far below a flying or jumping animal its floor is looked for. */
    private static final int START_DROP = 4;
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private EnclosureFill() {
    }

    /** @param starts animals' feet positions as {x, y, z} */
    public static Enclosure fill(FloorGrid grid, List<int[]> starts, int maxArea) {
        Map<Long, Integer> floor = new HashMap<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int[] s : starts) {
            int y = startHeight(grid, s[0], s[1], s[2]);
            if (y != FloorGrid.NO_FLOOR && floor.size() < maxArea && floor.putIfAbsent(Enclosure.key(s[0], s[2]), y) == null) {
                queue.add(new int[]{s[0], y, s[2]});
            }
        }
        if (floor.isEmpty()) {
            return Enclosure.NONE;
        }
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            for (int[] d : DIRECTIONS) {
                int nx = cell[0] + d[0], nz = cell[2] + d[1];
                long key = Enclosure.key(nx, nz);
                if (floor.containsKey(key)) {
                    continue;
                }
                int ny = grid.stepTo(nx, nz, cell[1]);
                if (ny == FloorGrid.NO_FLOOR) {
                    continue;
                }
                if (floor.size() >= maxArea) {
                    return new Enclosure(false, Map.copyOf(floor));
                }
                floor.put(key, ny);
                queue.add(new int[]{nx, ny, nz});
            }
        }
        return new Enclosure(floor.size() < maxArea, Map.copyOf(floor));
    }

    /** The animal's own floor, or the floor up to a few blocks below a flying or jumping animal. */
    private static int startHeight(FloorGrid grid, int x, int y, int z) {
        for (int from = y; from >= y - START_DROP; from--) {
            int h = grid.stepTo(x, z, from);
            if (h != FloorGrid.NO_FLOOR && h <= y) {
                return h;
            }
        }
        return FloorGrid.NO_FLOOR;
    }
}
