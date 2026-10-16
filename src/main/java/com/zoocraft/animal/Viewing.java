package com.zoocraft.animal;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Proximity viewing (spec section 8.4): close enough, and one ray not stopped by a solid opaque block.
 * Glass, fences, bars and leaves don't block the view; walls do. Visitors see further down than up, so bridges,
 * galleries and terraces overlooking an enclosure work (found in the terraced park, where a pond's animals on its bottom
 * were too far below its boardwalk); the ray still stops views through a solid floor.
 */
public final class Viewing {
    /** How far above a visitor's feet an animal can be seen. */
    public static final double MAX_ABOVE = 3.0;
    /** How far below a visitor's feet an animal can be seen. */
    public static final double MAX_BELOW = 8.0;

    private Viewing() {
    }

    public static boolean canSee(Level level, Vec3 eye, Vec3 feet, Entity animal, int radius) {
        double dx = animal.getX() - feet.x;
        double dz = animal.getZ() - feet.z;
        if (dx * dx + dz * dz > (double) radius * radius || !withinHeight(animal.getY() - feet.y)) {
            return false;
        }
        Vec3 target = animal.position().add(0, animal.getBbHeight() * 0.6, 0);
        Boolean blocked = BlockGetter.traverseBlocks(eye, target, level,
                (lvl, pos) -> lvl.getBlockState(pos).canOcclude() ? Boolean.TRUE : null,
                lvl -> Boolean.FALSE);
        return !blocked;
    }

    /** Whether an animal {@code dy} blocks above (negative: below) a visitor's feet is within seeing height. */
    public static boolean withinHeight(double dy) {
        return dy <= MAX_ABOVE && -dy <= MAX_BELOW;
    }
}
