package com.zoocraft.core.needs;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeedsTest {
    @Test
    void rolledNeedsStayInTheirRanges() {
        Random random = new Random(7);
        for (int i = 0; i < 1000; i++) {
            Needs needs = Needs.roll(random);
            assertTrue(needs.hunger() >= 0 && needs.hunger() <= 30, "hunger start " + needs.hunger());
            assertTrue(needs.toilet() >= 0 && needs.toilet() <= 20, "toilet start " + needs.toilet());
            assertTrue(needs.hungerRate() >= 0.14 && needs.hungerRate() <= 0.25, "hunger rate " + needs.hungerRate());
            assertTrue(needs.toiletRate() >= 0.11 && needs.toiletRate() <= 0.2, "toilet rate " + needs.toiletRate());
        }
    }

    /** Seconds until hunger reaches 60 from {@code start} at {@code rate}. */
    private static double secondsToHungry(double start, double rate) {
        Needs needs = new Needs(start, 0, rate, 0.11, 0, 0);
        double seconds = 0;
        while (!needs.wants(Need.HUNGER, 60)) {
            needs.tick(0.5);
            seconds += 0.5;
        }
        return seconds;
    }

    @Test
    void visitorsGetHungryAfterAboutFourToSevenMinutes() {
        // Fastest: high start, fast rate. Slowest: zero start, slow rate.
        assertTrue(secondsToHungry(30, 0.25) >= 2 * 60, "far too fast");
        assertTrue(secondsToHungry(0, 0.25) >= 4 * 60 - 1, "fast rate from empty should still take about 4 minutes");
        assertTrue(secondsToHungry(0, 0.14) <= 7.2 * 60, "slow rate from empty should take about 7 minutes");
    }

    @Test
    void urgentAtEightyFive() {
        Needs needs = new Needs(84, 0, 0.2, 0.2, 0, 0);
        assertFalse(needs.urgent(Need.HUNGER));
        needs.set(Need.HUNGER, 85);
        assertTrue(needs.urgent(Need.HUNGER));
    }

    @Test
    void overdueOnlyAfterSixtySecondsFull() {
        Needs needs = new Needs(100, 0, 0.2, 0.2, 0, 0);
        needs.tick(59.5);
        assertFalse(needs.overdue(Need.HUNGER));
        needs.tick(0.5);
        assertTrue(needs.overdue(Need.HUNGER));
        assertFalse(needs.overdue(Need.TOILET));
    }

    @Test
    void foodAndDrinkLowerHungerButNotBelowZero() {
        Needs needs = new Needs(70, 0, 0.2, 0.2, 0, 0);
        needs.eat(false);
        assertEquals(10, needs.hunger(), 1e-9);
        needs.set(Need.HUNGER, 70);
        needs.eat(true);
        assertEquals(35, needs.hunger(), 1e-9);
        needs.eat(false);
        assertEquals(0, needs.hunger(), 1e-9);
    }

    @Test
    void eatingResetsTheFullTimer() {
        Needs needs = new Needs(100, 0, 0.2, 0.2, 0, 0);
        needs.tick(50);
        needs.eat(true);
        needs.set(Need.HUNGER, 100);
        needs.tick(20);
        assertFalse(needs.overdue(Need.HUNGER), "time full before eating must not count");
    }

    @Test
    void toiletResetsToZero() {
        Needs needs = new Needs(0, 95, 0.2, 0.2, 0, 0);
        needs.useToilet();
        assertEquals(0, needs.toilet(), 1e-9);
    }
}
