package com.zoocraft.core.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductRulesTest {
    /** unsellableTag, spawnEgg, hasContents, drinkTag, foodComponent, foodTag, notVisitorFood */
    private static ProductCategory classify(boolean unsellableTag, boolean spawnEgg, boolean hasContents, boolean drinkTag,
                                            boolean foodComponent, boolean foodTag, boolean notVisitorFood) {
        return ProductRules.classify(new ProductFacts(unsellableTag, spawnEgg, hasContents, drinkTag, foodComponent, foodTag, notVisitorFood));
    }

    @Test
    void taggedUnsellableItemIsNotForSale() {
        assertEquals(ProductCategory.UNSELLABLE, classify(true, false, false, false, false, false, false));
    }

    @Test
    void spawnEggIsNotForSale() {
        assertEquals(ProductCategory.UNSELLABLE, classify(false, true, false, false, false, false, false));
    }

    @Test
    void foodWithContentsIsNotForSale() {
        assertEquals(ProductCategory.UNSELLABLE, classify(false, false, true, false, true, false, false));
    }

    @Test
    void drinkTagMakesADrink() {
        assertEquals(ProductCategory.DRINK, classify(false, false, false, true, true, false, false));
    }

    @Test
    void drinkThatIsNotVisitorFoodIsASouvenir() {
        assertEquals(ProductCategory.SOUVENIR, classify(false, false, false, true, false, false, true));
    }

    @Test
    void moddedFoodWithOnlyTheFoodComponentIsFood() {
        assertEquals(ProductCategory.FOOD, classify(false, false, false, false, true, false, false));
    }

    @Test
    void foodTagMakesFoodWithoutTheComponent() {
        assertEquals(ProductCategory.FOOD, classify(false, false, false, false, false, true, false));
    }

    @Test
    void rottenFleshStyleFoodIsASouvenir() {
        assertEquals(ProductCategory.SOUVENIR, classify(false, false, false, false, true, false, true));
    }

    @Test
    void ordinaryBlockIsASouvenir() {
        assertEquals(ProductCategory.SOUVENIR, classify(false, false, false, false, false, false, false));
    }
}
