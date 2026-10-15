package com.zoocraft.core.crowd;

/**
 * Which gate a leaving visitor heads for (Phase 3 spec section 7.3): the nearest, with a gate's weight halving for
 * every 8 visitors already heading to it, so one gate doesn't take the whole crowd when the zoo closes.
 */
public final class GateChoice {
    public static final double HALVING = 8.0;

    private GateChoice() {
    }

    /** @return the index of the chosen gate, or -1 when there are none */
    public static int pick(double[] distances, int[] heading) {
        int best = -1;
        double bestCost = Double.MAX_VALUE;
        for (int i = 0; i < distances.length; i++) {
            double cost = distances[i] * Math.pow(2, heading[i] / HALVING);
            if (cost < bestCost) {
                bestCost = cost;
                best = i;
            }
        }
        return best;
    }
}
