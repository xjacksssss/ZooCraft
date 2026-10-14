package com.zoocraft.core.rating;

/** Gradual rating changes (spec section 8.7): one visitor can only nudge a zoo's numbers. */
public final class Ratings {
    /** How far zoo satisfaction moves towards each departing visitor's score. */
    public static final double SATISFACTION_WEIGHT = 0.05;
    /** How far reputation moves towards current satisfaction on each departure. */
    public static final double REPUTATION_WEIGHT = 0.01;

    private Ratings() {
    }

    public static double blend(double current, double sample, double weight) {
        return clamp(current + weight * (sample - current));
    }

    public static double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }
}
