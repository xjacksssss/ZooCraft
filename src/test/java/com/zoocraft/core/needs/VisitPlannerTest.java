package com.zoocraft.core.needs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisitPlannerTest {
    /** A builder-ish helper: everything off unless set. */
    private record In(boolean leaving, boolean hungry, boolean foodAvailable, boolean needsToilet, boolean toiletAvailable,
                      boolean foodOnCooldown, boolean toiletOnCooldown, boolean endingByChoice, boolean souvenirWish,
                      boolean souvenirAvailable, boolean lastStopDone) {
        static In none() {
            return new In(false, false, false, false, false, false, false, false, false, false, false);
        }

        In hungry(boolean available) {
            return new In(leaving, true, available, needsToilet, toiletAvailable, foodOnCooldown, toiletOnCooldown, endingByChoice, souvenirWish, souvenirAvailable, lastStopDone);
        }

        In toilet(boolean available) {
            return new In(leaving, hungry, foodAvailable, true, available, foodOnCooldown, toiletOnCooldown, endingByChoice, souvenirWish, souvenirAvailable, lastStopDone);
        }

        In foodCooldown() {
            return new In(leaving, hungry, foodAvailable, needsToilet, toiletAvailable, true, toiletOnCooldown, endingByChoice, souvenirWish, souvenirAvailable, lastStopDone);
        }

        In ending(boolean wish, boolean available, boolean done) {
            return new In(leaving, hungry, foodAvailable, needsToilet, toiletAvailable, foodOnCooldown, toiletOnCooldown, true, wish, available, done);
        }

        In startLeaving() {
            return new In(true, hungry, foodAvailable, needsToilet, toiletAvailable, foodOnCooldown, toiletOnCooldown, endingByChoice, souvenirWish, souvenirAvailable, lastStopDone);
        }

        PlannerInput build() {
            return new PlannerInput(leaving, hungry, foodAvailable, needsToilet, toiletAvailable, foodOnCooldown, toiletOnCooldown,
                    endingByChoice, souvenirWish, souvenirAvailable, lastStopDone);
        }
    }

    @Test
    void nothingNeededMeansExhibits() {
        assertEquals(Errand.EXHIBIT, VisitPlanner.next(In.none().build()));
    }

    @Test
    void toiletBeatsFood() {
        assertEquals(Errand.TOILET, VisitPlanner.next(In.none().hungry(true).toilet(true).build()));
    }

    @Test
    void foodBeatsExhibits() {
        assertEquals(Errand.FOOD, VisitPlanner.next(In.none().hungry(true).build()));
    }

    @Test
    void cooldownSuppressesAnErrand() {
        assertEquals(Errand.EXHIBIT, VisitPlanner.next(In.none().hungry(true).foodCooldown().build()));
    }

    @Test
    void leavingAlwaysLeaves() {
        assertEquals(Errand.LEAVE, VisitPlanner.next(In.none().hungry(true).toilet(true).startLeaving().build()));
    }

    @Test
    void endingByChoiceStopsForASouvenirOnlyWithWishStockAndNoLastStop() {
        assertEquals(Errand.SOUVENIR, VisitPlanner.next(In.none().ending(true, true, false).build()));
        assertEquals(Errand.LEAVE, VisitPlanner.next(In.none().ending(false, true, false).build()));
        assertEquals(Errand.LEAVE, VisitPlanner.next(In.none().ending(true, false, false).build()));
        assertEquals(Errand.LEAVE, VisitPlanner.next(In.none().ending(true, true, true).build()));
    }

    @Test
    void unmetListsNeedsWithNothingToMeetThem() {
        assertEquals(List.of(Need.HUNGER), VisitPlanner.unmet(In.none().hungry(false).build()));
        assertEquals(List.of(Need.TOILET), VisitPlanner.unmet(In.none().toilet(false).build()));
        assertEquals(List.of(), VisitPlanner.unmet(In.none().hungry(true).build()));
    }

    @Test
    void cooldownIsNotAnUnmetNeed() {
        assertEquals(List.of(), VisitPlanner.unmet(In.none().hungry(true).foodCooldown().build()));
    }
}
