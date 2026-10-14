package com.zoocraft.core.needs;

import com.zoocraft.core.shop.PriceRules;
import com.zoocraft.core.shop.ProductCategory;
import com.zoocraft.core.visitor.VisitorProfile;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** What a visitor buys, or why it buys nothing (Phase 2 spec section 8.3). */
public final class ProductChoice {
    public enum Outcome { CHOSEN, NOTHING_STOCKED, TOO_EXPENSIVE, CANT_AFFORD }

    public record Result(Outcome outcome, Optional<Offer> offer) {
        static Result none(Outcome outcome) {
            return new Result(outcome, Optional.empty());
        }
    }

    private ProductChoice() {
    }

    /**
     * Drops offers outside the wanted categories, then those over budget, then those over the visitor's acceptable
     * price, and picks among the rest at random, weighted towards nearer shops and, for budget-conscious visitors,
     * cheaper items.
     *
     * @param random a number in [0, 1)
     */
    public static Result choose(List<Offer> offers, Set<ProductCategory> wanted, int budget, VisitorProfile profile,
                                double personalFactor, double reputation, double random) {
        List<Offer> stocked = offers.stream().filter(o -> wanted.contains(o.category())).toList();
        if (stocked.isEmpty()) {
            return Result.none(Outcome.NOTHING_STOCKED);
        }
        List<Offer> affordable = stocked.stream().filter(o -> o.price() <= budget).toList();
        if (affordable.isEmpty()) {
            return Result.none(Outcome.CANT_AFFORD);
        }
        List<Offer> acceptable = affordable.stream()
                .filter(o -> PriceRules.accepts(o.price(), o.categoryDefault(), profile, personalFactor, reputation)).toList();
        if (acceptable.isEmpty()) {
            return Result.none(Outcome.TOO_EXPENSIVE);
        }
        double[] weights = new double[acceptable.size()];
        double total = 0;
        for (int i = 0; i < acceptable.size(); i++) {
            Offer o = acceptable.get(i);
            double weight = 1.0 / (1.0 + o.distance() / 16.0);
            if (profile == VisitorProfile.BUDGET_CONSCIOUS) {
                weight /= o.price();
            }
            weights[i] = weight;
            total += weight;
        }
        double pick = random * total;
        for (int i = 0; i < weights.length; i++) {
            pick -= weights[i];
            if (pick < 0) {
                return new Result(Outcome.CHOSEN, Optional.of(acceptable.get(i)));
            }
        }
        return new Result(Outcome.CHOSEN, Optional.of(acceptable.getLast()));
    }
}
