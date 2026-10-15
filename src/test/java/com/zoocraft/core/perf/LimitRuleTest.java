package com.zoocraft.core.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LimitRuleTest {
    @Test
    void usesTheWorseOfTheTwoMeasurements() {
        // Phase 3 spec section 3.2: 9.1 ms empty, 17.5 ms at 100, 29.8 ms at 200.
        assertEquals(0.1035, LimitRule.costPerVisitor(9.1, 17.5, 29.8), 1e-9);
    }

    @Test
    void todaysNumbersGiveFortyFive() {
        assertEquals(45, LimitRule.serverDefault(0.1035));
        assertEquals(20, LimitRule.perZooDefault(45));
    }

    @Test
    void theOptimisationTargetGivesOneHundredAndTwentyFive() {
        assertEquals(125, LimitRule.serverDefault(0.04));
        assertEquals(60, LimitRule.perZooDefault(125));
    }

    @Test
    void perZooDefaultIsAtLeastTwelve() {
        assertEquals(12, LimitRule.perZooDefault(10));
    }

    @Test
    void freeOrNegativeCostsHitTheCeilings() {
        assertEquals(LimitRule.MAX_TOTAL / 2, LimitRule.serverDefault(0.0));
        assertEquals(LimitRule.MAX_TOTAL / 2, LimitRule.serverDefault(-1));
        assertEquals(LimitRule.MAX_PER_ZOO, LimitRule.perZooDefault(LimitRule.MAX_TOTAL * 2));
    }
}
