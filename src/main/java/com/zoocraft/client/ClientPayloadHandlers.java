package com.zoocraft.client;

import com.zoocraft.network.BoundaryDraftPayload;
import com.zoocraft.network.DashboardPayloads;
import com.zoocraft.network.DashboardSnapshot;
import com.zoocraft.network.ZooOutlinesPayload;
import net.minecraft.client.Minecraft;

/** Client-side packet handling. Only ever called on the client (see ZooNetwork). */
public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {
    }

    public static void boundaryDraft(BoundaryDraftPayload payload) {
        ClientBoundaryState.draft = payload;
    }

    public static void outlines(ZooOutlinesPayload payload) {
        ClientBoundaryState.outlines = payload;
    }

    public static void openNameZooScreen() {
        Minecraft.getInstance().setScreen(new NameZooScreen());
    }

    public static void dashboard(DashboardSnapshot snapshot) {
        ClientDashboardState.current = snapshot;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DashboardScreen screen) {
            screen.onSnapshot();
        } else if (snapshot.openScreen()) {
            minecraft.setScreen(new DashboardScreen());
        }
    }

    public static void zooList(DashboardPayloads.ZooList list) {
        Minecraft.getInstance().setScreen(new ZooPickerScreen(list.zoos()));
    }

    public static void dashboardClosed() {
        if (Minecraft.getInstance().screen instanceof DashboardScreen) {
            Minecraft.getInstance().setScreen(null);
        }
        ClientDashboardState.current = null;
    }
}
