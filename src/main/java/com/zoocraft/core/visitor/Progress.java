package com.zoocraft.core.visitor;

/**
 * How a visitor is getting on towards its target. {@link #ticksWithoutProgress} drives the retry rule (P1 7.5) and
 * restarts with each retry or step aside; {@link #stuckTicks} is what {@code /zoo debug perf} reports and only
 * restarts on real progress or a new target, so a visitor stuck through several retries shows its whole time.
 */
public final class Progress {
    /** Getting at least this much closer counts as progress. */
    public static final double STEP = 0.5;

    private boolean active;
    private double routeBest = Double.MAX_VALUE;
    private double overallBest = Double.MAX_VALUE;
    private long lastProgress;
    private long stuckSince;

    /** Heading somewhere new. */
    public void onTarget(long now) {
        active = true;
        routeBest = Double.MAX_VALUE;
        overallBest = Double.MAX_VALUE;
        lastProgress = now;
        stuckSince = now;
    }

    /** Not heading anywhere. */
    public void clear() {
        active = false;
    }

    /** @return true when the visitor got closer than ever on this route */
    public boolean update(double distance, long now) {
        if (distance < overallBest - STEP) {
            overallBest = distance;
            stuckSince = now;
        }
        if (distance < routeBest - STEP) {
            routeBest = distance;
            lastProgress = now;
            return true;
        }
        return false;
    }

    /** A route failed and is being tried afresh: the retry clock restarts, the stuck time doesn't. */
    public void onRouteFailed(long now) {
        routeBest = Double.MAX_VALUE;
        lastProgress = now;
    }

    /** The visitor stepped aside from a crowd and re-plans once. */
    public void onStepAside(long now) {
        lastProgress = now;
    }

    public long ticksWithoutProgress(long now) {
        return active ? now - lastProgress : 0;
    }

    public long stuckTicks(long now) {
        return active ? now - stuckSince : 0;
    }
}
