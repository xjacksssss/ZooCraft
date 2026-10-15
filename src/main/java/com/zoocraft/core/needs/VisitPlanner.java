package com.zoocraft.core.needs;

import java.util.ArrayList;
import java.util.List;

/** Chooses an exploring visitor's next errand, in the priority order of Phase 2 spec section 8.2. */
public final class VisitPlanner {
    private VisitPlanner() {
    }

    public static Errand next(PlannerInput in) {
        if (in.leaving()) {
            return Errand.LEAVE;
        }
        if (in.needsToilet() && in.toiletAvailable() && !in.toiletOnCooldown()) {
            return Errand.TOILET;
        }
        if (in.hungry() && in.foodAvailable() && !in.foodOnCooldown()) {
            return Errand.FOOD;
        }
        if (in.hasRubbish() && in.binAvailable()) {
            return Errand.BIN;
        }
        if (in.endingByChoice()) {
            return in.souvenirWish() && in.souvenirAvailable() && !in.lastStopDone() ? Errand.SOUVENIR : Errand.LEAVE;
        }
        return Errand.EXHIBIT;
    }

    /** Needs over the threshold that nothing in the zoo can meet. These become complaints. */
    public static List<Need> unmet(PlannerInput in) {
        List<Need> unmet = new ArrayList<>();
        if (in.hungry() && !in.foodAvailable() && !in.foodOnCooldown()) {
            unmet.add(Need.HUNGER);
        }
        if (in.needsToilet() && !in.toiletAvailable() && !in.toiletOnCooldown()) {
            unmet.add(Need.TOILET);
        }
        return unmet;
    }
}
