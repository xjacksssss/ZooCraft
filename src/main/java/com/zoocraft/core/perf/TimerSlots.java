package com.zoocraft.core.perf;

/**
 * Fixed timer slots with a rolling window of per-tick totals (Phase 3 spec section 6.1). Not thread-safe: used on
 * the server thread only. Nothing allocates after construction.
 */
public final class TimerSlots {
    public static final int WINDOW = 600;

    private final int window;
    private final long[] current;
    private final long[] currentCalls;
    private final long[][] history;
    private final long[][] historyCalls;
    private final long[] sums;
    private final long[] callSums;
    private int cursor;
    private int filled;
    private int depth;

    public TimerSlots(int slots, int window) {
        this.window = window;
        this.current = new long[slots];
        this.currentCalls = new long[slots];
        this.history = new long[slots][window];
        this.historyCalls = new long[slots][window];
        this.sums = new long[slots];
        this.callSums = new long[slots];
    }

    /** Entering a timed section. */
    public void open() {
        depth++;
    }

    /** Leaving a timed section: its time goes to {@code slot}, and to {@code totalSlot} when it was the outermost. */
    public void close(int slot, long nanos, int totalSlot) {
        add(slot, nanos);
        depth = Math.max(0, depth - 1);
        if (depth == 0) {
            current[totalSlot] += nanos;
            currentCalls[totalSlot]++;
        }
    }

    public void add(int slot, long nanos) {
        current[slot] += nanos;
        currentCalls[slot]++;
    }

    /** Closes the current tick into the window. */
    public void endTick() {
        for (int s = 0; s < current.length; s++) {
            sums[s] += current[s] - history[s][cursor];
            callSums[s] += currentCalls[s] - historyCalls[s][cursor];
            history[s][cursor] = current[s];
            historyCalls[s][cursor] = currentCalls[s];
            current[s] = 0;
            currentCalls[s] = 0;
        }
        cursor = (cursor + 1) % window;
        filled = Math.min(filled + 1, window);
    }

    public int ticks() {
        return filled;
    }

    public double averageNanos(int slot) {
        return filled == 0 ? 0 : (double) sums[slot] / filled;
    }

    /** Average per tick over the most recent {@code lastTicks} ticks (or all recorded ticks, if fewer). */
    public double averageNanos(int slot, int lastTicks) {
        int n = Math.min(lastTicks, filled);
        if (n == 0) {
            return 0;
        }
        long sum = 0;
        for (int i = 1; i <= n; i++) {
            sum += history[slot][Math.floorMod(cursor - i, window)];
        }
        return (double) sum / n;
    }

    public long worstNanos(int slot) {
        long worst = 0;
        for (int i = 1; i <= filled; i++) {
            worst = Math.max(worst, history[slot][Math.floorMod(cursor - i, window)]);
        }
        return worst;
    }

    public long calls(int slot) {
        return callSums[slot];
    }

    /** Calls in the most recent {@code lastTicks} ticks (or all recorded ticks, if fewer). */
    public long calls(int slot, int lastTicks) {
        int n = Math.min(lastTicks, filled);
        long sum = 0;
        for (int i = 1; i <= n; i++) {
            sum += historyCalls[slot][Math.floorMod(cursor - i, window)];
        }
        return sum;
    }

    public int depth() {
        return depth;
    }
}
