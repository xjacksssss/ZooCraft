package com.zoocraft.core.choice;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExhibitChoiceTest {
    @Test
    void unseenSpeciesWeighMore() {
        ExhibitOption pigs = new ExhibitOption(Set.of("pig"), 10, false);
        ExhibitOption cows = new ExhibitOption(Set.of("cow"), 10, false);
        Set<String> seen = Set.of("pig");
        assertTrue(ExhibitChoice.weight(cows, seen, false) > ExhibitChoice.weight(pigs, seen, false));
    }

    @Test
    void recentlyVisitedWeighLess() {
        ExhibitOption recent = new ExhibitOption(Set.of("cow"), 10, true);
        ExhibitOption fresh = new ExhibitOption(Set.of("cow"), 10, false);
        assertTrue(ExhibitChoice.weight(fresh, Set.of(), false) > ExhibitChoice.weight(recent, Set.of(), false));
    }

    @Test
    void closerWeighsMore() {
        ExhibitOption near = new ExhibitOption(Set.of("cow"), 5, false);
        ExhibitOption far = new ExhibitOption(Set.of("cow"), 60, false);
        assertTrue(ExhibitChoice.weight(near, Set.of(), false) > ExhibitChoice.weight(far, Set.of(), false));
    }

    @Test
    void enthusiastsCareMoreAboutNewSpecies() {
        ExhibitOption cows = new ExhibitOption(Set.of("cow"), 10, false);
        ExhibitOption pigs = new ExhibitOption(Set.of("pig"), 10, false);
        Set<String> seen = Set.of("pig");
        double generalRatio = ExhibitChoice.weight(cows, seen, false) / ExhibitChoice.weight(pigs, seen, false);
        double enthusiastRatio = ExhibitChoice.weight(cows, seen, true) / ExhibitChoice.weight(pigs, seen, true);
        assertTrue(enthusiastRatio > generalRatio);
    }

    @Test
    void pickUsesRollAcrossWeights() {
        List<ExhibitOption> options = List.of(new ExhibitOption(Set.of("a"), 0, false), new ExhibitOption(Set.of("b"), 0, false));
        assertEquals(0, ExhibitChoice.pick(options, Set.of(), false, 0.0));
        assertEquals(1, ExhibitChoice.pick(options, Set.of(), false, 0.999));
        assertEquals(-1, ExhibitChoice.pick(List.of(), Set.of(), false, 0.5));
    }

    @Test
    void farExhibitsInABigZooAreNotStarved() {
        double scale = ExhibitChoice.routeScale(240, 240);
        assertEquals(60.0, scale);
        ExhibitOption near = new ExhibitOption(Set.of("cow"), 10, false, scale, 0, 0, 0, false);
        ExhibitOption far = new ExhibitOption(Set.of("cow"), 150, false, scale, 0, 0, 0, false);
        double ratio = ExhibitChoice.weight(near, Set.of(), false) / ExhibitChoice.weight(far, Set.of(), false);
        double phaseOne = ExhibitChoice.weight(new ExhibitOption(Set.of("cow"), 10, false), Set.of(), false)
                / ExhibitChoice.weight(new ExhibitOption(Set.of("cow"), 150, false), Set.of(), false);
        assertTrue(ratio <= 3.0 + 1e-9 && phaseOne > 6, "near/far ratio " + ratio + ", Phase 1 " + phaseOne);
        assertEquals(16.0, ExhibitChoice.routeScale(30, 40));
    }

    @Test
    void overViewedExhibitsLoseWeight() {
        ExhibitOption popular = new ExhibitOption(Set.of("cow"), 10, false, 16, 40, 10, 0, false);
        ExhibitOption ignored = new ExhibitOption(Set.of("cow"), 10, false, 16, 0, 10, 0, false);
        assertTrue(ExhibitChoice.weight(ignored, Set.of(), false) > 2 * ExhibitChoice.weight(popular, Set.of(), false));
    }

    @Test
    void aFullExhibitDropsToATenth() {
        ExhibitOption full = new ExhibitOption(Set.of("cow"), 10, false, 16, 0, 0, 1.0, true);
        ExhibitOption empty = new ExhibitOption(Set.of("cow"), 10, false, 16, 0, 0, 0, false);
        assertEquals(0.1, ExhibitChoice.weight(full, Set.of(), false) / ExhibitChoice.weight(empty, Set.of(), false), 1e-9);
        ExhibitOption half = new ExhibitOption(Set.of("cow"), 10, false, 16, 0, 0, 0.5, false);
        assertEquals(0.75, ExhibitChoice.weight(half, Set.of(), false) / ExhibitChoice.weight(empty, Set.of(), false), 1e-9);
    }
}
