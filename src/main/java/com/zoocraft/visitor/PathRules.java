package com.zoocraft.visitor;

import com.zoocraft.core.geometry.ZooShape;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Where a visitor's paths may go. The boundary is a rule, not a wall: admitted visitors stay inside and arriving
 * visitors stay outside, except around gates where they cross.
 */
public record PathRules(@Nullable ZooShape shape, Mode mode, List<BlockPos> gateAreas) {
    public static final PathRules ANYWHERE = new PathRules(null, Mode.ANY, List.of());
    private static final double GATE_RADIUS_SQ = 2.5 * 2.5;

    public enum Mode { ANY, INSIDE, OUTSIDE }

    public boolean allows(int x, int z) {
        if (mode == Mode.ANY || shape == null) {
            return true;
        }
        for (BlockPos gate : gateAreas) {
            double dx = gate.getX() - x, dz = gate.getZ() - z;
            if (dx * dx + dz * dz <= GATE_RADIUS_SQ) {
                return true;
            }
        }
        boolean inside = shape.containsBlock(x, z);
        return mode == Mode.INSIDE ? inside : !inside;
    }
}
