package com.zoocraft.core.needs;

import com.zoocraft.core.visitor.VisitorProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SouvenirRulesTest {
    @Test
    void chancesFollowTheSpec() {
        assertEquals(0.10, SouvenirRules.chance(VisitorProfile.BUDGET_CONSCIOUS, 9), 1e-9);
        assertEquals(0.35, SouvenirRules.chance(VisitorProfile.GENERAL, 0), 1e-9);
        assertEquals(0.25, SouvenirRules.chance(VisitorProfile.ANIMAL_ENTHUSIAST, 4), 1e-9);
        assertEquals(0.35, SouvenirRules.chance(VisitorProfile.ANIMAL_ENTHUSIAST, 5), 1e-9);
    }
}
