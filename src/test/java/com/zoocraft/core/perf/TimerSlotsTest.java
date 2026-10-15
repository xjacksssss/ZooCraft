package com.zoocraft.core.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimerSlotsTest {
    private static final int A = 0, B = 1, TOTAL = 2;

    @Test
    void averagesPerTickOverTheWindow() {
        TimerSlots t = new TimerSlots(3, 4);
        t.add(A, 100);
        t.add(A, 100);
        t.endTick();
        t.endTick(); // a tick with nothing in it
        assertEquals(2, t.ticks());
        assertEquals(100.0, t.averageNanos(A), 1e-9);
        assertEquals(200, t.worstNanos(A));
        assertEquals(2, t.calls(A));
    }

    @Test
    void oldTicksFallOutOfTheWindow() {
        TimerSlots t = new TimerSlots(3, 2);
        t.add(A, 1000);
        t.endTick();
        t.add(A, 10);
        t.endTick();
        t.add(A, 20);
        t.endTick();
        assertEquals(2, t.ticks());
        assertEquals(15.0, t.averageNanos(A), 1e-9);
        assertEquals(20, t.worstNanos(A));
        assertEquals(2, t.calls(A));
    }

    @Test
    void recentAverageUsesOnlyTheLastTicks() {
        TimerSlots t = new TimerSlots(3, 10);
        for (int i = 0; i < 5; i++) {
            t.add(A, 100);
            t.endTick();
        }
        t.add(A, 400);
        t.endTick();
        assertEquals(250.0, t.averageNanos(A, 2), 1e-9);
        assertEquals(400.0, t.averageNanos(A, 1), 1e-9);
        assertEquals(150.0, t.averageNanos(A, 100), 1e-9); // more than recorded: uses what there is
    }

    @Test
    void nestedTimeCountsOnceInTheTotal() {
        TimerSlots t = new TimerSlots(3, 4);
        t.open();          // outer A
        t.open();          // inner B
        t.close(B, 30, TOTAL);
        t.close(A, 100, TOTAL);
        t.endTick();
        assertEquals(100.0, t.averageNanos(A), 1e-9);
        assertEquals(30.0, t.averageNanos(B), 1e-9);
        assertEquals(100.0, t.averageNanos(TOTAL), 1e-9);
        assertEquals(0, t.depth());
    }

    @Test
    void closingMoreThanOpenedNeverGoesNegative() {
        TimerSlots t = new TimerSlots(3, 4);
        t.close(A, 5, TOTAL);
        assertEquals(0, t.depth());
        t.open();
        t.close(A, 7, TOTAL);
        t.endTick();
        assertEquals(12.0, t.averageNanos(A), 1e-9);
        assertEquals(12.0, t.averageNanos(TOTAL), 1e-9);
    }

    @Test
    void emptyTimersReportZero() {
        TimerSlots t = new TimerSlots(3, 4);
        assertEquals(0.0, t.averageNanos(A), 1e-9);
        assertEquals(0, t.worstNanos(A));
        assertEquals(0, t.calls(A));
    }
}
