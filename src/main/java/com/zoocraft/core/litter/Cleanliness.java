package com.zoocraft.core.litter;

/**
 * A zoo's cleanliness, built from what visitors actually see (spec section 9.4): each sample counts the litter near
 * one visitor, and the zoo's rating moves a small step towards it, so it changes gradually and can recover.
 */
public final class Cleanliness {
    public static final double WEIGHT = 0.05;
    public static final double PER_UNIT = 20;

    private Cleanliness() {
    }

    public static double sample(int unitsNearby) {
        return Math.max(0, 100 - PER_UNIT * unitsNearby);
    }

    public static double blend(double current, double sample) {
        return Math.max(0, Math.min(100, current + WEIGHT * (sample - current)));
    }

    /** "clean", "some_litter" or "dirty", for the dashboard. */
    public static String label(double cleanliness) {
        if (cleanliness >= 80) {
            return "clean";
        }
        return cleanliness >= 50 ? "some_litter" : "dirty";
    }
}
