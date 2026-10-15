package com.zoocraft.registry;

import com.zoocraft.ZooCraft;
import com.zoocraft.litter.RubbishBinBlock;
import com.zoocraft.litter.RubbishBinBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** ZooCraft's one dedicated block: the rubbish bin. */
public final class ZooBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ZooCraft.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ZooCraft.MOD_ID);

    public static final DeferredBlock<RubbishBinBlock> RUBBISH_BIN = BLOCKS.registerBlock("rubbish_bin", RubbishBinBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(2.0f, 6.0f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> RUBBISH_BIN_ITEM = ZooItems.ITEMS.registerSimpleBlockItem(RUBBISH_BIN, new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RubbishBinBlockEntity>> RUBBISH_BIN_ENTITY =
            BLOCK_ENTITIES.register("rubbish_bin", () -> BlockEntityType.Builder.of(RubbishBinBlockEntity::new, RUBBISH_BIN.get()).build(null));

    private ZooBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
