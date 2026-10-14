package com.zoocraft.core;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JtsSmokeTest {
    @Test
    void squareContainsInteriorPoint() {
        GeometryFactory factory = new GeometryFactory();
        Polygon square = factory.createPolygon(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(4, 0), new Coordinate(4, 4),
                new Coordinate(0, 4), new Coordinate(0, 0)});
        assertTrue(square.isValid());
        assertTrue(square.contains(factory.createPoint(new Coordinate(1, 1))));
    }
}
