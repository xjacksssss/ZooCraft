package com.zoocraft.client;

import com.zoocraft.ZooCraft;
import com.zoocraft.network.ToolModePayload;
import com.zoocraft.registry.ZooItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sneak and scroll while holding the Zoo Designation Tool to change its mode instead of the hotbar slot. */
@EventBusSubscriber(modid = ZooCraft.MOD_ID, value = Dist.CLIENT)
public final class ToolScrollHandler {
    private ToolScrollHandler() {
    }

    @SubscribeEvent
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null || !player.isShiftKeyDown() || event.getScrollDeltaY() == 0
                || !player.getMainHandItem().is(ZooItems.DESIGNATION_TOOL.get())) {
            return;
        }
        event.setCanceled(true);
        PacketDistributor.sendToServer(new ToolModePayload(event.getScrollDeltaY() > 0 ? -1 : 1));
    }
}
