package com.zoocraft.zoo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/** Sales figures for one shop. Today's counters roll over with the in-game day. Mutated only through {@link ZooManager}. */
public final class ShopStats {
    public static final Codec<ShopStats> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("revenueTotal").forGetter(ShopStats::revenueTotal),
            Codec.LONG.fieldOf("unitsTotal").forGetter(ShopStats::unitsTotal),
            Codec.LONG.fieldOf("dayNumber").forGetter(s -> s.dayNumber),
            Codec.LONG.fieldOf("revenueToday").forGetter(ShopStats::revenueToday),
            Codec.INT.fieldOf("unitsToday").forGetter(ShopStats::unitsToday),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("soldToday").forGetter(s -> s.soldToday),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("missedToday").forGetter(s -> s.missedToday)
    ).apply(i, ShopStats::new));

    private long revenueTotal;
    private long unitsTotal;
    private long dayNumber;
    private long revenueToday;
    private int unitsToday;
    private final Map<String, Integer> soldToday;
    private final Map<String, Integer> missedToday;

    public ShopStats() {
        this(0, 0, 0, 0, 0, Map.of(), Map.of());
    }

    public ShopStats(long revenueTotal, long unitsTotal, long dayNumber, long revenueToday, int unitsToday,
                     Map<String, Integer> soldToday, Map<String, Integer> missedToday) {
        this.revenueTotal = revenueTotal;
        this.unitsTotal = unitsTotal;
        this.dayNumber = dayNumber;
        this.revenueToday = revenueToday;
        this.unitsToday = unitsToday;
        this.soldToday = new HashMap<>(soldToday);
        this.missedToday = new HashMap<>(missedToday);
    }

    public long revenueTotal() {
        return revenueTotal;
    }

    public long unitsTotal() {
        return unitsTotal;
    }

    public long revenueToday() {
        return revenueToday;
    }

    public int unitsToday() {
        return unitsToday;
    }

    /** Item ID to units sold today. */
    public Map<String, Integer> soldToday() {
        return Map.copyOf(soldToday);
    }

    /** {@code "<reason>|<itemId>"} to purchases that failed today. */
    public Map<String, Integer> missedToday() {
        return Map.copyOf(missedToday);
    }

    /** Missed sales today for one item, whatever the reason. */
    public int missedToday(String itemId) {
        int total = 0;
        for (Map.Entry<String, Integer> e : missedToday.entrySet()) {
            if (e.getKey().endsWith("|" + itemId)) {
                total += e.getValue();
            }
        }
        return total;
    }

    void rollDay(long currentDay) {
        if (currentDay != dayNumber) {
            dayNumber = currentDay;
            revenueToday = 0;
            unitsToday = 0;
            soldToday.clear();
            missedToday.clear();
        }
    }

    void recordSale(String itemId, int price) {
        revenueTotal += price;
        unitsTotal++;
        revenueToday += price;
        unitsToday++;
        soldToday.merge(itemId, 1, Integer::sum);
    }

    void recordMissed(String reason, String itemId) {
        missedToday.merge(reason + "|" + itemId, 1, Integer::sum);
    }
}
