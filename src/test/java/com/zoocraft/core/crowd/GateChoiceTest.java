package com.zoocraft.core.crowd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GateChoiceTest {
    @Test
    void nearestGateWithoutCrowds() {
        assertEquals(0, GateChoice.pick(new double[]{10, 30}, new int[]{0, 0}));
    }

    @Test
    void aGatesWeightHalvesForEveryEightHeadingToIt() {
        // 10 blocks with 16 heading there counts as 40; 30 blocks with none counts as 30.
        assertEquals(1, GateChoice.pick(new double[]{10, 30}, new int[]{16, 0}));
        assertEquals(0, GateChoice.pick(new double[]{10, 30}, new int[]{8, 0}));
        assertEquals(-1, GateChoice.pick(new double[]{}, new int[]{}));
    }
}
