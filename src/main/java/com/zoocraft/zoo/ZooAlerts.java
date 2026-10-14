package com.zoocraft.zoo;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Live problems with a zoo, worded for the dashboard ("Visitors can't find a way to your gates").
 * Runtime only: alerts are recomputed by the systems that detect them.
 */
public final class ZooAlerts {
    public static final String NO_ROUTE_TO_GATES = "zoocraft.alert.no_route_to_gates";
    public static final String NO_ANIMALS = "zoocraft.alert.no_animals";

    private static final Map<UUID, Set<String>> ALERTS = new HashMap<>();

    private ZooAlerts() {
    }

    public static void raise(Zoo zoo, String key) {
        ALERTS.computeIfAbsent(zoo.id(), k -> new LinkedHashSet<>()).add(key);
    }

    public static void clear(Zoo zoo, String key) {
        Set<String> set = ALERTS.get(zoo.id());
        if (set != null) {
            set.remove(key);
        }
    }

    public static Set<String> active(Zoo zoo) {
        return Set.copyOf(ALERTS.getOrDefault(zoo.id(), Set.of()));
    }

    public static void reset() {
        ALERTS.clear();
    }
}
