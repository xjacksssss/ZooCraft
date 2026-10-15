package com.zoocraft.core.visitor;

/** Per-step amounts for a configurable visitor step, so rates per second never change (Phase 3 spec section 6.5). */
public final class StepRates {
    /** Interest falls from 100 to 0 in this many seconds when nothing interesting happens. */
    public static final double INTEREST_FULL_DECAY_SECONDS = 360;
    /** An urgent need costs 1 satisfaction every 10 seconds. */
    private static final double URGENT_LOSS_PER_SECOND = 0.1;

    private StepRates() {
    }

    public static double interestDecay(int stepTicks) {
        return 100.0 / INTEREST_FULL_DECAY_SECONDS * seconds(stepTicks);
    }

    public static double urgentLoss(int stepTicks) {
        return URGENT_LOSS_PER_SECOND * seconds(stepTicks);
    }

    public static double seconds(int stepTicks) {
        return stepTicks / 20.0;
    }

    /**
     * Seconds a periodic check should account for: the real gap since the last one (checks only run on visitor steps,
     * so they can come late), at most two intervals, and one interval for the first check.
     */
    public static double elapsedSeconds(long now, long last, int intervalTicks) {
        if (last <= Long.MIN_VALUE / 4) {
            return intervalTicks / 20.0;
        }
        return Math.min(now - last, 2L * intervalTicks) / 20.0;
    }

    public static boolean due(long now, long last, int intervalTicks) {
        return now - last >= intervalTicks;
    }
}
