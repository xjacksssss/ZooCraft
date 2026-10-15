package com.zoocraft.core.choice;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpotChoiceTest {
    @Test
    void picksTheLeastOccupied() {
        assertEquals(1, SpotChoice.pick(List.of(new SpotChoice.Spot(1, 2, false, false, 1), new SpotChoice.Spot(0, 2, false, false, 9))));
    }

    @Test
    void prefersPathsAmongTiesThenTheNearest() {
        List<SpotChoice.Spot> spots = List.of(new SpotChoice.Spot(0, 2, false, false, 1), new SpotChoice.Spot(0, 2, true, false, 8),
                new SpotChoice.Spot(0, 2, true, false, 5));
        assertEquals(2, SpotChoice.pick(spots));
    }

    @Test
    void skipsUnreachableSpotsAndReportsNone() {
        assertEquals(1, SpotChoice.pick(List.of(new SpotChoice.Spot(0, 2, true, true, 1), new SpotChoice.Spot(1, 2, false, false, 1))));
        assertEquals(-1, SpotChoice.pick(List.of(new SpotChoice.Spot(0, 2, true, true, 1))));
        assertEquals(-1, SpotChoice.pick(List.of()));
    }

    @Test
    void fullSpotsAreStillRankedSoTheCallerCanWait() {
        assertEquals(0, SpotChoice.pick(List.of(new SpotChoice.Spot(2, 2, false, false, 1), new SpotChoice.Spot(3, 2, false, false, 1))));
        assertTrue(SpotChoice.full(new SpotChoice.Spot(2, 2, false, false, 1)));
    }
}
