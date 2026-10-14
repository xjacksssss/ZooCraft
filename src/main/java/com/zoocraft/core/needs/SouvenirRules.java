package com.zoocraft.core.needs;

import com.zoocraft.core.visitor.VisitorProfile;

/** How likely a visitor is to want a souvenir on the way out (Phase 2 spec section 8.3). */
public final class SouvenirRules {
    /** Animal enthusiasts who have seen at least this many species are keener on a souvenir. */
    public static final int KEEN_SPECIES = 5;

    private SouvenirRules() {
    }

    public static double chance(VisitorProfile profile, int speciesSeen) {
        return switch (profile) {
            case BUDGET_CONSCIOUS -> 0.10;
            case GENERAL -> 0.35;
            case ANIMAL_ENTHUSIAST -> speciesSeen >= KEEN_SPECIES ? 0.35 : 0.25;
        };
    }
}
