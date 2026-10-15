package com.zoocraft.core.visitor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LookChoiceTest {
    private static LookChoice.Input in(boolean player, boolean allowed, boolean exhibit, boolean walking, boolean passing, boolean leader) {
        return new LookChoice.Input(player, allowed, exhibit, walking, passing, leader);
    }

    @Test
    void aNearbyPlayerOnlyGetsAGlanceOneTimeInTen() {
        assertEquals(LookChoice.Kind.PLAYER, LookChoice.choose(in(true, true, true, false, false, false), 0.05, 0.5));
        assertEquals(LookChoice.Kind.EXHIBIT_ANIMAL, LookChoice.choose(in(true, true, true, false, false, false), 0.15, 0.5));
    }

    @Test
    void noGlanceDuringTheCooldown() {
        assertNotEquals(LookChoice.Kind.PLAYER, LookChoice.choose(in(true, false, false, false, false, false), 0.0, 0.5));
    }

    @Test
    void walkingVisitorsLookAheadOrAtAnimalsTheyPass() {
        assertEquals(LookChoice.Kind.PASSING_ANIMAL, LookChoice.choose(in(false, true, false, true, true, false), 0.5, 0.2));
        assertEquals(LookChoice.Kind.AHEAD, LookChoice.choose(in(false, true, false, true, true, false), 0.5, 0.4));
        assertEquals(LookChoice.Kind.AHEAD, LookChoice.choose(in(false, true, false, true, false, false), 0.5, 0.1));
    }

    @Test
    void groupMembersSometimesLookAtTheLeader() {
        assertEquals(LookChoice.Kind.LEADER, LookChoice.choose(in(false, true, false, false, false, true), 0.5, 0.1));
        assertEquals(LookChoice.Kind.RANDOM, LookChoice.choose(in(false, true, false, false, false, true), 0.5, 0.3));
        assertEquals(LookChoice.Kind.RANDOM, LookChoice.choose(in(false, true, false, false, false, false), 0.5, 0.1));
    }

    @Test
    void durationsAndCooldownMatchTheSpec() {
        assertEquals(20, LookChoice.duration(LookChoice.Kind.PLAYER, 0.0));
        assertEquals(40, LookChoice.duration(LookChoice.Kind.PLAYER, 0.999));
        assertEquals(60, LookChoice.duration(LookChoice.Kind.EXHIBIT_ANIMAL, 0.0));
        assertEquals(120, LookChoice.duration(LookChoice.Kind.EXHIBIT_ANIMAL, 0.999));
        assertEquals(400, LookChoice.playerCooldown(0.0));
        assertEquals(800, LookChoice.playerCooldown(0.999));
    }
}
