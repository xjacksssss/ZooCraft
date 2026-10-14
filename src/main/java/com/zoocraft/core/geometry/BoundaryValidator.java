package com.zoocraft.core.geometry;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Checks a proposed boundary against the shape rules in spec section 6.2. */
public final class BoundaryValidator {
    private BoundaryValidator() {
    }

    /** Removes consecutive duplicate corners, including a final corner equal to the first. */
    public static List<Vertex> normalise(List<Vertex> vertices) {
        List<Vertex> result = new ArrayList<>(vertices.size());
        for (Vertex v : vertices) {
            if (result.isEmpty() || !result.getLast().equals(v)) {
                result.add(v);
            }
        }
        while (result.size() > 1 && result.getFirst().equals(result.getLast())) {
            result.removeLast();
        }
        return result;
    }

    /**
     * @param others shapes of other zoos in the same dimension
     * @return the first problem found, or empty when the boundary is acceptable
     */
    public static Optional<BoundaryProblem> validate(List<Vertex> vertices, BoundaryLimits limits, List<NamedShape> others) {
        List<Vertex> corners = normalise(vertices);
        if (corners.size() < 3) {
            return Optional.of(BoundaryProblem.of(BoundaryProblem.Kind.TOO_FEW_CORNERS, 3));
        }
        if (corners.size() > limits.maxVertices()) {
            return Optional.of(BoundaryProblem.of(BoundaryProblem.Kind.TOO_MANY_CORNERS, limits.maxVertices()));
        }
        ZooShape shape = ZooShape.of(corners);
        TopologyValidationError error = new IsValidOp(shape.polygon()).getValidationError();
        if (error != null) {
            Coordinate c = error.getCoordinate();
            Vertex at = c == null ? null : new Vertex((int) Math.floor(c.x), (int) Math.floor(c.y));
            return Optional.of(new BoundaryProblem(BoundaryProblem.Kind.SELF_INTERSECTING, at, null, 0));
        }
        if (shape.area() < 1e-9 || shape.area() < limits.minArea()) {
            return Optional.of(BoundaryProblem.of(BoundaryProblem.Kind.TOO_SMALL, limits.minArea()));
        }
        if (shape.area() > limits.maxArea()) {
            return Optional.of(BoundaryProblem.of(BoundaryProblem.Kind.TOO_LARGE, limits.maxArea()));
        }
        for (NamedShape other : others) {
            if (shape.overlaps(other.shape())) {
                return Optional.of(new BoundaryProblem(BoundaryProblem.Kind.OVERLAPS, null, other.name(), 0));
            }
        }
        return Optional.empty();
    }
}
