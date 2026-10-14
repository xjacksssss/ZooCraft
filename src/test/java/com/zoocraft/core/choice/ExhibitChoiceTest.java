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
}
