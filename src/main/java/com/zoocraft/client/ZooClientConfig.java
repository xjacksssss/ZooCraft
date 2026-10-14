package com.zoocraft.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-side settings. */
public final class ZooClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SHOW_VISITOR_COMMENTS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        SHOW_VISITOR_COMMENTS = b.comment("Show what visitors say above their heads.").define("showVisitorComments", true);
        SPEC = b.build();
    }

    private ZooClientConfig() {
    }
}
