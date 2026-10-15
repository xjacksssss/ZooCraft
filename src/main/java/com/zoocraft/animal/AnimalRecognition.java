package com.zoocraft.animal;

import com.zoocraft.ZooCraft;
import com.zoocraft.core.animal.RecognitionFacts;
import com.zoocraft.core.animal.RecognitionRules;
import com.zoocraft.visitor.Visitor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * Automatic animal recognition (spec sections 8.1 and 8.2). Works for any mod's animals without registration;
 * modpacks can adjust it with the {@code zoocraft:exhibit_animals} and {@code zoocraft:not_exhibit_animals} tags
 * and merge species with the {@code zoocraft:species} data map.
 */
public final class AnimalRecognition {
    public static final TagKey<EntityType<?>> EXHIBIT_ANIMALS = TagKey.create(Registries.ENTITY_TYPE, ZooCraft.id("exhibit_animals"));
    public static final TagKey<EntityType<?>> NOT_EXHIBIT_ANIMALS = TagKey.create(Registries.ENTITY_TYPE, ZooCraft.id("not_exhibit_animals"));

    public static final DataMapType<EntityType<?>, SpeciesData> SPECIES = DataMapType.builder(
            ZooCraft.id("species"), Registries.ENTITY_TYPE, SpeciesData.CODEC).synced(SpeciesData.CODEC, false).build();

    private AnimalRecognition() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterDataMapTypesEvent event) -> event.register(SPECIES));
    }

    public static boolean isExhibitAnimal(Entity entity) {
        if (!(entity instanceof Mob) || entity instanceof Visitor || !entity.isAlive()) {
            return false;
        }
        return RecognitionRules.isExhibitAnimal(facts(entity));
    }

    /** What the rules need to know about a constructed entity. */
    public static RecognitionFacts facts(Entity entity) {
        EntityType<?> type = entity.getType();
        return new RecognitionFacts(
                type.is(EXHIBIT_ANIMALS),
                type.is(NOT_EXHIBIT_ANIMALS),
                type.is(Tags.EntityTypes.BOSSES),
                entity instanceof Animal,
                entity instanceof WaterAnimal,
                type.getCategory().getName());
    }

    /** What can be known without constructing the entity: tags and spawn category only. */
    public static RecognitionFacts factsForType(EntityType<?> type) {
        return new RecognitionFacts(
                type.is(EXHIBIT_ANIMALS),
                type.is(NOT_EXHIBIT_ANIMALS),
                type.is(Tags.EntityTypes.BOSSES),
                false,
                false,
                type.getCategory().getName());
    }

    /** The species an entity type belongs to: its data map entry if any, otherwise the entity type itself. */
    public static ResourceLocation species(EntityType<?> type) {
        SpeciesData data = type.builtInRegistryHolder().getData(SPECIES);
        return data != null ? data.species() : BuiltInRegistries.ENTITY_TYPE.getKey(type);
    }

    public static Component speciesName(EntityType<?> type) {
        SpeciesData data = type.builtInRegistryHolder().getData(SPECIES);
        if (data != null && data.name().isPresent()) {
            return Component.translatableWithFallback(data.name().get(), data.name().get());
        }
        return type.getDescription();
    }
}
