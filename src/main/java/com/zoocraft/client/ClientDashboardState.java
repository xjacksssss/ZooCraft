package com.zoocraft.client;

import com.zoocraft.network.DashboardSnapshot;
import org.jetbrains.annotations.Nullable;

/** The latest dashboard snapshot from the server. Display only. */
public final class ClientDashboardState {
    static @Nullable DashboardSnapshot current;

    private ClientDashboardState() {
    }
}
