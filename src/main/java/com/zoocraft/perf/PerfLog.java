package com.zoocraft.perf;

import com.zoocraft.ZooCraft;
import com.zoocraft.core.perf.PerfSlot;
import com.zoocraft.core.perf.TimerSlots;
import com.zoocraft.visitor.VisitorTracker;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** {@code /zoo debug perf log}: one CSV line per second to {@code logs/zoocraft-perf.csv} (Phase 3 spec section 6.2). */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class PerfLog {
    private static BufferedWriter out;
    private static int secondsLeft;
    private static int elapsed;

    private PerfLog() {
    }

    /** Starts a new log, replacing any running one. */
    public static Path start(MinecraftServer server, int seconds) throws IOException {
        stop();
        Path file = server.getServerDirectory().resolve("logs").resolve("zoocraft-perf.csv");
        Files.createDirectories(file.getParent());
        out = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        StringBuilder header = new StringBuilder("seconds");
        for (PerfSlot slot : PerfSlot.values()) {
            header.append(',').append(slot.name().toLowerCase(Locale.ROOT)).append("_ms");
        }
        out.write(header.append(",path_plans_per_s,path_short_per_s,visitors\n").toString());
        secondsLeft = seconds;
        elapsed = 0;
        return file;
    }

    public static boolean running() {
        return out != null;
    }

    public static void stop() {
        if (out == null) {
            return;
        }
        try {
            out.close();
        } catch (IOException e) {
            ZooCraft.LOGGER.warn("Couldn't close the ZooCraft perf log", e);
        }
        out = null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onTick(ServerTickEvent.Post event) {
        if (out == null || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        TimerSlots t = SimulationTimers.global();
        StringBuilder line = new StringBuilder(Integer.toString(++elapsed));
        for (PerfSlot slot : PerfSlot.values()) {
            line.append(',').append(String.format(Locale.ROOT, "%.3f", PerfReport.ms(t.averageNanos(slot.ordinal(), 20))));
        }
        line.append(',').append(t.calls(PerfSlot.VISITOR_PATHFINDING.ordinal(), 20))
                .append(',').append(t.calls(PerfSlot.VISITOR_PATH_SHORT.ordinal(), 20));
        line.append(',').append(VisitorTracker.total()).append('\n');
        try {
            out.write(line.toString());
            out.flush();
        } catch (IOException e) {
            ZooCraft.LOGGER.warn("Couldn't write the ZooCraft perf log", e);
            stop();
            return;
        }
        if (--secondsLeft <= 0) {
            stop();
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        stop();
    }
}
