package com.zoocraft.core.geometry;

import org.junit.jupiter.api.Test;

import static com.zoocraft.core.geometry.BoundaryValidatorTest.square;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZooShapeTest {
    private final ZooShape shape = ZooShape.of(square(0, 0, 20));

    @Test
    void containsInteriorAndEdgeBlocks() {
        assertTrue(shape.containsBlock(10, 10));
        assertTrue(shape.containsBlock(0, 0));
        assertTrue(shape.containsBlock(20, 10));
        assertFalse(shape.containsBlock(21, 10));
        assertFalse(shape.containsBlock(-1, 5));
    }

    @Test
    void distanceToEdge() {
        assertEquals(0.0, shape.distanceToEdge(0.5, 10.5), 1e-9);
        assertEquals(5.0, shape.distanceToEdge(5.5, 10.5), 1e-9);
        assertEquals(3.0, shape.distanceToEdge(-2.5, 10.5), 1e-9);
    }

    @Test
    void outwardNormalPointsOutsideOnEachSide() {
        assertNormal(shape.outwardNormalAt(0.5, 10.5), -1, 0);
        assertNormal(shape.outwardNormalAt(20.5, 10.5), 1, 0);
        assertNormal(shape.outwardNormalAt(10.5, 0.5), 0, -1);
        assertNormal(shape.outwardNormalAt(10.5, 20.5), 0, 1);
        // From outside, the nearest edge's outward normal still points away from the zoo.
        assertNormal(shape.outwardNormalAt(-3.5, 10.5), -1, 0);
    }

    @Test
    void outwardNormalSameForReversedWinding() {
        ZooShape reversed = ZooShape.of(java.util.List.of(new Vertex(0, 20), new Vertex(20, 20), new Vertex(20, 0), new Vertex(0, 0)));
        assertNormal(reversed.outwardNormalAt(0.5, 10.5), -1, 0);
    }

    @Test
    void boundsAndArea() {
        assertEquals(0, shape.minX());
        assertEquals(20, shape.maxX());
        assertEquals(0, shape.minZ());
        assertEquals(20, shape.maxZ());
        assertEquals(400.0, shape.area(), 1e-9);
    }

    @Test
    void coveredChunks() {
        ZooShape forty = ZooShape.of(square(0, 0, 39));
        assertEquals(9, forty.coveredChunks().size());
        assertTrue(forty.coveredChunks().contains(ZooShape.chunkKey(2, 2)));
        assertFalse(forty.coveredChunks().contains(ZooShape.chunkKey(3, 0)));
    }

    @Test
    void triangleDoesNotCoverFarCorner() {
        ZooShape triangle = ZooShape.of(java.util.List.of(new Vertex(0, 0), new Vertex(63, 0), new Vertex(0, 63)));
        assertTrue(triangle.coveredChunks().contains(ZooShape.chunkKey(0, 0)));
        assertFalse(triangle.coveredChunks().contains(ZooShape.chunkKey(3, 3)));
    }

    private static void assertNormal(double[] normal, double x, double z) {
        assertEquals(x, normal[0], 1e-9);
        assertEquals(z, normal[1], 1e-9);
    }
}
