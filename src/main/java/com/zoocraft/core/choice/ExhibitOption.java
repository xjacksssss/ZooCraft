package com.zoocraft.core.choice;

import java.util.Set;

/**
 * One exhibit a visitor could go to next (Phase 3 spec section 8.3).
 *
 * @param routeScale       distances are measured against this (a quarter of the zoo's larger dimension, at least 16)
 * @param viewsToday       how often visitors have seen this exhibit today
 * @param medianViewsToday the median of that over the zoo's exhibits
 * @param occupiedShare    share of the exhibit's viewing-spot places taken
 * @param full             every spot is full
 */
public record ExhibitOption(Set<String> species, double distance, boolean recentlyVisited, double routeScale, int viewsToday,
                            int medianViewsToday, double occupiedShare, boolean full) {
    /** Phase 1 inputs only: a small zoo with no view history and no crowds. */
    public ExhibitOption(Set<String> species, double distance, boolean recentlyVisited) {
        this(species, distance, recentlyVisited, ExhibitChoice.MIN_ROUTE_SCALE, 0, 0, 0, false);
    }
}
