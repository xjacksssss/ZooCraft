package com.zoocraft.core.geometry;

/** Configurable limits on zoo boundary shapes. Areas are in square blocks. */
public record BoundaryLimits(int minArea, int maxArea, int maxVertices) {
}
