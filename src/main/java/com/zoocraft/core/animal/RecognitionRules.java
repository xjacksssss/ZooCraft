package com.zoocraft.core.animal;

import java.util.Set;

/** Which entities count as exhibit animals (spec section 8.1). Being inside the zoo is checked separately. */
public final class RecognitionRules {
    private static final Set<String> ANIMAL_CATEGORIES = Set.of(
            "creature", "water_creature", "water_ambient", "underground_water_creature", "axolotls");

    private RecognitionRules() {
    }

    public static boolean isExhibitAnimal(RecognitionFacts facts) {
        if (facts.excludeTagged() || facts.boss()) {
            return false;
        }
        if (facts.includeTagged()) {
            return true;
        }
        if ("monster".equals(facts.spawnCategory())) {
            return false;
        }
        return facts.animalClass() || facts.waterAnimalClass() || ANIMAL_CATEGORIES.contains(facts.spawnCategory());
    }
}
