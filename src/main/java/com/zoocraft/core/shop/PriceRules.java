package com.zoocraft.core.shop;

import com.zoocraft.core.visitor.VisitorProfile;

/** Shop price limits and what visitors are willing to pay (spec section 7.3). */
public final class PriceRules {
    public static final int MIN_PRICE = 1;
    public static final int MAX_PRICE = 999;

    private PriceRules() {
    }

    public static boolean valid(int price) {
        return price >= MIN_PRICE && price <= MAX_PRICE;
    }

    public static double profileShopFactor(VisitorProfile profile) {
        return switch (profile) {
            case BUDGET_CONSCIOUS -> 0.8;
            case ANIMAL_ENTHUSIAST -> 1.0;
            case GENERAL -> 1.2;
        };
    }

    /** The highest price this visitor considers reasonable for an item whose category default is {@code categoryDefault}. */
    public static double maxAcceptable(int categoryDefault, VisitorProfile profile, double personalFactor, double reputation) {
        return categoryDefault * profileShopFactor(profile) * personalFactor * (0.5 + reputation / 100.0);
    }

    public static boolean accepts(int price, int categoryDefault, VisitorProfile profile, double personalFactor, double reputation) {
        return price <= maxAcceptable(categoryDefault, profile, personalFactor, reputation);
    }
}
