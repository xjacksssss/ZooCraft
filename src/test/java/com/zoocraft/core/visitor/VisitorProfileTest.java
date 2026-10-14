package com.zoocraft.core.visitor;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisitorProfileTest {
    @Test
    void budgetsStayWithinRanges() {
        Random random = new Random(1);
        for (VisitorProfile profile : VisitorProfile.values()) {
            for (int i = 0; i < 500; i++) {
                int budget = profile.rollBudget(random);
                assertTrue(budget >= profile.minBudget() && budget <= profile.maxBudget(), profile + " rolled " + budget);
            }
        }
    }

    @Test
    void priceAboveBudgetIsRefused() {
        assertFalse(VisitorProfile.GENERAL.accepts(5, 4, 100, 1.3));
    }

    @Test
    void freeEntryIsAlwaysAccepted() {
        for (VisitorProfile profile : VisitorProfile.values()) {
            assertTrue(profile.accepts(0, 0, 0, 0.7));
        }
    }

    @Test
    void enthusiastsAcceptMoreThanBudgetVisitors() {
        assertTrue(VisitorProfile.ANIMAL_ENTHUSIAST.maxAcceptablePrice(50, 1.0) > VisitorProfile.BUDGET_CONSCIOUS.maxAcceptablePrice(50, 1.0));
    }

    @Test
    void reputationRaisesAcceptablePrice() {
        assertTrue(VisitorProfile.GENERAL.maxAcceptablePrice(90, 1.0) > VisitorProfile.GENERAL.maxAcceptablePrice(20, 1.0));
    }

    @Test
    void defaultPriceIsAcceptableToMostAtStartingReputation() {
        assertTrue(VisitorProfile.BUDGET_CONSCIOUS.accepts(2, 5, 50, 1.0));
        assertTrue(VisitorProfile.GENERAL.accepts(2, 5, 50, 1.0));
    }

    @Test
    void rollCoversEveryProfile() {
        Random random = new Random(7);
        java.util.EnumSet<VisitorProfile> seen = java.util.EnumSet.noneOf(VisitorProfile.class);
        for (int i = 0; i < 200; i++) {
            seen.add(VisitorProfile.roll(random));
        }
        assertTrue(seen.size() == 3);
    }
}
