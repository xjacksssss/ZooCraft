package com.zoocraft.core.animal;

import java.util.Set;

/** Which entities count as exhibit animals (spec section 8.1). Being inside the zoo is checked separately. */
public final class RecognitionRules {
    private static final Set<String> ANIMAL_CATEGORIES = Set.of(
            "creature", "water_creature", "water_ambient", "underground_water_creature", "axolotls");

    private RecognitionRules() {
    }

    public static boolean isExhibitAnimal(RecognitionFacts facts) {
        return decide(facts).recognised();
    }

    /** The same rules as {@link #isExhibitAnimal}, in the same order, naming the one that decided. */
    public static RecognitionVerdict decide(RecognitionFacts facts) {
        if (facts.excludeTagged()) {
            return new RecognitionVerdict(false, RecognitionVerdict.Reason.EXCLUDED_BY_TAG);
        }
        if (facts.boss()) {
            return new RecognitionVerdict(false, RecognitionVerdict.Reason.BOSS);
        }
        if (facts.includeTagged()) {
            return new RecognitionVerdict(true, RecognitionVerdict.Reason.INCLUDED_BY_TAG);
        }
        if ("monster".equals(facts.spawnCategory())) {
            return new RecognitionVerdict(false, RecognitionVerdict.Reason.MONSTER_CATEGORY);
        }
        if (facts.animalClass()) {
            return new RecognitionVerdict(true, RecognitionVerdict.Reason.ANIMAL_CLASS);
        }
        if (facts.waterAnimalClass()) {
            return new RecognitionVerdict(true, RecognitionVerdict.Reason.WATER_ANIMAL_CLASS);
        }
        if (ANIMAL_CATEGORIES.contains(facts.spawnCategory())) {
            return new RecognitionVerdict(true, RecognitionVerdict.Reason.ANIMAL_CATEGORY);
        }
        return new RecognitionVerdict(false, RecognitionVerdict.Reason.NO_RULE);
    }
}
