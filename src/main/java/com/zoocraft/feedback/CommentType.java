package com.zoocraft.feedback;

import java.util.Locale;

/**
 * Things visitors say, each tied to a real event (spec section 8.6). The satisfaction effect is applied to the
 * visitor who says it; complaints are tallied on the zoo for the dashboard.
 */
public enum CommentType {
    AMAZING_EXHIBIT(8, false),
    MANY_ANIMALS(10, false),
    SAME_ANIMALS(-6, true),
    NOTHING_TO_SEE(-10, true),
    CANT_REACH_EXHIBIT(-8, true),
    TOO_EXPENSIVE(0, true),
    ZOO_CLOSED(-2, false),
    COULD_NOT_LEAVE(-25, true),
    LOST(-5, true);

    private final int satisfactionEffect;
    private final boolean complaint;

    CommentType(int satisfactionEffect, boolean complaint) {
        this.satisfactionEffect = satisfactionEffect;
        this.complaint = complaint;
    }

    public int satisfactionEffect() {
        return satisfactionEffect;
    }

    public boolean complaint() {
        return complaint;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "zoocraft.comment." + key();
    }

    public static CommentType byKey(String key) {
        for (CommentType type : values()) {
            if (type.key().equals(key)) {
                return type;
            }
        }
        return null;
    }
}
