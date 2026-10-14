package com.zoocraft.core.geometry;

import java.util.ArrayList;
import java.util.List;

/** Pure editing operations on a boundary's corner list (treated as a closed ring). Inputs are never modified. */
public final class DraftOps {
    private DraftOps() {
    }

    /** Index of the corner closest to (x, z) within {@code maxDistance}, or -1. */
    public static int nearestVertex(List<Vertex> vertices, int x, int z, double maxDistance) {
        int best = -1;
        double bestDistance = maxDistance;
        for (int i = 0; i < vertices.size(); i++) {
            Vertex v = vertices.get(i);
            double d = Math.hypot(v.x() - x, v.z() - z);
            if (d <= bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    /** Index of the start corner of the edge closest to (x, z) within {@code maxDistance}, or -1. */
    public static int nearestEdge(List<Vertex> vertices, int x, int z, double maxDistance) {
        int best = -1;
        double bestDistance = maxDistance;
        for (int i = 0; i < vertices.size(); i++) {
            Vertex a = vertices.get(i);
            Vertex b = vertices.get((i + 1) % vertices.size());
            double d = pointToSegment(x, z, a.x(), a.z(), b.x(), b.z());
            if (d <= bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    public static <T> List<T> insertAfter(List<T> vertices, int index, T vertex) {
        List<T> result = new ArrayList<>(vertices);
        result.add(index + 1, vertex);
        return result;
    }

    public static <T> List<T> move(List<T> vertices, int index, T vertex) {
        List<T> result = new ArrayList<>(vertices);
        result.set(index, vertex);
        return result;
    }

    public static <T> List<T> remove(List<T> vertices, int index) {
        List<T> result = new ArrayList<>(vertices);
        result.remove(index);
        return result;
    }

    private static double pointToSegment(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double lengthSquared = dx * dx + dz * dz;
        double t = lengthSquared == 0 ? 0 : ((px - ax) * dx + (pz - az) * dz) / lengthSquared;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(px - (ax + t * dx), pz - (az + t * dz));
    }
}
