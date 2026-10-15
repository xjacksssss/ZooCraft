package com.zoocraft.core.litter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LitterRulesTest {
    @Test
    void belowBothCapsSpawnsNewLitter() {
        assertEquals(LitterRules.Outcome.SPAWN, LitterRules.decide(10, 48, 100, 300, 2));
    }

    @Test
    void atTheZooCapANearbyPileGrows() {
        assertEquals(LitterRules.Outcome.GROW, LitterRules.decide(48, 48, 100, 300, 1));
    }

    @Test
    void atTheServerCapANearbyPileGrows() {
        assertEquals(LitterRules.Outcome.GROW, LitterRules.decide(5, 48, 300, 300, 3));
    }

    @Test
    void atACapWithNothingNearbyNothingHappens() {
        assertEquals(LitterRules.Outcome.NONE, LitterRules.decide(48, 48, 100, 300, 0));
    }

    @Test
    void aFullPileCountsAsNothingNearby() {
        assertEquals(LitterRules.Outcome.NONE, LitterRules.decide(48, 48, 100, 300, LitterRules.MAX_PILE));
    }
}
