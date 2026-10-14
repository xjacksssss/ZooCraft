package com.zoocraft.core.visitor;

import java.util.random.RandomGenerator;

/** The three Phase 1 visitor profiles (GDD section 4.2). They change decisions, never access. */
public enum VisitorProfile {
    // Default budgets leave room for shopping (Phase 2 spec section 8.3); servers can change them in the config.
    BUDGET_CONSCIOUS(4, 12, 3.0, 30),
    ANIMAL_ENTHUSIAST(6, 18, 6.0, 30),
    GENERAL(5, 15, 4.0, 40);

    private final int minBudget;
    private final int maxBudget;
    private final double basePrice;
    private final int weight;

    VisitorProfile(int minBudget, int maxBudget, double basePrice, int weight) {
        this.minBudget = minBudget;
        this.maxBudget = maxBudget;
        this.basePrice = basePrice;
        this.weight = weight;
    }

    public int minBudget() {
        return minBudget;
    }

    public int maxBudget() {
        return maxBudget;
    }

    public int rollBudget(RandomGenerator random) {
        return rollBudget(random, minBudget, maxBudget);
    }

    /** A budget between configured limits; a maximum below the minimum is treated as the minimum. */
    public int rollBudget(RandomGenerator random, int min, int max) {
        return min + random.nextInt(Math.max(0, max - min) + 1);
    }

    /** A random personal price tolerance, so visitors with the same profile still differ. */
    public static double rollPersonalFactor(RandomGenerator random) {
        return 0.7 + random.nextDouble() * 0.6;
    }

    /** The highest admission price this visitor considers reasonable at the zoo's reputation (0–100). */
    public double maxAcceptablePrice(double reputation, double personalFactor) {
        return basePrice * (0.5 + reputation / 100.0) * personalFactor;
    }

    public boolean accepts(int price, int budget, double reputation, double personalFactor) {
        if (price <= 0) {
            return true;
        }
        return price <= budget && price <= maxAcceptablePrice(reputation, personalFactor);
    }

    public static VisitorProfile roll(RandomGenerator random) {
        int total = 0;
        for (VisitorProfile profile : values()) {
            total += profile.weight;
        }
        int pick = random.nextInt(total);
        for (VisitorProfile profile : values()) {
            pick -= profile.weight;
            if (pick < 0) {
                return profile;
            }
        }
        return GENERAL;
    }
}
