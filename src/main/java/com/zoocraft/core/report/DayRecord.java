package com.zoocraft.core.report;

import java.util.ArrayList;
import java.util.List;

/**
 * One in-game day's totals for a zoo (Phase 2 spec section 10.1). Today's record is added to as things happen;
 * closed days don't change. Saved as a plain list of numbers, so fields can be added at the end later and older
 * saves read them as 0.
 */
public final class DayRecord {
    public long day;
    public long admission;
    public long shopSales;
    public long deposits;
    public long withdrawals;
    public long closingBalance;
    public long arrivals;
    public long refusals;
    public long departures;
    /** Sum of departing visitors' satisfaction, for the average. */
    public long satisfactionTotal;
    /** Everything visitors spent: admission and shops. */
    public long spending;
    /** Sum of visit lengths in ticks, for the average. */
    public long visitTicks;
    public long meals;
    public long toiletVisits;
    public long budgetVisitors;
    public long enthusiastVisitors;
    public long generalVisitors;

    public long income() {
        return admission + shopSales;
    }

    public List<Long> toList() {
        return List.of(day, admission, shopSales, deposits, withdrawals, closingBalance, arrivals, refusals, departures,
                satisfactionTotal, spending, visitTicks, meals, toiletVisits, budgetVisitors, enthusiastVisitors, generalVisitors);
    }

    public static DayRecord fromList(List<Long> values) {
        long[] v = new long[17];
        for (int i = 0; i < Math.min(v.length, values.size()); i++) {
            v[i] = values.get(i);
        }
        DayRecord r = new DayRecord();
        r.day = v[0];
        r.admission = v[1];
        r.shopSales = v[2];
        r.deposits = v[3];
        r.withdrawals = v[4];
        r.closingBalance = v[5];
        r.arrivals = v[6];
        r.refusals = v[7];
        r.departures = v[8];
        r.satisfactionTotal = v[9];
        r.spending = v[10];
        r.visitTicks = v[11];
        r.meals = v[12];
        r.toiletVisits = v[13];
        r.budgetVisitors = v[14];
        r.enthusiastVisitors = v[15];
        r.generalVisitors = v[16];
        return r;
    }

    public DayRecord copy() {
        return fromList(new ArrayList<>(toList()));
    }
}
