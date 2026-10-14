package com.zoocraft;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(ZooCraft.MOD_ID)
public final class ZooCraft {
    public static final String MOD_ID = "zoocraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ZooCraft(IEventBus modBus, ModContainer container) {
        LOGGER.info("ZooCraft loading");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
