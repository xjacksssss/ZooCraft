package com.zoocraft.core.choice;

import java.util.Set;

/** One exhibit a visitor could go to next. */
public record ExhibitOption(Set<String> species, double distance, boolean recentlyVisited) {
}
