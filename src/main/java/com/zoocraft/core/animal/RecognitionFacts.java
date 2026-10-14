package com.zoocraft.core.animal;

/**
 * What is known about an entity type for recognition purposes.
 *
 * @param spawnCategory the lower-case spawn category name, for example "creature" or "monster"
 */
public record RecognitionFacts(boolean includeTagged, boolean excludeTagged, boolean boss,
                               boolean animalClass, boolean waterAnimalClass, String spawnCategory) {
}
