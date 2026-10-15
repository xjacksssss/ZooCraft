package com.zoocraft.core.visitor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressTest {
    @Test
    void gettingCloserIsProgress() {
        Progress p = new Progress();
        p.onTarget(0);
        assertTrue(p.update(20, 0));
        assertFalse(p.update(19.8, 100), "less than half a block isn't progress");
        assertEquals(100, p.ticksWithoutProgress(100));
        assertTrue(p.update(19, 120));
        assertEquals(0, p.ticksWithoutProgress(120));
        assertEquals(0, p.stuckTicks(120));
    }

    @Test
    void aFailedRouteRestartsTheRetryClockButNotTheStuckTime() {
        Progress p = new Progress();
        p.onTarget(0);
        p.update(20, 0);
        p.onRouteFailed(600);
        assertEquals(0, p.ticksWithoutProgress(600));
        p.update(20, 700); // the retried route starts from here
        assertEquals(100, p.ticksWithoutProgress(800));
        assertEquals(700, p.stuckTicks(700), "a visitor stuck through retries is still stuck");
        p.onStepAside(800);
        assertEquals(0, p.ticksWithoutProgress(800));
        assertEquals(800, p.stuckTicks(800));
    }

    @Test
    void aNewTargetOrNoTargetResetsEverything() {
        Progress p = new Progress();
        p.onTarget(0);
        p.update(20, 0);
        p.onTarget(500);
        assertEquals(0, p.stuckTicks(500));
        p.clear();
        assertEquals(0, p.stuckTicks(900));
        assertEquals(0, p.ticksWithoutProgress(900));
    }
}
