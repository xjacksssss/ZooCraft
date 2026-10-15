package com.zoocraft.core.exhibit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Viewing spots along an enclosure's edge (Phase 3 spec section 8.1): standable blocks outside the enclosure, within 2
 * blocks of its edge, spread round it (one per run of roughly equal length, in order of angle round the centre), path
 * blocks first, then the closest to the edge.
 */
public final class ViewingSpots {
    public static final int EDGE_DISTANCE = 2;

    private ViewingSpots() {
    }

    /** @return spots as {x, y, z} */
    public static List<int[]> pick(Enclosure enclosure, SpotGrid grid, int maxSpots) {
        // {x, y, z, distance to the nearest edge cell}
        Map<Long, int[]> candidates = new LinkedHashMap<>();
        for (int[] edge : enclosure.edge()) {
            for (int dx = -EDGE_DISTANCE; dx <= EDGE_DISTANCE; dx++) {
                for (int dz = -EDGE_DISTANCE; dz <= EDGE_DISTANCE; dz++) {
                    int x = edge[0] + dx, z = edge[2] + dz;
                    if (enclosure.contains(x, z)) {
                        continue;
                    }
                    int d = Math.max(Math.abs(dx), Math.abs(dz));
                    long key = Enclosure.key(x, z);
                    int[] known = candidates.get(key);
                    if (known != null) {
                        known[3] = Math.min(known[3], d);
                    } else {
                        int y = grid.standY(x, z, edge[1]);
                        if (y != FloorGrid.NO_FLOOR) {
                            candidates.put(key, new int[]{x, y, z, d});
                        }
                    }
                }
            }
        }
        if (candidates.isEmpty() || maxSpots <= 0) {
            return List.of();
        }
        double cx = enclosure.centreX(), cz = enclosure.centreZ();
        List<int[]> ordered = new ArrayList<>(candidates.values());
        ordered.sort(Comparator.comparingDouble(c -> Math.atan2(c[2] + 0.5 - cz, c[0] + 0.5 - cx)));
        int n = Math.min(maxSpots, ordered.size());
        Comparator<int[]> best = Comparator.<int[]>comparingInt(c -> grid.path(c[0], c[1], c[2]) ? 0 : 1).thenComparingInt(c -> c[3]);
        List<int[]> spots = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int[] chosen = ordered.subList(i * ordered.size() / n, (i + 1) * ordered.size() / n).stream().min(best).orElseThrow();
            spots.add(new int[]{chosen[0], chosen[1], chosen[2]});
        }
        return spots;
    }
}
