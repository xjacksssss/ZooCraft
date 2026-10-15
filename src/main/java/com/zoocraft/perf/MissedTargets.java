package com.zoocraft.perf;

import com.zoocraft.ZooCraft;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where visitors' path plans most often fall short over the last 30 seconds, for {@code /zoo debug perf}. A target that
 * keeps being missed is usually one visitors can't stand on or reach (inside a pen, on a block, behind a wall).
 */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class MissedTargets {
    public record Missed(BlockPos pos, int count) {
    }

    private static final int MAX_TRACKED = 512;
    private static final int WINDOW_TICKS = 600;
    private static final Map<BlockPos, Integer> COUNTS = new HashMap<>();
    private static Map<BlockPos, Integer> last = Map.of();

    private MissedTargets() {
    }

    public static void record(BlockPos target) {
        if (COUNTS.size() < MAX_TRACKED || COUNTS.containsKey(target)) {
            COUNTS.merge(target.immutable(), 1, Integer::sum);
        }
    }

    /** The most-missed targets in the current and previous window together, most first. */
    public static List<Missed> top(int n) {
        Map<BlockPos, Integer> merged = new HashMap<>(last);
        COUNTS.forEach((pos, c) -> merged.merge(pos, c, Integer::sum));
        List<Missed> list = new ArrayList<>();
        merged.forEach((pos, c) -> list.add(new Missed(pos, c)));
        list.sort(Comparator.comparingInt(Missed::count).reversed());
        return list.size() > n ? list.subList(0, n) : list;
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % WINDOW_TICKS == 0) {
            last = Map.copyOf(COUNTS);
            COUNTS.clear();
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        COUNTS.clear();
        last = Map.of();
    }
}
