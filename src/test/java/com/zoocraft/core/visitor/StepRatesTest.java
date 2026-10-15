package com.zoocraft.core.visitor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepRatesTest {
    @Test
    void defaultStepKeepsThePhaseTwoRates() {
        // Before M9: INTEREST_DECAY_PER_STEP = 100 / (6 * 60 * 20 / 10), URGENT_LOSS_PER_STEP = 1 / (10 * 20 / 10).
        assertEquals(100.0 / (6 * 60 * 20 / 10), StepRates.interestDecay(10), 1e-12);
        assertEquals(1.0 / (10 * 20 / 10), StepRates.urgentLoss(10), 1e-12);
        assertEquals(0.5, StepRates.seconds(10), 1e-12);
    }

    @Test
    void ratesPerSecondDoNotDependOnTheStep() {
        for (int step : new int[]{1, 3, 7, 10, 20, 40}) {
            double stepsPerSecond = 20.0 / step;
            assertEquals(100.0 / 360, StepRates.interestDecay(step) * stepsPerSecond, 1e-12, "interest, step " + step);
            assertEquals(0.1, StepRates.urgentLoss(step) * stepsPerSecond, 1e-12, "urgent loss, step " + step);
            assertEquals(1.0, StepRates.seconds(step) * stepsPerSecond, 1e-12, "seconds, step " + step);
        }
    }

    @Test
    void dueWhenTheIntervalHasPassed() {
        assertTrue(StepRates.due(100, 80, 20));
        assertFalse(StepRates.due(99, 80, 20));
        assertTrue(StepRates.due(0, Long.MIN_VALUE / 2, 20));
    }
}
