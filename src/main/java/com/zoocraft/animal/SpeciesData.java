package com.zoocraft.animal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Data map value for {@code zoocraft:species}: lets a datapack say several entity types are one species.
 * Example {@code data/zoocraft/data_maps/entity_type/species.json}:
 * <pre>{"values": {"somemod:lion_male": {"species": "somemod:lion", "name": "Lion"}, ...}}</pre>
 *
 * @param name optional display name (a translation key or plain text)
 */
public record SpeciesData(ResourceLocation species, Optional<String> name) {
    public static final Codec<SpeciesData> CODEC = Codec.withAlternative(
            RecordCodecBuilder.create(i -> i.group(
                    ResourceLocation.CODEC.fieldOf("species").forGetter(SpeciesData::species),
                    Codec.STRING.optionalFieldOf("name").forGetter(SpeciesData::name)
            ).apply(i, SpeciesData::new)),
            ResourceLocation.CODEC.xmap(id -> new SpeciesData(id, Optional.empty()), SpeciesData::species));
}
