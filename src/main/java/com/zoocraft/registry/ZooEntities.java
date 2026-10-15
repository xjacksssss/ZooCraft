package com.zoocraft.registry;

import com.zoocraft.ZooCraft;
import com.zoocraft.litter.LitterEntity;
import com.zoocraft.visitor.Visitor;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ZooEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ZooCraft.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<Visitor>> VISITOR = ENTITIES.register("visitor",
            () -> EntityType.Builder.of(Visitor::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .eyeHeight(1.62f)
                    .clientTrackingRange(10)
                    .build(ZooCraft.id("visitor").toString()));

    /** A piece of litter (Phase 2 spec section 9.3). */
    public static final DeferredHolder<EntityType<?>, EntityType<LitterEntity>> LITTER = ENTITIES.register("litter",
            () -> EntityType.Builder.<LitterEntity>of(LitterEntity::new, MobCategory.MISC)
                    .sized(0.4f, 0.2f)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .build(ZooCraft.id("litter").toString()));

    /** For testing: a visitor spawned from an egg has no zoo and wanders off. */
    public static final DeferredItem<DeferredSpawnEggItem> VISITOR_SPAWN_EGG = ZooItems.ITEMS.register("visitor_spawn_egg",
            () -> new DeferredSpawnEggItem(VISITOR, 0x5E8C3A, 0xC8A165, new Item.Properties()));

    private ZooEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener(ZooEntities::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(VISITOR.get(), Visitor.createAttributes().build());
    }
}
