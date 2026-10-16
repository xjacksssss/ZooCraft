package com.zoocraft.core.animal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void villagersAndTradersAreNeverAnimalsUnlessTaggedIn() {
        RecognitionFacts trader = new RecognitionFacts(false, false, false, true, false, "creature", true);
        assertEquals(new RecognitionVerdict(false, RecognitionVerdict.Reason.VILLAGER_OR_TRADER), RecognitionRules.decide(trader));
        RecognitionFacts taggedIn = new RecognitionFacts(true, false, false, false, false, "creature", true);
        assertTrue(RecognitionRules.isExhibitAnimal(taggedIn));
    }

    @Test
    void includeTagAddsAnything() {
        assertTrue(RecognitionRules.isExhibitAnimal(facts(true, false, false, false, false, "misc")));
    }

    @Test
    void decideNamesTheRuleThatDecided() {
        assertEquals(new RecognitionVerdict(false, RecognitionVerdict.Reason.EXCLUDED_BY_TAG),
                RecognitionRules.decide(facts(true, true, false, true, false, "creature")));
        assertEquals(new RecognitionVerdict(false, RecognitionVerdict.Reason.BOSS),
                RecognitionRules.decide(facts(true, false, true, true, false, "creature")));
        assertEquals(new RecognitionVerdict(true, RecognitionVerdict.Reason.INCLUDED_BY_TAG),
                RecognitionRules.decide(facts(true, false, false, false, false, "monster")));
        assertEquals(new RecognitionVerdict(false, RecognitionVerdict.Reason.MONSTER_CATEGORY),
                RecognitionRules.decide(facts(false, false, false, true, false, "monster")));
        assertEquals(new RecognitionVerdict(true, RecognitionVerdict.Reason.ANIMAL_CLASS),
                RecognitionRules.decide(facts(false, false, false, true, false, "misc")));
        assertEquals(new RecognitionVerdict(true, RecognitionVerdict.Reason.WATER_ANIMAL_CLASS),
                RecognitionRules.decide(facts(false, false, false, false, true, "misc")));
        assertEquals(new RecognitionVerdict(true, RecognitionVerdict.Reason.ANIMAL_CATEGORY),
                RecognitionRules.decide(facts(false, false, false, false, false, "creature")));
        assertEquals(new RecognitionVerdict(false, RecognitionVerdict.Reason.NO_RULE),
                RecognitionRules.decide(facts(false, false, false, false, false, "misc")));
    }

    @Test
    void decideAgreesWithIsExhibitAnimalForEveryCombination() {
        String[] categories = {"creature", "monster", "misc", "water_creature", "ambient"};
        for (int bits = 0; bits < 32; bits++) {
            for (String category : categories) {
                RecognitionFacts f = facts((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0, (bits & 16) != 0, category);
                assertEquals(RecognitionRules.isExhibitAnimal(f), RecognitionRules.decide(f).recognised(), f.toString());
            }
        }
    }
}
