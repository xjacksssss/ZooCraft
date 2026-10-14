package com.zoocraft.client;

import com.zoocraft.network.BoundaryDraftPayload;
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
}
