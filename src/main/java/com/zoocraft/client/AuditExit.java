package com.zoocraft.client;

import com.zoocraft.ZooCraft;
import com.zoocraft.animal.RecognitionAudit;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * For an unattended recognition audit ({@code -Dzoocraft.recognitionAudit=true}): keeps the single-player world running
 * when the window loses focus (a paused integrated server stops the audit), and closes the game once the results are
 * written, putting the player's pause setting back first.
 */
@EventBusSubscriber(modid = ZooCraft.MOD_ID, value = Dist.CLIENT)
final class AuditExit {
    private static boolean started;
    private static boolean stopping;
    private static boolean pauseOnLostFocus;

    private AuditExit() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(RecognitionAudit.PROPERTY)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!started) {
            started = true;
            pauseOnLostFocus = minecraft.options.pauseOnLostFocus;
            minecraft.options.pauseOnLostFocus = false;
        }
        if (!stopping && RecognitionAudit.exitRequested()) {
            stopping = true;
            minecraft.options.pauseOnLostFocus = pauseOnLostFocus;
            ZooCraft.LOGGER.info("Recognition audit finished; closing the game.");
            minecraft.stop();
        }
    }
}
