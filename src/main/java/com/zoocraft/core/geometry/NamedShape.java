package com.zoocraft.core.geometry;

/** An existing zoo's shape, named so overlap messages can say which zoo is in the way. */
public record NamedShape(String name, ZooShape shape) {
}
