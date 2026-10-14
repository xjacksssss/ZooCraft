package com.zoocraft.core.shop;

/** Sorts items into shop categories, in the order of spec section 7.2. */
public final class ProductRules {
    private ProductRules() {
    }

    public static ProductCategory classify(ProductFacts f) {
        if (f.unsellableTag() || f.spawnEgg() || f.hasContents()) {
            return ProductCategory.UNSELLABLE;
        }
        if (f.drinkTag() && !f.notVisitorFood()) {
            return ProductCategory.DRINK;
        }
        if ((f.foodComponent() || f.foodTag()) && !f.notVisitorFood()) {
            return ProductCategory.FOOD;
        }
        return ProductCategory.SOUVENIR;
    }
}
