package com.zoocraft.core.report;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Ranks what is going wrong so the dashboard can lead with the most useful advice (Phase 2 spec section 10.3).
 * An issue's kind names its advice text; its args fill in the shop, item or place; its count is how often it
 * happened today.
 */
public final class Advice {
    public record Issue(String kind, List<String> args, int count) {
    }

    private static final Map<String, Integer> WEIGHTS = Map.ofEntries(
            Map.entry("could_not_leave", 5),
            Map.entry("hungry_no_food", 4),
            Map.entry("no_toilet", 4),
            Map.entry("sold_out", 3),
            Map.entry("cant_reach_shop", 3),
            Map.entry("cant_reach_toilet", 3),
            Map.entry("bin_full", 3),
            Map.entry("toilet_queue", 2),
            Map.entry("rubbish", 2),
            Map.entry("cant_reach_exhibit", 2));

    private Advice() {
    }

    public static int weight(String kind) {
        return WEIGHTS.getOrDefault(kind, 1);
    }

    public static List<Issue> rank(List<Issue> issues, int limit) {
        return issues.stream()
                .filter(i -> i.count() > 0)
                .sorted(Comparator.comparingLong((Issue i) -> (long) i.count() * weight(i.kind())).reversed())
                .limit(limit)
                .toList();
    }
}
