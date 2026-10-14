package com.zoocraft.core.geometry;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundaryValidatorTest {
    private static final BoundaryLimits LIMITS = new BoundaryLimits(64, 65_536, 64);

    static List<Vertex> square(int x, int z, int size) {
        return List.of(new Vertex(x, z), new Vertex(x + size, z), new Vertex(x + size, z + size), new Vertex(x, z + size));
    }

    private static Optional<BoundaryProblem> validate(List<Vertex> vertices, List<NamedShape> others) {
        return BoundaryValidator.validate(vertices, LIMITS, others);
    }

    @Test
    void squareIsValid() {
        assertTrue(validate(square(0, 0, 20), List.of()).isEmpty());
    }

    @Test
    void clockwiseAndAnticlockwiseBothValid() {
        List<Vertex> reversed = new ArrayList<>(square(0, 0, 20));
        java.util.Collections.reverse(reversed);
        assertTrue(validate(reversed, List.of()).isEmpty());
        assertEquals(ZooShape.of(square(0, 0, 20)).area(), ZooShape.of(reversed).area(), 1e-9);
    }

    @Test
    void twoCornersIsTooFew() {
        assertEquals(BoundaryProblem.Kind.TOO_FEW_CORNERS,
                validate(List.of(new Vertex(0, 0), new Vertex(10, 10)), List.of()).orElseThrow().kind());
    }

    @Test
    void repeatedConsecutiveCornerIsIgnored() {
        List<Vertex> vertices = List.of(new Vertex(0, 0), new Vertex(20, 0), new Vertex(20, 0),
                new Vertex(20, 20), new Vertex(0, 20), new Vertex(0, 0));
        assertTrue(validate(vertices, List.of()).isEmpty());
        assertEquals(4, BoundaryValidator.normalise(vertices).size());
    }

    @Test
    void repeatedCornersCanLeaveTooFew() {
        List<Vertex> vertices = List.of(new Vertex(0, 0), new Vertex(0, 0), new Vertex(20, 0), new Vertex(20, 0));
        assertEquals(BoundaryProblem.Kind.TOO_FEW_CORNERS, validate(vertices, List.of()).orElseThrow().kind());
    }

    @Test
    void bowTieSelfIntersects() {
        List<Vertex> bowTie = List.of(new Vertex(0, 0), new Vertex(20, 20), new Vertex(20, 0), new Vertex(0, 20));
        BoundaryProblem problem = validate(bowTie, List.of()).orElseThrow();
        assertEquals(BoundaryProblem.Kind.SELF_INTERSECTING, problem.kind());
        assertNotNull(problem.at());
        assertEquals(10, problem.at().x());
        assertEquals(10, problem.at().z());
    }

    @Test
    void collinearCornersAreTooSmall() {
        List<Vertex> line = List.of(new Vertex(0, 0), new Vertex(10, 0), new Vertex(20, 0));
        BoundaryProblem problem = validate(line, List.of()).orElseThrow();
        assertTrue(problem.kind() == BoundaryProblem.Kind.TOO_SMALL || problem.kind() == BoundaryProblem.Kind.SELF_INTERSECTING);
    }

    @Test
    void tinySquareIsTooSmall() {
        BoundaryProblem problem = validate(square(0, 0, 4), List.of()).orElseThrow();
        assertEquals(BoundaryProblem.Kind.TOO_SMALL, problem.kind());
        assertEquals(64, problem.limit());
    }

    @Test
    void hugeSquareIsTooLarge() {
        BoundaryProblem problem = validate(square(0, 0, 300), List.of()).orElseThrow();
        assertEquals(BoundaryProblem.Kind.TOO_LARGE, problem.kind());
        assertEquals(65_536, problem.limit());
    }

    @Test
    void tooManyCorners() {
        List<Vertex> circle = new ArrayList<>();
        for (int i = 0; i < 65; i++) {
            double angle = 2 * Math.PI * i / 65;
            circle.add(new Vertex((int) Math.round(Math.cos(angle) * 100), (int) Math.round(Math.sin(angle) * 100)));
        }
        BoundaryProblem problem = validate(circle, List.of()).orElseThrow();
        assertEquals(BoundaryProblem.Kind.TOO_MANY_CORNERS, problem.kind());
        assertEquals(64, problem.limit());
    }

    @Test
    void overlapNamesTheOtherZoo() {
        NamedShape riverside = new NamedShape("Riverside Zoo", ZooShape.of(square(0, 0, 20)));
        BoundaryProblem problem = validate(square(10, 10, 20), List.of(riverside)).orElseThrow();
        assertEquals(BoundaryProblem.Kind.OVERLAPS, problem.kind());
        assertEquals("Riverside Zoo", problem.otherZoo());
    }

    @Test
    void zooInsideAnotherOverlaps() {
        NamedShape big = new NamedShape("Big", ZooShape.of(square(0, 0, 100)));
        assertEquals(BoundaryProblem.Kind.OVERLAPS, validate(square(10, 10, 20), List.of(big)).orElseThrow().kind());
    }

    @Test
    void touchingEdgesAreAllowed() {
        NamedShape left = new NamedShape("Left", ZooShape.of(square(0, 0, 20)));
        assertTrue(validate(square(20, 0, 20), List.of(left)).isEmpty());
    }

    @Test
    void separateZoosAreAllowed() {
        NamedShape far = new NamedShape("Far", ZooShape.of(square(500, 500, 20)));
        assertFalse(validate(square(0, 0, 20), List.of(far)).isPresent());
    }
}
