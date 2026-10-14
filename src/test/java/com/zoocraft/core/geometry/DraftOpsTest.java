package com.zoocraft.core.geometry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.zoocraft.core.geometry.BoundaryValidatorTest.square;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DraftOpsTest {
    private final List<Vertex> square = square(0, 0, 20);

    @Test
    void nearestVertexWithinRange() {
        assertEquals(1, DraftOps.nearestVertex(square, 19, 1, 1.5));
        assertEquals(-1, DraftOps.nearestVertex(square, 10, 10, 1.5));
    }

    @Test
    void nearestEdgeWithinRangeIncludingClosingEdge() {
        assertEquals(0, DraftOps.nearestEdge(square, 10, 1, 1.5));
        assertEquals(3, DraftOps.nearestEdge(square, 0, 10, 1.5));
        assertEquals(-1, DraftOps.nearestEdge(square, 10, 10, 1.5));
    }

    @Test
    void insertAfterPutsCornerBetweenEdgeEnds() {
        List<Vertex> result = DraftOps.insertAfter(square, 0, new Vertex(10, -3));
        assertEquals(5, result.size());
        assertEquals(new Vertex(10, -3), result.get(1));
        assertEquals(new Vertex(20, 0), result.get(2));
    }

    @Test
    void moveReplacesCorner() {
        List<Vertex> result = DraftOps.move(square, 2, new Vertex(25, 25));
        assertEquals(new Vertex(25, 25), result.get(2));
        assertEquals(4, result.size());
    }

    @Test
    void removeDropsCorner() {
        List<Vertex> result = DraftOps.remove(square, 0);
        assertEquals(3, result.size());
        assertEquals(new Vertex(20, 0), result.getFirst());
    }
}
