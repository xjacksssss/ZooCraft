package com.zoocraft.core.visitor;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Waypoints for long walks that follow the park's paths, ramps and bridges instead of the straight line to the target
 * (found in the terraced reference park: from the top of a terrace, every block in the target's direction lies below a
 * cliff, and partial paths towards them end at its edge).
 *
 * <p>A visitor standing off the paths first joins them: a breadth-first search over any floor finds the nearest path
 * block. From there a breadth-first search over connected path blocks, stepping at most one block up or down, finds the
 * path block nearest the target; the waypoint is the block about {@code ahead} steps along that route. Each search
 * stops after {@code budget} cells (in the terraced park a visitor at the foot of a cliff had the nearest path 30 blocks
 * along it, so joining needs about as much room as following).
 */
public final class PathRoute {
    public static final int NO_FLOOR = Integer.MIN_VALUE;

    /** The walkable world as the route search sees it. */
    public interface Grid {
        /** Feet height on column (x, z) reached from feet height {@code fromY} in one step, or {@link #NO_FLOOR}. */
        int stepTo(int x, int z, int fromY);

        /** Whether feet at (x, y, z) stand on a path block. */
        boolean path(int x, int y, int z);

        /**
         * Like {@link #stepTo} but only onto path blocks. Most neighbours of a path block aren't paths, so a grid can
         * answer this faster by checking the path block first.
         */
        default int pathStepTo(int x, int z, int fromY) {
            int y = stepTo(x, z, fromY);
            return y != NO_FLOOR && path(x, y, z) ? y : NO_FLOOR;
        }
    }

    public record Cell(int x, int y, int z) {
    }

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    /** The route must end at least this much nearer the target than where it joins the paths, or it doesn't help. */
    static final double MIN_GAIN = 4.0;

    private PathRoute() {
    }

    /**
     * The next waypoint from {@code start} towards column (targetX, targetZ), or empty when the paths don't lead
     * anywhere nearer (no path within reach, or the target is off the paths): the caller then walks straight.
     */
    public static Optional<Cell> waypoint(Grid grid, Cell start, int targetX, int targetZ, int ahead, int budget) {
        Search join = new Search();
        boolean joined = !grid.path(start.x(), start.y(), start.z());
        Cell entry = start;
        if (joined) {
            entry = join.toNearestPath(grid, start, budget);
            if (entry == null) {
                return Optional.empty();
            }
            if (join.depth(entry) >= ahead) {
                return Optional.of(join.ancestorAt(entry, ahead));
            }
        }
        Search along = new Search();
        Cell best = along.alongPaths(grid, entry, targetX, targetZ, budget);
        if (distance(best, targetX, targetZ) > distance(entry, targetX, targetZ) - MIN_GAIN) {
            return joined ? Optional.of(entry) : Optional.empty();
        }
        int left = ahead - (joined ? join.depth(entry) : 0);
        return Optional.of(along.depth(best) <= left ? best : along.ancestorAt(best, left));
    }

    private static double distance(Cell c, int x, int z) {
        double dx = c.x() - x, dz = c.z() - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** One breadth-first search, remembering each cell's parent and depth. */
    private static final class Search {
        private final Map<Long, Cell> parent = new HashMap<>();
        private final Map<Long, Integer> depths = new HashMap<>();
        private final ArrayDeque<Cell> queue = new ArrayDeque<>();

        private boolean visit(Cell cell, Cell from, int depth) {
            long k = key(cell.x(), cell.y(), cell.z());
            if (depths.containsKey(k)) {
                return false;
            }
            depths.put(k, depth);
            if (from != null) {
                parent.put(k, from);
            }
            queue.add(cell);
            return true;
        }

        int depth(Cell cell) {
            return depths.getOrDefault(key(cell.x(), cell.y(), cell.z()), 0);
        }

        /** The cell on the way to {@code cell} at the given depth from the search's start. */
        Cell ancestorAt(Cell cell, int depth) {
            Cell c = cell;
            while (depth(c) > depth) {
                c = parent.get(key(c.x(), c.y(), c.z()));
            }
            return c;
        }

        Cell toNearestPath(Grid grid, Cell start, int budget) {
            visit(start, null, 0);
            while (!queue.isEmpty() && depths.size() < budget) {
                Cell c = queue.poll();
                int d = depth(c);
                for (int[] s : STEPS) {
                    int x = c.x() + s[0], z = c.z() + s[1];
                    int y = grid.stepTo(x, z, c.y());
                    if (y == NO_FLOOR) {
                        continue;
                    }
                    Cell next = new Cell(x, y, z);
                    if (visit(next, c, d + 1) && grid.path(x, y, z)) {
                        return next;
                    }
                }
            }
            return null;
        }

        Cell alongPaths(Grid grid, Cell entry, int targetX, int targetZ, int budget) {
            visit(entry, null, 0);
            Cell best = entry;
            double bestDistance = distance(entry, targetX, targetZ);
            while (!queue.isEmpty() && depths.size() < budget && bestDistance > 2) {
                Cell c = queue.poll();
                int d = depth(c);
                for (int[] s : STEPS) {
                    int x = c.x() + s[0], z = c.z() + s[1];
                    int y = grid.pathStepTo(x, z, c.y());
                    if (y == NO_FLOOR) {
                        continue;
                    }
                    Cell next = new Cell(x, y, z);
                    if (visit(next, c, d + 1)) {
                        double dist = distance(next, targetX, targetZ);
                        if (dist < bestDistance) {
                            best = next;
                            bestDistance = dist;
                        }
                    }
                }
            }
            return best;
        }
    }
}
