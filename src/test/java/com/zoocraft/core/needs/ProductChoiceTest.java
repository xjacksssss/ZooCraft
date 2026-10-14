package com.zoocraft.core.needs;

import com.zoocraft.core.shop.ProductCategory;
import com.zoocraft.core.visitor.VisitorProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductChoiceTest {
    private static final UUID SHOP = UUID.randomUUID();
    private static final Set<ProductCategory> REFRESHMENTS = Set.of(ProductCategory.FOOD, ProductCategory.DRINK);

    private static Offer offer(String item, ProductCategory category, int price, int categoryDefault, double distance) {
        return new Offer(SHOP, item, category, price, categoryDefault, distance);
    }

    private static ProductChoice.Result choose(List<Offer> offers, int budget, VisitorProfile profile, double random) {
        return ProductChoice.choose(offers, REFRESHMENTS, budget, profile, 1.0, 50, random);
    }

    @Test
    void noOffersMeansNothingStocked() {
        assertEquals(ProductChoice.Outcome.NOTHING_STOCKED, choose(List.of(), 10, VisitorProfile.GENERAL, 0).outcome());
    }

    @Test
    void wrongCategoryCountsAsNothingStocked() {
        List<Offer> offers = List.of(offer("minecraft:poppy", ProductCategory.SOUVENIR, 1, 3, 1));
        assertEquals(ProductChoice.Outcome.NOTHING_STOCKED, choose(offers, 10, VisitorProfile.GENERAL, 0).outcome());
    }

    @Test
    void everythingOverBudgetMeansCantAfford() {
        List<Offer> offers = List.of(offer("minecraft:bread", ProductCategory.FOOD, 5, 2, 1));
        assertEquals(ProductChoice.Outcome.CANT_AFFORD, choose(offers, 4, VisitorProfile.GENERAL, 0).outcome());
    }

    @Test
    void affordableButOverpricedMeansTooExpensive() {
        List<Offer> offers = List.of(offer("minecraft:bread", ProductCategory.FOOD, 9, 2, 1));
        assertEquals(ProductChoice.Outcome.TOO_EXPENSIVE, choose(offers, 20, VisitorProfile.GENERAL, 0).outcome());
    }

    @Test
    void acceptableOfferIsChosen() {
        Offer bread = offer("minecraft:bread", ProductCategory.FOOD, 2, 2, 3);
        ProductChoice.Result result = choose(List.of(bread), 10, VisitorProfile.GENERAL, 0.5);
        assertEquals(ProductChoice.Outcome.CHOSEN, result.outcome());
        assertEquals(bread, result.offer().orElseThrow());
    }

    @Test
    void budgetVisitorsLeanTowardsCheaperItems() {
        // A category default of 3 makes both prices acceptable to a budget visitor (limit 2.4), so only the weighting decides.
        Offer cheap = offer("minecraft:apple", ProductCategory.FOOD, 1, 3, 4);
        Offer dear = offer("minecraft:bread", ProductCategory.FOOD, 2, 3, 4);
        int cheapPicks = 0;
        for (int i = 0; i < 100; i++) {
            double random = (i + 0.5) / 100;
            if (choose(List.of(dear, cheap), 10, VisitorProfile.BUDGET_CONSCIOUS, random).offer().orElseThrow() == cheap) {
                cheapPicks++;
            }
        }
        // Weighted 1/1 against 1/2: about two thirds should be the cheap item.
        assertEquals(67, cheapPicks, 2);
    }
}
