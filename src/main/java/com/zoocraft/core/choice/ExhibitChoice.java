package com.zoocraft.core.choice;

import java.util.List;
import java.util.Set;

/** Weighted choice of the next exhibit (spec section 8.5). */
public final class ExhibitChoice {
    private ExhibitChoice() {
    }

    public static double weight(ExhibitOption option, Set<String> seen, boolean enthusiast) {
        long unseen = option.species().stream().filter(s -> !seen.contains(s)).count();
        double weight = 1.0 + unseen * (enthusiast ? 6.0 : 3.0);
        if (option.recentlyVisited()) {
            weight *= 0.2;
        }
        return weight / (1.0 + option.distance() / 16.0);
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
