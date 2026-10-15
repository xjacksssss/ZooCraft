package com.zoocraft.core.exhibit;

import java.util.EnumSet;
import java.util.Set;

/**
 * An exhibit's rating from 0 to 100 (Phase 3 spec section 8.5): viewing access 40%, variety 20%, space 25% and
 * surroundings 15%. Generic rules only, so nothing species-specific is invented (GDD 5.3). Grades: Excellent from
 * 80, Good from 60, Fair from 40, Poor below.
 */
public final class ExhibitRating {
    public enum Grade { EXCELLENT, GOOD, FAIR, POOR }

    public enum Warning { CRAMPED, BARE, CANT_SEE }

    /**
     * @param spotsReached    spots a visitor has reached in the last day
     * @param viewedFromSpots a visitor saw the exhibit's animals in the last day
     * @param attempts        visitors who set off for the exhibit in the last day
     * @param open            the enclosure fill hit its cap, so the animals roam open land
     * @param animalWeight    animals, with very large ones counting double
     * @param naturalShare    share of the enclosure floor that is natural ground
     * @param waterOrLeaves   water or leaves inside or next to the enclosure
     */
    public record Input(int spots, int spotsReached, boolean viewedFromSpots, int attempts, int species, boolean open, int floorBlocks,
                        double animalWeight, double naturalShare, boolean waterOrLeaves) {
    }

    public record Result(int score, Grade grade, Set<Warning> warnings) {
        /** Before an exhibit has been rated. */
        public static final Result UNRATED = new Result(100, Grade.EXCELLENT, Set.of());
    }

    /** Floor blocks per animal for full space marks; half marks at half this. */
    public static final double FULL_SPACE = 12;
    public static final double CRAMPED_BELOW = 6;
    public static final double BARE_BELOW = 0.25;
    public static final double CANT_SEE_BELOW = 0.25;
    public static final double WATER_BONUS = 0.25;
    public static final int MIN_ATTEMPTS = 10;

    private ExhibitRating() {
    }

    public static Result rate(Input in) {
        double access = access(in.spots(), in.spotsReached(), in.viewedFromSpots(), in.attempts());
        double surroundings = Math.min(1.0, in.naturalShare() + (in.waterOrLeaves() ? WATER_BONUS : 0));
        double space = space(in.open(), in.floorBlocks(), in.animalWeight());
        int score = (int) Math.round(100 * (0.40 * access + 0.20 * variety(in.species()) + 0.25 * space + 0.15 * surroundings));
        EnumSet<Warning> warnings = EnumSet.noneOf(Warning.class);
        if (!in.open() && in.animalWeight() > 0 && in.floorBlocks() / in.animalWeight() < CRAMPED_BELOW) {
            warnings.add(Warning.CRAMPED);
        }
        if (surroundings < BARE_BELOW) {
            warnings.add(Warning.BARE);
        }
        if (in.attempts() >= MIN_ATTEMPTS && access < CANT_SEE_BELOW) {
            warnings.add(Warning.CANT_SEE);
        }
        return new Result(score, grade(score), Set.copyOf(warnings));
    }

    /**
     * Share of spots reached recently, or nothing when nobody saw an animal from them (visitors got there but couldn't
     * see). Full marks until visitors have tried.
     */
    public static double access(int spots, int reached, boolean viewed, int attempts) {
        if (attempts == 0) {
            return 1.0;
        }
        if (spots == 0) {
            return 0.0;
        }
        return viewed ? Math.min(1.0, (double) reached / spots) : 0.0;
    }

    public static double variety(int species) {
        return species >= 3 ? 1.0 : species == 2 ? 0.85 : 0.6;
    }

    public static double space(boolean open, int floor, double animalWeight) {
        if (open || animalWeight <= 0) {
            return 1.0;
        }
        return Math.min(1.0, floor / animalWeight / FULL_SPACE);
    }

    public static Grade grade(int score) {
        return score >= 80 ? Grade.EXCELLENT : score >= 60 ? Grade.GOOD : score >= 40 ? Grade.FAIR : Grade.POOR;
    }
}
