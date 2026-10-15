package com.zoocraft.core.litter;

/**
 * Keeps litter from piling up without limit (Phase 2 spec section 9.3): below the caps a dropped piece becomes new
 * litter; at a cap it makes the nearest small pile bigger instead, or doesn't happen at all.
 */
public final class LitterRules {
    public static final int MAX_PILE = 4;
    public static final double MERGE_DISTANCE = 8;

    public enum Outcome { SPAWN, GROW, NONE }

    private LitterRules() {
    }

    /** @param nearestPile pile size of the nearest litter within {@link #MERGE_DISTANCE}, or 0 when there is none */
    public static Outcome decide(int zooCount, int zooCap, int serverCount, int serverCap, int nearestPile) {
        if (zooCount < zooCap && serverCount < serverCap) {
            return Outcome.SPAWN;
        }
        return nearestPile > 0 && nearestPile < MAX_PILE ? Outcome.GROW : Outcome.NONE;
    }
}
