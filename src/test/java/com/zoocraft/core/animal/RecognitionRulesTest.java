package com.zoocraft.core.animal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecognitionRulesTest {
    private static RecognitionFacts facts(boolean include, boolean exclude, boolean boss, boolean animal, boolean water, String category) {
        return new RecognitionFacts(include, exclude, boss, animal, water, category);
    }

    @Test
    void vanillaAnimalIsRecognised() {
        assertTrue(RecognitionRules.isExhibitAnimal(facts(false, false, false, true, false, "creature")));
    }

    @Test
    void fishByCategoryIsRecognised() {
        assertTrue(RecognitionRules.isExhibitAnimal(facts(false, false, false, false, true, "water_ambient")));
    }

    @Test
    void moddedCreatureByCategoryOnlyIsRecognised() {
        assertTrue(RecognitionRules.isExhibitAnimal(facts(false, false, false, false, false, "creature")));
        assertTrue(RecognitionRules.isExhibitAnimal(facts(false, false, false, false, false, "axolotls")));
    }

    @Test
    void miscEntityIsNotRecognised() {
        assertFalse(RecognitionRules.isExhibitAnimal(facts(false, false, false, false, false, "misc")));
    }

    @Test
    void excludeTagWins() {
        assertFalse(RecognitionRules.isExhibitAnimal(facts(false, true, false, true, false, "creature")));
        assertFalse(RecognitionRules.isExhibitAnimal(facts(true, true, false, true, false, "creature")));
    }

    @Test
    void bossesAreNeverExhibits() {
        assertFalse(RecognitionRules.isExhibitAnimal(facts(false, false, true, true, false, "creature")));
    }

    @Test
    void monsterCategoryAnimalIsExcludedUnlessTaggedIn() {
        assertFalse(RecognitionRules.isExhibitAnimal(facts(false, false, false, true, false, "monster")));
        assertTrue(RecognitionRules.isExhibitAnimal(facts(true, false, false, true, false, "monster")));
    }

    @Test
    void includeTagAddsAnything() {
        assertTrue(RecognitionRules.isExhibitAnimal(facts(true, false, false, false, false, "misc")));
    }
}
