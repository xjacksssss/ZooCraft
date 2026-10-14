package com.zoocraft.zoo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Moves real emerald items in and out of a player's inventory. Emerald blocks count as 9. */
public final class Emeralds {
    private Emeralds() {
    }

    public static long count(Inventory inventory) {
        long total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(Items.EMERALD)) {
                total += stack.getCount();
            } else if (stack.is(Items.EMERALD_BLOCK)) {
                total += stack.getCount() * 9L;
            }
        }
        return total;
    }

    /**
     * Removes exactly {@code amount} emeralds' worth, using loose emeralds first, then breaking emerald blocks
     * and returning the change. Callers must check {@link #count} first; returns false and removes nothing if short.
     */
    public static boolean remove(ServerPlayer player, long amount) {
        Inventory inventory = player.getInventory();
        if (amount <= 0 || count(inventory) < amount) {
            return false;
        }
        long remaining = amount;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(Items.EMERALD)) {
                int take = (int) Math.min(stack.getCount(), remaining);
                stack.shrink(take);
                remaining -= take;
            }
        }
        long blocksNeeded = (remaining + 8) / 9;
        long change = blocksNeeded * 9 - remaining;
        for (int slot = 0; slot < inventory.getContainerSize() && blocksNeeded > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(Items.EMERALD_BLOCK)) {
                int take = (int) Math.min(stack.getCount(), blocksNeeded);
                stack.shrink(take);
                blocksNeeded -= take;
            }
        }
        give(player, change);
        inventory.setChanged();
        return true;
    }

    /** Gives emeralds, dropping at the player's feet anything that does not fit. */
    public static void give(ServerPlayer player, long amount) {
        long remaining = amount;
        while (remaining > 0) {
            int size = (int) Math.min(64, remaining);
            ItemStack stack = new ItemStack(Items.EMERALD, size);
            remaining -= size;
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
    }
}
