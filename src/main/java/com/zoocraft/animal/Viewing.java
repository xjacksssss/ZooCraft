package com.zoocraft.animal;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Proximity viewing (spec section 8.4): close enough, and one ray not stopped by a solid opaque block.
 * Glass, fences, bars and leaves don't block the view; walls do.
 */
public final class Viewing {
    public static final double MAX_VERTICAL = 3.0;

    private Viewing() {
    }

    public static boolean canSee(Level level, Vec3 eye, Vec3 feet, Entity animal, int radius) {
        double dx = animal.getX() - feet.x;
        double dz = animal.getZ() - feet.z;
        if (dx * dx + dz * dz > (double) radius * radius || Math.abs(animal.getY() - feet.y) > MAX_VERTICAL) {
            return false;
        }
        Vec3 target = animal.position().add(0, animal.getBbHeight() * 0.6, 0);
        Boolean blocked = BlockGetter.traverseBlocks(eye, target, level,
                (lvl, pos) -> lvl.getBlockState(pos).canOcclude() ? Boolean.TRUE : null,
                lvl -> Boolean.FALSE);
        return !blocked;
    }
}
