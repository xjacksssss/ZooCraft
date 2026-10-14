package com.zoocraft.core.rating;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RatingsTest {
    @Test
    void oneUnhappyVisitorBarelyMovesSatisfaction() {
        double satisfaction = 50;
        for (int i = 0; i < 100; i++) {
            satisfaction = Ratings.blend(satisfaction, 50, Ratings.SATISFACTION_WEIGHT);
        }
        satisfaction = Ratings.blend(satisfaction, 0, Ratings.SATISFACTION_WEIGHT);
        assertTrue(satisfaction > 47, "satisfaction fell to " + satisfaction);
    }

    @Test
    void reputationFollowsMoreSlowlyThanSatisfaction() {
        double satisfaction = Ratings.blend(50, 100, Ratings.SATISFACTION_WEIGHT);
        double reputation = Ratings.blend(50, 100, Ratings.REPUTATION_WEIGHT);
        assertTrue(satisfaction - 50 > reputation - 50);
    }

    @Test
    void recoversAfterImprovements() {
        double satisfaction = 5;
        for (int i = 0; i < 100; i++) {
            satisfaction = Ratings.blend(satisfaction, 90, Ratings.SATISFACTION_WEIGHT);
        }
        assertTrue(satisfaction > 80);
    }

    @Test
    void clampKeepsWithinRange() {
        assertEquals(0, Ratings.clamp(-4));
        assertEquals(100, Ratings.clamp(140));
        assertEquals(42, Ratings.clamp(42));
    }
}
