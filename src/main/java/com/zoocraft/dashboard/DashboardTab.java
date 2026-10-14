package com.zoocraft.dashboard;

/** Dashboard tabs. The client says which one it is viewing, and the server sends only that tab's data (Phase 2 spec section 4.5). */
public enum DashboardTab {
    OVERVIEW,
    ANIMALS,
    SHOPS,
    SETTINGS;

    public static DashboardTab byOrdinal(int ordinal) {
        return values()[Math.floorMod(ordinal, values().length)];
    }
}
