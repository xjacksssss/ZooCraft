package com.zoocraft.litter;

import com.zoocraft.core.perf.PerfSlot;
import com.zoocraft.perf.SimulationTimers;
import com.zoocraft.config.ZooConfig;
import com.zoocraft.core.litter.LitterRules;
import com.zoocraft.registry.ZooEntities;
import com.zoocraft.zoo.Zoo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Optional;

/** The one place litter is dropped, so the boundary, fluid and cap rules always apply (Phase 2 spec section 9.3). */
public final class Littering {
    private Littering() {
    }

    public static LitterRules.Outcome drop(ServerLevel level, Zoo zoo, Vec3 at, int variant) {
        long timer = SimulationTimers.start();
        try {
            return doDrop(level, zoo, at, variant);
        } finally {
            SimulationTimers.stop(PerfSlot.LITTER, zoo.id(), timer);
        }
    }

    private static LitterRules.Outcome doDrop(ServerLevel level, Zoo zoo, Vec3 at, int variant) {
        BlockPos pos = BlockPos.containing(at);
        if (!level.dimension().equals(zoo.dimension()) || !zoo.contains(pos) || !level.getFluidState(pos).isEmpty()) {
            return LitterRules.Outcome.NONE;
        }
        var inZoo = LitterTracker.litterIn(zoo);
        double merge = LitterRules.MERGE_DISTANCE * LitterRules.MERGE_DISTANCE;
        Optional<LitterEntity> nearest = inZoo.stream()
                .filter(l -> l.pile() < LitterRules.MAX_PILE && l.distanceToSqr(at) <= merge)
                .min(Comparator.comparingDouble(l -> l.distanceToSqr(at)));
        LitterRules.Outcome outcome = LitterRules.decide(inZoo.size(), ZooConfig.MAX_LITTER_PER_ZOO.get(),
                LitterTracker.total(), ZooConfig.MAX_LITTER_TOTAL.get(), nearest.map(LitterEntity::pile).orElse(0));
        switch (outcome) {
            case SPAWN -> {
                LitterEntity litter = ZooEntities.LITTER.get().create(level);
                if (litter == null) {
                    return LitterRules.Outcome.NONE;
                }
                litter.place(at, variant);
                level.addFreshEntity(litter);
            }
            case GROW -> nearest.ifPresent(l -> l.setPile(l.pile() + 1));
            case NONE -> {
            }
        }
        return outcome;
    }
}
