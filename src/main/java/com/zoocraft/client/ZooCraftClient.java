package com.zoocraft.client;

import com.zoocraft.ZooCraft;
import com.zoocraft.registry.ZooEntities;
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
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ZooEntities.VISITOR.get(), VisitorRenderer::new);
    }
}
