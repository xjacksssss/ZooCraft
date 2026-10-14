package com.zoocraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.zoocraft.ZooCraft;
import com.zoocraft.network.DashboardPayloads;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

/** The dashboard key. Defaults to semicolon, which is free in ATM10's key layout. */
public final class ZooKeys {
    public static final KeyMapping OPEN_DASHBOARD = new KeyMapping("key.zoocraft.dashboard", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SEMICOLON, "key.categories.zoocraft");

    private ZooKeys() {
    }

    static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_DASHBOARD);
    }

    @EventBusSubscriber(modid = ZooCraft.MOD_ID, value = Dist.CLIENT)
    static final class Ticker {
        private Ticker() {
        }

        @SubscribeEvent
        static void onClientTick(ClientTickEvent.Post event) {
            while (OPEN_DASHBOARD.consumeClick()) {
                if (Minecraft.getInstance().player != null && Minecraft.getInstance().screen == null) {
                    PacketDistributor.sendToServer(new DashboardPayloads.OpenRequest(Optional.empty()));
                }
            }
        }
    }
}
