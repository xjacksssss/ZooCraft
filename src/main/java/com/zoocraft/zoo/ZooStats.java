package com.zoocraft.zoo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/** Attendance and feedback counters for one zoo. Mutated only through {@link ZooManager}. */
public final class ZooStats {
    public static final Codec<ZooStats> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("totalAttendance").forGetter(s -> s.totalAttendance),
            Codec.INT.fieldOf("attendanceToday").forGetter(s -> s.attendanceToday),
            Codec.LONG.fieldOf("dayNumber").forGetter(s -> s.dayNumber),
            Codec.LONG.fieldOf("refusedAdmissions").forGetter(s -> s.refusedAdmissions),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("commentTally").forGetter(s -> s.commentTally),
            Codec.LONG.optionalFieldOf("litterCollected", 0L).forGetter(s -> s.litterCollected),
            Codec.DOUBLE.optionalFieldOf("cleanliness", 100.0).forGetter(s -> s.cleanliness)
    ).apply(i, ZooStats::new));

    long totalAttendance;
    int attendanceToday;
    long dayNumber;
    long refusedAdmissions;
    final Map<String, Integer> commentTally;
    long litterCollected;
    /** 0 to 100, built from what visitors see (Phase 2 spec section 9.4). */
    double cleanliness;

    public ZooStats() {
        this(0, 0, 0, 0, Map.of(), 0, 100);
    }

    private ZooStats(long totalAttendance, int attendanceToday, long dayNumber, long refusedAdmissions, Map<String, Integer> commentTally,
                     long litterCollected, double cleanliness) {
        this.totalAttendance = totalAttendance;
        this.attendanceToday = attendanceToday;
        this.dayNumber = dayNumber;
        this.refusedAdmissions = refusedAdmissions;
        this.commentTally = new HashMap<>(commentTally);
        this.litterCollected = litterCollected;
        this.cleanliness = cleanliness;
    }

    public double cleanliness() {
        return cleanliness;
    }

    /** Pieces of litter players have picked up, ever. */
    public long litterCollected() {
        return litterCollected;
    }

    public long totalAttendance() {
        return totalAttendance;
    }

    public int attendanceToday() {
        return attendanceToday;
    }

    public long dayNumber() {
        return dayNumber;
    }

    public long refusedAdmissions() {
        return refusedAdmissions;
    }

    /** Comment type key to count over the current in-game day. */
    public Map<String, Integer> commentTally() {
        return Map.copyOf(commentTally);
    }

    /** Starts a new day's counters when the in-game day has changed. */
    void rollDay(long currentDay) {
        if (currentDay != dayNumber) {
            dayNumber = currentDay;
            attendanceToday = 0;
            commentTally.clear();
        }
    }
}
