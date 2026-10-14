package com.zoocraft.core.exhibit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Groups animals into exhibits: animals within the link distance of each other (chained) share an exhibit. */
public final class ExhibitGrouping {
    private ExhibitGrouping() {
    }

    public static List<List<AnimalPoint>> group(List<AnimalPoint> animals, double linkDistance) {
        double linkSq = linkDistance * linkDistance;
        boolean[] used = new boolean[animals.size()];
        List<List<AnimalPoint>> groups = new ArrayList<>();
        for (int start = 0; start < animals.size(); start++) {
            if (used[start]) {
                continue;
            }
            List<AnimalPoint> group = new ArrayList<>();
            Deque<Integer> queue = new ArrayDeque<>();
            queue.add(start);
            used[start] = true;
            while (!queue.isEmpty()) {
                AnimalPoint current = animals.get(queue.poll());
                group.add(current);
                for (int other = 0; other < animals.size(); other++) {
                    if (!used[other] && distanceSq(current, animals.get(other)) <= linkSq) {
                        used[other] = true;
                        queue.add(other);
                    }
                }
            }
            groups.add(group);
        }
        return groups;
    }

    /**
     * For each current group, the index of the previous group it continues (the one sharing the most animals),
     * or -1 for a new exhibit. Each previous group continues at most one current group.
     */
    public static int[] match(List<Set<UUID>> previous, List<Set<UUID>> current) {
        int[] result = new int[current.size()];
        boolean[] taken = new boolean[previous.size()];
        java.util.Arrays.fill(result, -1);
        // Greedy by overlap size, largest first, so the strongest continuations win.
        List<int[]> pairs = new ArrayList<>();
        for (int c = 0; c < current.size(); c++) {
            for (int p = 0; p < previous.size(); p++) {
                int overlap = 0;
                for (UUID id : current.get(c)) {
                    if (previous.get(p).contains(id)) {
                        overlap++;
                    }
                }
                if (overlap > 0) {
                    pairs.add(new int[]{overlap, c, p});
                }
            }
        }
        pairs.sort((a, b) -> Integer.compare(b[0], a[0]));
        for (int[] pair : pairs) {
            if (result[pair[1]] == -1 && !taken[pair[2]]) {
                result[pair[1]] = pair[2];
                taken[pair[2]] = true;
            }
        }
        return result;
    }

    private static double distanceSq(AnimalPoint a, AnimalPoint b) {
        double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
