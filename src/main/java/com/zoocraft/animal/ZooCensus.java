package com.zoocraft.animal;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One zoo's animals at the last census.
 *
 * @param speciesOf    species id for each animal's entity UUID
 * @param speciesNames display name per species id
 * @param speciesCounts number of animals per species id
 */
public record ZooCensus(long takenAt, List<Mob> animals, Map<UUID, String> speciesOf, List<Exhibit> exhibits,
                        Map<String, Component> speciesNames, Map<String, Integer> speciesCounts) {
    public static final ZooCensus EMPTY = new ZooCensus(-1, List.of(), Map.of(), List.of(), Map.of(), Map.of());

    public Optional<Exhibit> exhibit(UUID id) {
        return exhibits.stream().filter(e -> e.id().equals(id)).findFirst();
    }

    public Optional<Exhibit> exhibitContaining(Mob animal) {
        return exhibits.stream().filter(e -> e.animals().contains(animal)).findFirst();
    }

    public String speciesOf(Mob animal) {
        return speciesOf.getOrDefault(animal.getUUID(), "");
    }
}
