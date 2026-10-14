package com.zoocraft.zoo;

import net.minecraft.network.chat.Component;

import java.util.List;

/** Outcome of a zoo action, with a message for the player and any extra notes (such as detached gates). */
public record ActionResult(boolean success, Component message, List<Component> notes) {
    public static ActionResult ok(Component message) {
        return new ActionResult(true, message, List.of());
    }

    public static ActionResult ok(Component message, List<Component> notes) {
        return new ActionResult(true, message, List.copyOf(notes));
    }

    public static ActionResult fail(Component message) {
        return new ActionResult(false, message, List.of());
    }
}
