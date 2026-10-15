package com.zoocraft.core.choice;

import java.util.List;

/** Which viewing spot a visitor takes (Phase 3 spec section 7.2): the least occupied, then path blocks, then the nearest. */
public final class SpotChoice {
    /** @param distance from the visitor, in blocks */
    public record Spot(int occupied, int capacity, boolean path, boolean unreachable, double distance) {
    }

    private SpotChoice() {
    }

    public static boolean full(Spot spot) {
        return spot.occupied() >= spot.capacity();
    }

    /**
     * Full spots are still ranked, so a visitor can head for the least crowded one and wait there.
     *
     * @return the chosen index, or -1 when every spot is unreachable
     */
    public static int pick(List<Spot> spots) {
        int best = -1;
        for (int i = 0; i < spots.size(); i++) {
            Spot spot = spots.get(i);
            if (!spot.unreachable() && (best < 0 || better(spot, spots.get(best)))) {
                best = i;
            }
        }
        return best;
    }

    private static boolean better(Spot a, Spot b) {
        if (a.occupied() != b.occupied()) {
            return a.occupied() < b.occupied();
        }
        if (a.path() != b.path()) {
            return a.path();
        }
        return a.distance() < b.distance();
    }
}
