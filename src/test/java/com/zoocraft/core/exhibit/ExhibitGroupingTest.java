package com.zoocraft.core.exhibit;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ExhibitGroupingTest {
    private static AnimalPoint at(double x, double z) {
        return new AnimalPoint(UUID.randomUUID(), "minecraft:pig", x, 64, z);
    }

    @Test
    void nearbyAnimalsFormOneExhibitAndFarOnesAnother() {
        List<List<AnimalPoint>> groups = ExhibitGrouping.group(List.of(at(0, 0), at(5, 0), at(30, 30), at(33, 30)), 8);
        assertEquals(2, groups.size());
        assertEquals(2, groups.get(0).size());
        assertEquals(2, groups.get(1).size());
    }

    @Test
    void chainsLinkThroughMiddleAnimals() {
        List<List<AnimalPoint>> groups = ExhibitGrouping.group(List.of(at(0, 0), at(7, 0), at(14, 0)), 8);
        assertEquals(1, groups.size());
    }

    @Test
    void emptyInputGivesNoExhibits() {
        assertEquals(0, ExhibitGrouping.group(List.of(), 8).size());
    }

    @Test
    void matchFollowsMostOverlappingPreviousGroup() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
        List<Set<UUID>> previous = List.of(Set.of(a, b), Set.of(c));
        List<Set<UUID>> current = List.of(Set.of(c, d), Set.of(a), Set.of(UUID.randomUUID()));
        assertArrayEquals(new int[]{1, 0, -1}, ExhibitGrouping.match(previous, current));
    }

    @Test
    void eachPreviousGroupIsMatchedAtMostOnce() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        List<Set<UUID>> previous = List.of(Set.of(a, b));
        List<Set<UUID>> current = List.of(Set.of(a), Set.of(b));
        int[] match = ExhibitGrouping.match(previous, current);
        assertEquals(1, (match[0] == 0 ? 1 : 0) + (match[1] == 0 ? 1 : 0));
    }
}
