package com.zoocraft.core.exhibit;

import java.util.UUID;

/** An animal's identity, species and position at census time. */
public record AnimalPoint(UUID id, String species, double x, double y, double z) {
}
