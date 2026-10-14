package com.zoocraft.core.shop;

/** A shop's physical stock, seen by item ID. {@link #canTake} must not change anything. */
public interface StockSource {
    boolean canTake(String itemId);

    /** Removes one unit. Returns false, having removed nothing, when that isn't possible. */
    boolean take(String itemId);

    /** Puts back the unit removed by the last successful {@link #take}. */
    void undoTake();
}
