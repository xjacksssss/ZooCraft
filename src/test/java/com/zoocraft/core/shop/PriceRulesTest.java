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
    void reputationRaisesWillingnessToPay() {
        assertTrue(PriceRules.maxAcceptable(2, VisitorProfile.ANIMAL_ENTHUSIAST, 1.0, 90)
                > PriceRules.maxAcceptable(2, VisitorProfile.ANIMAL_ENTHUSIAST, 1.0, 10));
    }
}
