package com.zoocraft.zoo;

import net.minecraft.util.StringRepresentable;

public enum GateStatus implements StringRepresentable {
    /** Usable by visitors. */
    OPEN,
    /** Visitors recently failed to get through it. */
    BLOCKED,
    /** No longer within reach of the boundary edge after a boundary edit. Kept, but not used. */
    DETACHED;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
