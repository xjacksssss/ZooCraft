package com.zoocraft.core.animal;

/** Whether an entity counts as an exhibit animal, and which rule decided (Phase 3 spec section 6.3). */
public record RecognitionVerdict(boolean recognised, Reason reason) {
    public enum Reason {
        EXCLUDED_BY_TAG("tag zoocraft:not_exhibit_animals"),
        BOSS("boss tag"),
        INCLUDED_BY_TAG("tag zoocraft:exhibit_animals"),
        VILLAGER_OR_TRADER("villager or trader"),
        MONSTER_CATEGORY("monster category"),
        ANIMAL_CLASS("extends Animal"),
        WATER_ANIMAL_CLASS("extends WaterAnimal"),
        ANIMAL_CATEGORY("animal spawn category"),
        NO_RULE("no rule matched");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
