package com.zoocraft.zoo;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Live problems with a zoo, worded for the dashboard ("Visitors can't find a way to your gates").
 * Runtime only: alerts are recomputed by the systems that detect them. Each alert has an ID so the system that
 * raised it can clear it, and may expire on its own.
 */
public final class ZooAlerts {
    public static final String NO_ROUTE_TO_GATES = "zoocraft.alert.no_route_to_gates";
    public static final String NO_ANIMALS = "zoocraft.alert.no_animals";

    private record Alert(Component text, long until) {
    }

    private static final Map<UUID, Map<String, Alert>> ALERTS = new HashMap<>();

    private ZooAlerts() {
    }

    /** Raises an alert whose ID is also its translation key. */
    public static void raise(Zoo zoo, String key) {
        raise(zoo, key, Component.translatable(key));
    }

    public static void raise(Zoo zoo, String id, Component text) {
        raiseUntil(zoo, id, text, Long.MAX_VALUE);
    }

    /** Raises an alert that disappears by itself at {@code untilGameTime}. */
    public static void raiseUntil(Zoo zoo, String id, Component text, long untilGameTime) {
        ALERTS.computeIfAbsent(zoo.id(), k -> new LinkedHashMap<>()).put(id, new Alert(text, untilGameTime));
    }

    public static void clear(Zoo zoo, String id) {
        Map<String, Alert> alerts = ALERTS.get(zoo.id());
        if (alerts != null) {
            alerts.remove(id);
        }
    }

    /** The alerts still active at {@code gameTime}, oldest first. */
    public static List<Component> messages(Zoo zoo, long gameTime) {
        Map<String, Alert> alerts = ALERTS.get(zoo.id());
        if (alerts == null) {
            return List.of();
        }
        alerts.values().removeIf(a -> a.until() <= gameTime);
        List<Component> texts = new ArrayList<>();
        alerts.values().forEach(a -> texts.add(a.text()));
        return texts;
    }

    public static void reset() {
        ALERTS.clear();
    }
}
