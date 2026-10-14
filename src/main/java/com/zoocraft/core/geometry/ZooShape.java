package com.zoocraft.core.geometry;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An immutable zoo boundary on the X/Z plane, wrapping a JTS polygon whose corners are block centres.
 * The shape may be invalid (for example self-intersecting); {@link BoundaryValidator} decides whether it is acceptable.
 */
public final class ZooShape {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    private final List<Vertex> vertices;
    private final Polygon polygon;
    private final PreparedGeometry prepared;
    private final int minX, maxX, minZ, maxZ;
    private Set<Long> chunks;

    private ZooShape(List<Vertex> vertices) {
        this.vertices = List.copyOf(vertices);
        Coordinate[] ring = new Coordinate[vertices.size() + 1];
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < vertices.size(); i++) {
            Vertex v = vertices.get(i);
            ring[i] = new Coordinate(v.x() + 0.5, v.z() + 0.5);
            minX = Math.min(minX, v.x());
            maxX = Math.max(maxX, v.x());
            minZ = Math.min(minZ, v.z());
            maxZ = Math.max(maxZ, v.z());
        }
        ring[vertices.size()] = ring[0];
        this.polygon = FACTORY.createPolygon(ring);
        this.prepared = PreparedGeometryFactory.prepare(polygon);
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    /** Builds a shape from at least 3 corners. Consecutive duplicate corners should already be removed. */
    public static ZooShape of(List<Vertex> vertices) {
        if (vertices.size() < 3) {
            throw new IllegalArgumentException("A shape needs at least 3 corners");
        }
        return new ZooShape(vertices);
    }

    public List<Vertex> vertices() {
        return vertices;
    }

    Polygon polygon() {
        return polygon;
    }

    /** True when the block's centre is inside the boundary or on its edge. */
    public boolean containsBlock(int x, int z) {
        if (x < minX || x > maxX || z < minZ || z > maxZ) {
            return false;
        }
        return prepared.covers(point(x + 0.5, z + 0.5));
    }

    /** True when the exact position is inside the boundary or on its edge. */
    public boolean contains(double x, double z) {
        if (x < minX || x > maxX + 1 || z < minZ || z > maxZ + 1) {
            return false;
        }
        return prepared.covers(point(x, z));
    }

    public double distanceToEdge(double x, double z) {
        return polygon.getExteriorRing().distance(point(x, z));
    }

    /** Unit vector {x, z} perpendicular to the edge nearest the position, pointing out of the zoo. */
    public double[] outwardNormalAt(double x, double z) {
        Coordinate[] ring = polygon.getExteriorRing().getCoordinates();
        Coordinate p = new Coordinate(x, z);
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < ring.length - 1; i++) {
            double d = org.locationtech.jts.algorithm.Distance.pointToSegment(p, ring[i], ring[i + 1]);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        Coordinate a = ring[best];
        Coordinate b = ring[best + 1];
        double dx = b.x - a.x;
        double dz = b.y - a.y;
        double length = Math.sqrt(dx * dx + dz * dz);
        double nx = dz / length;
        double nz = -dx / length;
        double midX = (a.x + b.x) / 2;
        double midZ = (a.y + b.y) / 2;
        if (prepared.contains(point(midX + nx * 0.25, midZ + nz * 0.25))) {
            nx = -nx;
            nz = -nz;
        }
        return new double[]{nx + 0.0, nz + 0.0};
    }

    public double area() {
        return polygon.getArea();
    }

    public int minX() {
        return minX;
    }

    public int maxX() {
        return maxX;
    }

    public int minZ() {
        return minZ;
    }

    public int maxZ() {
        return maxZ;
    }

    /** True when the two shapes share some area. Shapes that only touch along an edge do not overlap. */
    public boolean overlaps(ZooShape other) {
        if (other.maxX < minX || other.minX > maxX || other.maxZ < minZ || other.minZ > maxZ) {
            return false;
        }
        return polygon.relate(other.polygon, "2********");
    }

    /** Chunk keys (see {@link #chunkKey}) of every chunk the shape touches. */
    public Set<Long> coveredChunks() {
        if (chunks == null) {
            Set<Long> result = new HashSet<>();
            for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
                for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                    Envelope chunk = new Envelope(cx * 16, cx * 16 + 16, cz * 16, cz * 16 + 16);
                    if (prepared.intersects(FACTORY.toGeometry(chunk))) {
                        result.add(chunkKey(cx, cz));
                    }
                }
            }
            chunks = Set.copyOf(result);
        }
        return chunks;
    }

    /** Same packing as Minecraft's {@code ChunkPos.asLong}. */
    public static long chunkKey(int chunkX, int chunkZ) {
        return (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
    }

    private static Point point(double x, double z) {
        return FACTORY.createPoint(new Coordinate(x, z));
    }
}
