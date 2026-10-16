package com.zoocraft.core.animal;

/**
 * What is known about an entity type for recognition purposes.
 *
 * @param spawnCategory     the lower-case spawn category name, for example "creature" or "monster"
 * @param villagerOrTrader  a villager-like NPC or a trader (vanilla's {@code Npc} or {@code Merchant}), whatever its class
 *                          or category: modded traders often sit in the creature category
 */
public record RecognitionFacts(boolean includeTagged, boolean excludeTagged, boolean boss,
                               boolean animalClass, boolean waterAnimalClass, String spawnCategory, boolean villagerOrTrader) {
    public RecognitionFacts(boolean includeTagged, boolean excludeTagged, boolean boss, boolean animalClass,
                            boolean waterAnimalClass, String spawnCategory) {
        this(includeTagged, excludeTagged, boss, animalClass, waterAnimalClass, spawnCategory, false);
    }
}
