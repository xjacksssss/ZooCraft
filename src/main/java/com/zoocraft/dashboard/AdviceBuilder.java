package com.zoocraft.dashboard;

import com.zoocraft.core.report.Advice;
import com.zoocraft.shop.FacilityCensus;
import com.zoocraft.shop.FacilitySnapshot;
import com.zoocraft.shop.ProductCatalog;
import com.zoocraft.zoo.Shop;
import com.zoocraft.zoo.Toilet;
import com.zoocraft.zoo.Zoo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns today's complaints into advice that names the cause and the place (Phase 2 spec section 10.3): from the
 * comment tally, the subjects recorded with complaints, shops' missed sales and full bins. Issues about shops or
 * toilets that no longer exist are dropped. Rebuilt for every snapshot, so advice goes away once the problem stops.
 */
final class AdviceBuilder {
    /** Complaints worded from the tally alone; the rest come from subjects or shop records. */
    private static final List<String> TALLY_KINDS = List.of("hungry_no_food", "no_toilet", "cant_afford", "same_animals",
            "nothing_to_see", "cant_reach_exhibit", "could_not_leave", "lost");

    private AdviceBuilder() {
    }

    static List<Component> build(Zoo zoo, int limit) {
        Map<Advice.Issue, Component> text = new IdentityHashMap<>();
        List<Advice.Issue> issues = new ArrayList<>();
        Map<String, Integer> tally = zoo.stats().commentTally();

        for (String kind : TALLY_KINDS) {
            int count = tally.getOrDefault(kind, 0);
            add(issues, text, kind, count, Component.translatable("zoocraft.advice." + kind, count));
        }
        int expensiveTickets = tally.getOrDefault("too_expensive", 0);
        add(issues, text, "admission_too_expensive", expensiveTickets,
                Component.translatable("zoocraft.advice.admission_too_expensive", expensiveTickets, zoo.admissionPrice()));

        for (Map.Entry<String, Integer> entry : zoo.reports().subjectsToday().entrySet()) {
            int bar = entry.getKey().indexOf('|');
            String kind = entry.getKey().substring(0, bar);
            String subject = entry.getKey().substring(bar + 1);
            int count = entry.getValue();
            switch (kind) {
                case "cant_reach_shop" -> shop(zoo, subject).ifPresent(shop ->
                        add(issues, text, kind, count, Component.translatable("zoocraft.advice.cant_reach_shop", shop.name())));
                case "toilet_queue", "cant_reach_toilet" -> toilet(zoo, subject).ifPresent(toilet ->
                        add(issues, text, kind, count, Component.translatable("zoocraft.advice." + kind, toilet.pos().getX(), toilet.pos().getZ())));
                case "rubbish_everywhere" -> {
                    String[] xz = subject.split(",");
                    if (xz.length == 2) {
                        add(issues, text, "rubbish", count, Component.translatable("zoocraft.advice.rubbish", xz[0], xz[1]));
                    }
                }
                default -> {
                }
            }
        }

        for (Shop shop : zoo.shops()) {
            for (Map.Entry<String, Integer> missed : shop.stats().missedToday().entrySet()) {
                int bar = missed.getKey().indexOf('|');
                String reason = missed.getKey().substring(0, bar);
                String itemId = missed.getKey().substring(bar + 1);
                Component item = ProductCatalog.item(itemId).map(i -> (Component) i.getDescription()).orElse(Component.literal(itemId));
                int count = missed.getValue();
                if (reason.equals("out_of_stock")) {
                    add(issues, text, "sold_out", count, Component.translatable("zoocraft.advice.sold_out", shop.name(), item, count));
                } else if (reason.equals("too_expensive")) {
                    add(issues, text, "too_expensive", count, Component.translatable("zoocraft.advice.too_expensive", shop.name(), item,
                            ProductCatalog.priceFor(shop, itemId)));
                }
            }
        }

        for (FacilitySnapshot.BinState bin : FacilityCensus.cached(zoo).bins()) {
            if (!bin.usable()) {
                add(issues, text, "bin_full", 1, Component.translatable("zoocraft.advice.bin_full", bin.pos().getX(), bin.pos().getZ()));
            }
        }

        return Advice.rank(issues, limit).stream().map(text::get).toList();
    }

    private static void add(List<Advice.Issue> issues, Map<Advice.Issue, Component> text, String kind, int count, Component line) {
        if (count > 0) {
            Advice.Issue issue = new Advice.Issue(kind, List.of(), count);
            issues.add(issue);
            text.put(issue, line);
        }
    }

    private static Optional<Shop> shop(Zoo zoo, String id) {
        return parse(id).flatMap(zoo::shop);
    }

    private static Optional<Toilet> toilet(Zoo zoo, String id) {
        return parse(id).flatMap(uuid -> zoo.toilets().stream().filter(t -> t.id().equals(uuid)).findFirst());
    }

    private static Optional<UUID> parse(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
