package com.zoocraft.core.shop;

import com.zoocraft.core.visitor.VisitorProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceRulesTest {
    @Test
    void pricesRunFromOneTo999() {
        assertFalse(PriceRules.valid(0));
        assertTrue(PriceRules.valid(1));
        assertTrue(PriceRules.valid(999));
        assertFalse(PriceRules.valid(1000));
    }

    @Test
    void generalVisitorAtReputation50AcceptsUpTo1Point2TimesTheDefault() {
        assertEquals(2.4, PriceRules.maxAcceptable(2, VisitorProfile.GENERAL, 1.0, 50), 1e-9);
        assertTrue(PriceRules.accepts(2, 2, VisitorProfile.GENERAL, 1.0, 50));
        assertFalse(PriceRules.accepts(3, 2, VisitorProfile.GENERAL, 1.0, 50));
    }

    @Test
    void budgetVisitorsAreStricterThanGeneralVisitors() {
        double budget = PriceRules.maxAcceptable(3, VisitorProfile.BUDGET_CONSCIOUS, 1.0, 50);
        double general = PriceRules.maxAcceptable(3, VisitorProfile.GENERAL, 1.0, 50);
        assertTrue(budget < general);
        assertFalse(PriceRules.accepts(3, 3, VisitorProfile.BUDGET_CONSCIOUS, 1.0, 50));
    }

    @Test
    void limitIsRoundedToTheNearestEmerald() {
        // 2 × 1.0 × 0.95 × 1.0 = 1.9: close enough to the default price of 2 to pay it.
        assertTrue(PriceRules.accepts(2, 2, VisitorProfile.ANIMAL_ENTHUSIAST, 0.95, 50));
        // 2 × 0.8 × 0.7 × 1.0 = 1.12: a thrifty budget visitor won't pay 2.
        assertFalse(PriceRules.accepts(2, 2, VisitorProfile.BUDGET_CONSCIOUS, 0.7, 50));
    }

    @Test
    void oneEmeraldIsAlwaysAcceptable() {
        assertTrue(PriceRules.accepts(1, 1, VisitorProfile.BUDGET_CONSCIOUS, 0.7, 0));
    }

    @Test
    void reputationRaisesWillingnessToPay() {
        assertTrue(PriceRules.maxAcceptable(2, VisitorProfile.ANIMAL_ENTHUSIAST, 1.0, 90)
                > PriceRules.maxAcceptable(2, VisitorProfile.ANIMAL_ENTHUSIAST, 1.0, 10));
    }
}
