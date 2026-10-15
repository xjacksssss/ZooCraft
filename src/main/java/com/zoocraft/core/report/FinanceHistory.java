package com.zoocraft.core.report;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A zoo's last {@link #DAYS} in-game days (Phase 2 spec section 10.1): today's running totals and the closed days
 * before it. Days with nothing loaded (the server was off) are simply absent rather than invented as empty.
 */
public final class FinanceHistory {
    public static final int DAYS = 28;
    private static final long UNSET = Long.MIN_VALUE;

    private DayRecord today;
    private final Deque<DayRecord> past = new ArrayDeque<>();

    public FinanceHistory() {
        this.today = new DayRecord();
        this.today.day = UNSET;
    }

    /** Restores saved history; {@code past} is oldest first. */
    public FinanceHistory(DayRecord today, List<DayRecord> past) {
        this.today = today;
        this.past.addAll(past);
        trim();
    }

    public DayRecord today() {
        return today;
    }

    /** Closed days, oldest first. */
    public List<DayRecord> past() {
        return List.copyOf(past);
    }

    /** Starts a new day if {@code day} differs from today's, closing today with {@code balance}. */
    public void roll(long day, long balance) {
        if (today.day == day) {
            return;
        }
        if (today.day != UNSET) {
            today.closingBalance = balance;
            past.addLast(today);
            trim();
        }
        today = new DayRecord();
        today.day = day;
    }

    /** The last {@code count} days including today, oldest first. */
    public List<DayRecord> lastDays(int count) {
        List<DayRecord> all = new ArrayList<>(past);
        all.add(today);
        return List.copyOf(all.subList(Math.max(0, all.size() - count), all.size()));
    }

    private void trim() {
        while (past.size() > DAYS - 1) {
            past.removeFirst();
        }
    }
}
