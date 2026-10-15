package com.zoocraft.core.choice;

import java.util.List;
import java.util.Set;

/** Weighted choice of the next exhibit (Phase 1 spec section 8.5, Phase 3 spec section 8.3). */
public final class ExhibitChoice {
    private ExhibitChoice() {
    }

    public static final double MIN_ROUTE_SCALE = 16;

    /** A quarter of the zoo's larger dimension, at least 16 blocks, so far exhibits in big parks aren't starved. */
    public static double routeScale(int width, int depth) {
        return Math.max(MIN_ROUTE_SCALE, Math.max(width, depth) / 4.0);
    }

    /** base × unseenBonus × recentPenalty × distanceFactor × popularityFactor × crowdFactor (Phase 3 spec section 8.3). */
    public static double weight(ExhibitOption option, Set<String> seen, boolean enthusiast) {
        long unseen = option.species().stream().filter(s -> !seen.contains(s)).count();
        double weight = 1.0 + unseen * (enthusiast ? 6.0 : 3.0);
        if (option.recentlyVisited()) {
            weight *= 0.2;
        }
        weight /= 1.0 + option.distance() / option.routeScale();
        weight /= Math.sqrt(1.0 + option.viewsToday() / (option.medianViewsToday() + 1.0));
        weight *= option.full() ? 0.1 : 1.0 - 0.5 * option.occupiedShare();
        return weight;
    }

    /**
     * @param roll a random number in [0, 1)
     * @return the chosen index, or -1 when there are no options
     */
    public static int pick(List<ExhibitOption> options, Set<String> seen, boolean enthusiast, double roll) {
        if (options.isEmpty()) {
            return -1;
        }
        double total = 0;
        double[] weights = new double[options.size()];
        for (int i = 0; i < options.size(); i++) {
            weights[i] = weight(options.get(i), seen, enthusiast);
            total += weights[i];
        }
        double target = roll * total;
        for (int i = 0; i < weights.length; i++) {
            target -= weights[i];
            if (target < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }
}
