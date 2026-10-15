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
    /**
     * The area round a gate where either side may walk: it covers the queue slots (up to 4 blocks out and 2 to the side)
     * and the overflow point 7 blocks out, plus the distance at which visitors count as having arrived there.
     */
    public static final double GATE_AREA = 8.0;
    private static final double GATE_RADIUS_SQ = GATE_AREA * GATE_AREA;

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
