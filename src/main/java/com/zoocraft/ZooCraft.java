package com.zoocraft;

import com.mojang.logging.LogUtils;
import com.zoocraft.animal.AnimalRecognition;
import com.zoocraft.chunk.ZooChunkLoader;
import com.zoocraft.config.ZooConfig;
import com.zoocraft.registry.ZooEntities;
import com.zoocraft.network.ZooNetwork;
import com.zoocraft.registry.ZooItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(ZooCraft.MOD_ID)
public final class ZooCraft {
    public static final String MOD_ID = "zoocraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ZooCraft(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ZooConfig.SPEC);
        ZooItems.register(modBus);
        ZooEntities.register(modBus);
        ZooChunkLoader.register(modBus);
        AnimalRecognition.register(modBus);
        ZooNetwork.register(modBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
