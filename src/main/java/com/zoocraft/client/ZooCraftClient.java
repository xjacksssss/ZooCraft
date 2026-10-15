package com.zoocraft.client;

import com.zoocraft.ZooCraft;
import com.zoocraft.litter.LitterEntity;
import com.zoocraft.registry.ZooEntities;
import com.zoocraft.registry.ZooItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only setup: renderers, client config and the built-in config screen. */
@Mod(value = ZooCraft.MOD_ID, dist = Dist.CLIENT)
public final class ZooCraftClient {
    public ZooCraftClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ZooClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(ZooCraftClient::onRegisterRenderers);
        modBus.addListener(ZooKeys::onRegisterKeys);
        // Litter glows through walls for players holding a zoo tool, so it is easy to find and pick up.
        LitterEntity.glowOnClient = litter -> {
            LocalPlayer player = Minecraft.getInstance().player;
            return player != null && ZooClientConfig.HIGHLIGHT_LITTER.get()
                    && (ZooItems.isZooTool(player.getMainHandItem()) || ZooItems.isZooTool(player.getOffhandItem()));
        };
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ZooEntities.VISITOR.get(), VisitorRenderer::new);
        event.registerEntityRenderer(ZooEntities.LITTER.get(), LitterRenderer::new);
    }
}
