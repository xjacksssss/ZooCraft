package com.zoocraft.visitor;

import com.zoocraft.ZooCraft;
import com.zoocraft.core.crowd.Occupancy;
import com.zoocraft.zoo.Gate;
import com.zoocraft.zoo.Standing;
import com.zoocraft.zoo.Zoo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Where arriving visitors queue outside each gate (Phase 3 spec section 7.3): up to eight standable slots in an arc
 * 2 to 4 blocks outside it, one visitor each, and an overflow point 3 blocks further out. Slots are found when first
 * needed and forgotten whenever a zoo changes (gates, boundary), so they follow the zoo's edits. Runtime only.
 */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class GateQueues {
    public static final int MAX_SLOTS = 8;
    /** A slot not renewed for this long is free again. */
    static final long EXPIRY_TICKS = 10 * 20;
    private static final int OVERFLOW_DISTANCE = 7;

    private static final Map<UUID, List<BlockPos>> SLOTS = new HashMap<>();
    private static final Occupancy<BlockPos> TAKEN = new Occupancy<>(EXPIRY_TICKS);

    private GateQueues() {
    }

    public static List<BlockPos> slots(ServerLevel level, Zoo zoo, Gate gate) {
        List<BlockPos> slots = SLOTS.get(gate.id());
        if (slots == null) {
            slots = find(level, zoo, gate);
            SLOTS.put(gate.id(), slots);
        }
        return slots;
    }

    private static List<BlockPos> find(ServerLevel level, Zoo zoo, Gate gate) {
        double[] normal = zoo.shape().outwardNormalAt(gate.pos().getX() + 0.5, gate.pos().getZ() + 0.5);
        double tx = -normal[1], tz = normal[0];
        List<BlockPos> found = new ArrayList<>();
        for (int d = 2; d <= 4; d++) {
            for (int s : new int[]{0, -1, 1, -2, 2}) {
                int x = (int) Math.floor(gate.pos().getX() + 0.5 + normal[0] * d + tx * s);
                int z = (int) Math.floor(gate.pos().getZ() + 0.5 + normal[1] * d + tz * s);
                if (zoo.shape().containsBlock(x, z) || !level.isLoaded(new BlockPos(x, 0, z))) {
                    continue;
                }
                Standing.findStandable(level, new BlockPos(x, gate.pos().getY(), z), 2)
                        .filter(p -> level.getFluidState(p).isEmpty() && !found.contains(p)).ifPresent(found::add);
            }
        }
        found.sort(Comparator.comparingDouble(p -> p.distSqr(gate.outside())));
        return List.copyOf(found.size() > MAX_SLOTS ? found.subList(0, MAX_SLOTS) : found);
    }

    /**
     * Renews the visitor's slot at this gate, or takes the free slot nearest {@code near} (the group leader's slot)
     * or, without one, nearest the gate.
     */
    public static Optional<BlockPos> claim(ServerLevel level, Zoo zoo, Gate gate, UUID visitor, @Nullable BlockPos near, long now) {
        List<BlockPos> slots = slots(level, zoo, gate);
        for (BlockPos slot : slots) {
            if (TAKEN.holds(slot, visitor) && TAKEN.claim(slot, visitor, now, 1)) {
                return Optional.of(slot);
            }
        }
        BlockPos anchor = near != null ? near : gate.outside();
        for (BlockPos slot : slots.stream().sorted(Comparator.comparingDouble(s -> s.distSqr(anchor))).toList()) {
            if (TAKEN.claim(slot, visitor, now, 1)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }

    /** Where visitors wait when every slot is taken: beyond the queue, 3 blocks further out. */
    public static BlockPos overflow(ServerLevel level, Zoo zoo, Gate gate) {
        double[] normal = zoo.shape().outwardNormalAt(gate.pos().getX() + 0.5, gate.pos().getZ() + 0.5);
        BlockPos guess = BlockPos.containing(gate.pos().getX() + 0.5 + normal[0] * OVERFLOW_DISTANCE, gate.pos().getY(),
                gate.pos().getZ() + 0.5 + normal[1] * OVERFLOW_DISTANCE);
        if (!level.isLoaded(guess)) {
            return gate.outside();
        }
        return Standing.findStandable(level, guess, 3).orElse(gate.outside());
    }

    public static void release(UUID visitor) {
        TAKEN.releaseAll(visitor);
    }

    /** A zoo changed: its slots are found again when next needed. */
    public static void forget(Zoo zoo) {
        SLOTS.clear();
    }

    /** The most visitors on one slot (1 while queues work). */
    public static int maxOnOneSlot(long now) {
        return TAKEN.maxCount(now);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SLOTS.clear();
        TAKEN.clear();
    }
}
