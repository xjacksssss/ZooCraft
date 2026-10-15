package com.zoocraft.core.exhibit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExhibitRatingTest {
    private static ExhibitRating.Input input(int spots, int reached, boolean viewed, int attempts, int species, boolean open, int floor,
                                             double animals, double natural, boolean water) {
        return new ExhibitRating.Input(spots, reached, viewed, attempts, species, open, floor, animals, natural, water);
    }

    @Test
    void aGoodPenScoresExcellent() {
        ExhibitRating.Result r = ExhibitRating.rate(input(12, 12, true, 20, 3, false, 64, 2, 1.0, true));
        assertEquals(100, r.score());
        assertEquals(ExhibitRating.Grade.EXCELLENT, r.grade());
        assertTrue(r.warnings().isEmpty());
    }

    @Test
    void varietySteps() {
        assertEquals(0.6, ExhibitRating.variety(1), 1e-9);
        assertEquals(0.85, ExhibitRating.variety(2), 1e-9);
        assertEquals(1.0, ExhibitRating.variety(5), 1e-9);
    }

    @Test
    void spaceIsFullAtTwelveAndHalfAtSix() {
        assertEquals(1.0, ExhibitRating.space(false, 24, 2), 1e-9);
        assertEquals(0.5, ExhibitRating.space(false, 12, 2), 1e-9);
        assertEquals(1.0, ExhibitRating.space(true, 0, 5), 1e-9, "open exhibits get full marks");
    }

    @Test
    void crampedBelowSixPerAnimal() {
        assertTrue(ExhibitRating.rate(input(4, 4, true, 20, 1, false, 9, 4, 1.0, false)).warnings().contains(ExhibitRating.Warning.CRAMPED));
        assertFalse(ExhibitRating.rate(input(4, 4, true, 20, 1, false, 12, 2, 1.0, false)).warnings().contains(ExhibitRating.Warning.CRAMPED));
        assertFalse(ExhibitRating.rate(input(4, 4, true, 20, 1, true, 9, 4, 1.0, false)).warnings().contains(ExhibitRating.Warning.CRAMPED),
                "open exhibits are never cramped");
    }

    @Test
    void bareGroundBelowAQuarterNatural() {
        assertTrue(ExhibitRating.rate(input(4, 4, true, 20, 1, false, 64, 2, 0.2, false)).warnings().contains(ExhibitRating.Warning.BARE));
        assertFalse(ExhibitRating.rate(input(4, 4, true, 20, 1, false, 64, 2, 0.2, true)).warnings().contains(ExhibitRating.Warning.BARE),
                "water or leaves lift the surroundings above the bare line");
    }

    @Test
    void cantSeeOnlyAfterTenAttempts() {
        assertFalse(ExhibitRating.rate(input(8, 0, false, 9, 1, false, 64, 2, 1, false)).warnings().contains(ExhibitRating.Warning.CANT_SEE));
        assertTrue(ExhibitRating.rate(input(8, 1, false, 10, 1, false, 64, 2, 1, false)).warnings().contains(ExhibitRating.Warning.CANT_SEE));
    }

    @Test
    void noAttemptsYetMeansNoAccessPenalty() {
        assertEquals(1.0, ExhibitRating.access(8, 0, false, 0), 1e-9);
        assertEquals(0.0, ExhibitRating.access(8, 8, false, 10), 1e-9, "reached but nothing seen: visitors can't get close enough to see");
        assertEquals(0.5, ExhibitRating.access(8, 4, true, 10), 1e-9);
        assertEquals(0.0, ExhibitRating.access(0, 0, false, 10), 1e-9);
    }

    @Test
    void gradesFollowTheScore() {
        assertEquals(ExhibitRating.Grade.EXCELLENT, ExhibitRating.grade(80));
        assertEquals(ExhibitRating.Grade.GOOD, ExhibitRating.grade(60));
        assertEquals(ExhibitRating.Grade.FAIR, ExhibitRating.grade(59));
        assertEquals(ExhibitRating.Grade.POOR, ExhibitRating.grade(39));
    }
}
