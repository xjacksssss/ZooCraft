package com.zoocraft.client;

import com.zoocraft.network.BoundaryDraftPayload;
import com.zoocraft.network.ZooOutlinesPayload;

/** What the client knows about boundaries, for drawing only. */
public final class ClientBoundaryState {
    static BoundaryDraftPayload draft = BoundaryDraftPayload.NONE;
    static ZooOutlinesPayload outlines = ZooOutlinesPayload.NONE;

    private ClientBoundaryState() {
    }

    static void reset() {
        draft = BoundaryDraftPayload.NONE;
        outlines = ZooOutlinesPayload.NONE;
    }
}
