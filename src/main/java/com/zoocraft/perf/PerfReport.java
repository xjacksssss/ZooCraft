package com.zoocraft.perf;

import com.zoocraft.animal.AnimalCensus;
import com.zoocraft.animal.Exhibit;
import com.zoocraft.config.ZooConfig;
import com.zoocraft.core.perf.PerfSlot;
import com.zoocraft.core.perf.TimerSlots;
import com.zoocraft.visitor.Visitor;
import com.zoocraft.visitor.VisitorPhase;
import com.zoocraft.visitor.VisitorTracker;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The text of {@code /zoo debug perf} (Phase 3 spec section 6.2). Operator diagnostics, so plain literals. */
public final class PerfReport {
    /** A visitor this long without getting closer to its target counts as stuck. */
    public static final long STUCK_TICKS = 200;
    private static final int STUCK_SHOWN = 3;

    private PerfReport() {
    }

    public static List<Component> server(MinecraftServer server) {
        List<Component> lines = new ArrayList<>();
        TimerSlots t = SimulationTimers.global();
        double tick = ms(t.averageNanos(PerfSlot.SERVER_TICK.ordinal()));
        double total = ms(t.averageNanos(PerfSlot.ZOOCRAFT_TOTAL.ordinal()));
        lines.add(Component.literal("ZooCraft performance, last " + t.ticks() / 20 + " s").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        lines.add(Component.literal(String.format(Locale.ROOT, "Server tick: average %.1f ms, worst %.1f ms. ZooCraft: average %.2f ms (%.0f%%), worst %.1f ms.",
                tick, ms(t.worstNanos(PerfSlot.SERVER_TICK.ordinal())), total, tick > 0 ? 100 * total / tick : 0,
                ms(t.worstNanos(PerfSlot.ZOOCRAFT_TOTAL.ordinal())))));
        lines.addAll(slotLines(t));
        lines.add(planLine(t));
        lines.add(Component.literal(String.format(Locale.ROOT, "Visitors: %d of %d allowed on the server.", VisitorTracker.total(),
                ZooConfig.MAX_VISITORS_TOTAL.get())));
        for (Zoo zoo : ZooManager.get(server).all()) {
            List<Visitor> visitors = VisitorTracker.visitors(zoo.id());
            if (visitors.isEmpty() && !zoo.open()) {
                continue;
            }
            double zooMs = SimulationTimers.forZoo(zoo.id()).map(z -> ms(z.averageNanos(PerfSlot.ZOOCRAFT_TOTAL.ordinal()))).orElse(0.0);
            lines.add(Component.literal(String.format(Locale.ROOT, " - %s: %d of %d visitors (%s), %.2f ms per tick, %d stuck", zoo.name(),
                    visitors.size(), ZooConfig.MAX_VISITORS_PER_ZOO.get(), phases(visitors), zooMs, stuckVisitors(visitors))));
        }
        return lines;
    }

    public static List<Component> zoo(MinecraftServer server, Zoo zoo) {
        List<Component> lines = new ArrayList<>();
        List<Visitor> visitors = VisitorTracker.visitors(zoo.id());
        lines.add(Component.literal(zoo.name() + ": " + visitors.size() + " visitors (" + phases(visitors) + ")")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        SimulationTimers.forZoo(zoo.id()).ifPresent(t -> {
            lines.addAll(slotLines(t));
            lines.add(planLine(t));
        });

        List<Visitor> stuck = visitors.stream().filter(v -> v.admitted() && v.ticksWithoutProgress() > STUCK_TICKS)
                .sorted(Comparator.comparingLong(Visitor::ticksWithoutProgress).reversed()).toList();
        StringBuilder stuckLine = new StringBuilder("Stuck visitors: " + stuck.size());
        for (int i = 0; i < Math.min(STUCK_SHOWN, stuck.size()); i++) {
            Visitor v = stuck.get(i);
            stuckLine.append(i == 0 ? " (" : ", ").append(v.blockPosition().toShortString())
                    .append(" for ").append(v.ticksWithoutProgress() / 20).append(" s");
        }
        lines.add(Component.literal(stuck.isEmpty() ? stuckLine.toString() : stuckLine.append(")").toString()));
        lines.add(Component.literal("Most visitors heading for one spot: " + mostOnOneTarget(visitors)));
        lines.add(missedLine(server, zoo));

        List<Exhibit> exhibits = AnimalCensus.cached(zoo).exhibits();
        if (exhibits.isEmpty()) {
            lines.add(Component.literal("Exhibits: none counted yet."));
        } else {
            int[] views = exhibits.stream().mapToInt(Exhibit::viewCount).sorted().toArray();
            long never = Arrays.stream(views).filter(v -> v == 0).count();
            lines.add(Component.literal(String.format(Locale.ROOT, "Exhibits: %d, views per exhibit min %d, median %d, max %d, never viewed %d",
                    views.length, views[0], views[views.length / 2], views[views.length - 1], never)));
        }
        return lines;
    }

    /** Admitted visitors that have not got closer to their target for {@link #STUCK_TICKS}. */
    public static int stuckVisitors(List<Visitor> visitors) {
        int stuck = 0;
        for (Visitor v : visitors) {
            if (v.admitted() && v.ticksWithoutProgress() > STUCK_TICKS) {
                stuck++;
            }
        }
        return stuck;
    }

    /** The most visitors walking to, or standing at, the same block. M10 replaces this with real occupancy counts. */
    public static int mostOnOneTarget(List<Visitor> visitors) {
        Map<BlockPos, Integer> counts = new HashMap<>();
        int most = 0;
        for (Visitor v : visitors) {
            BlockPos target = v.walkTarget();
            if (target != null) {
                most = Math.max(most, counts.merge(target, 1, Integer::sum));
            }
        }
        return most;
    }

    public static double ms(double nanos) {
        return nanos / 1_000_000.0;
    }

    private static List<Component> slotLines(TimerSlots t) {
        List<PerfSlot> slots = new ArrayList<>();
        for (PerfSlot slot : PerfSlot.values()) {
            if (slot != PerfSlot.SERVER_TICK && slot != PerfSlot.ZOOCRAFT_TOTAL && slot != PerfSlot.VISITOR_PATH_SHORT
                    && t.calls(slot.ordinal()) > 0) {
                slots.add(slot);
            }
        }
        slots.sort(Comparator.comparingDouble((PerfSlot s) -> t.averageNanos(s.ordinal())).reversed());
        List<Component> lines = new ArrayList<>();
        for (PerfSlot slot : slots) {
            lines.add(Component.literal(String.format(Locale.ROOT, "   %s: %.3f ms per tick, worst %.2f ms, %d calls", slot.label(),
                    ms(t.averageNanos(slot.ordinal())), ms(t.worstNanos(slot.ordinal())), t.calls(slot.ordinal()))));
        }
        return lines;
    }

    /** Where this zoo's visitors' path plans most often fall short, with what the target stands on. */
    private static Component missedLine(MinecraftServer server, Zoo zoo) {
        net.minecraft.server.level.ServerLevel level = server.getLevel(zoo.dimension());
        List<String> parts = new ArrayList<>();
        for (MissedTargets.Missed m : MissedTargets.top(200)) {
            if (parts.size() == 5) {
                break;
            }
            if (level != null && zoo.contains(m.pos())) {
                String below = level.isLoaded(m.pos()) ? net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(level.getBlockState(m.pos().below()).getBlock()).getPath() : "unloaded";
                String at = level.isLoaded(m.pos()) ? net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(level.getBlockState(m.pos()).getBlock()).getPath() : "unloaded";
                parts.add(m.pos().toShortString() + " (" + at + " on " + below + ") x" + m.count());
            }
        }
        return Component.literal("Most missed path targets: " + (parts.isEmpty() ? "none" : String.join("; ", parts)));
    }

    private static Component planLine(TimerSlots t) {
        double seconds = Math.max(1, t.ticks()) / 20.0;
        long plans = t.calls(PerfSlot.VISITOR_PATHFINDING.ordinal());
        long shortOnes = t.calls(PerfSlot.VISITOR_PATH_SHORT.ordinal());
        double each = plans == 0 ? 0 : ms(t.averageNanos(PerfSlot.VISITOR_PATHFINDING.ordinal())) * t.ticks() / plans;
        return Component.literal(String.format(Locale.ROOT, "   Path plans: %.1f per second, %.0f%% fall short of their target, %.2f ms each",
                plans / seconds, plans == 0 ? 0 : 100.0 * shortOnes / plans, each));
    }

    private static String phases(List<Visitor> visitors) {
        Map<VisitorPhase, Integer> counts = new EnumMap<>(VisitorPhase.class);
        visitors.forEach(v -> counts.merge(v.phase(), 1, Integer::sum));
        List<String> parts = new ArrayList<>();
        counts.forEach((phase, n) -> parts.add(n + " " + phase.name().toLowerCase(Locale.ROOT)));
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }
}
