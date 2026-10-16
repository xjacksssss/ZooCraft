package com.zoocraft.core.perf;

/** How default visitor limits are set from measurements (Phase 3 spec section 9.2). */
public final class LimitRule {
    /** Visitors may cost at most this much tick time on the reference machine before halving. */
    public static final double BUDGET_MS = 10.0;
    public static final int MAX_TOTAL = 2000;
    public static final int MAX_PER_ZOO = 500;
    /** The config defaults: this rule applied to the final Phase 3 measurements (LimitRuleTest checks them). */
    public static final int SHIPPED_TOTAL = 140;
    public static final int SHIPPED_PER_ZOO = 68;

    private LimitRule() {
    }

    /** Milliseconds per tick each visitor adds: the worse of the 100 and 200 visitor measurements. */
    public static double costPerVisitor(double baselineMs, double at100Ms, double at200Ms) {
        return Math.max((at100Ms - baselineMs) / 100.0, (at200Ms - baselineMs) / 200.0);
    }

    /** The largest multiple of 10 visitors within the budget, halved for weaker servers. */
    public static int serverDefault(double costPerVisitorMs) {
        if (costPerVisitorMs <= 0) {
            return MAX_TOTAL / 2;
        }
        int withinBudget = (int) Math.floor(BUDGET_MS / costPerVisitorMs / 10.0) * 10;
        return Math.min(withinBudget, MAX_TOTAL) / 2;
    }

    /** Half the server default, rounded down to a multiple of 4, at least 12. */
    public static int perZooDefault(int serverDefault) {
        int half = (serverDefault / 2) / 4 * 4;
        return Math.min(MAX_PER_ZOO, Math.max(12, half));
    }
}
