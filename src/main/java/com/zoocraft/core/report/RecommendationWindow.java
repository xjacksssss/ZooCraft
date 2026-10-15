package com.zoocraft.core.report;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Whether the last {@link #SIZE} departing visitors would recommend the zoo (Phase 2 spec section 10.2): those who
 * left with satisfaction of at least {@link #THRESHOLD}.
 */
public final class RecommendationWindow {
    public static final int SIZE = 100;
    public static final double THRESHOLD = 70;

    private final Deque<Boolean> departures = new ArrayDeque<>();

    public void add(double satisfaction) {
        departures.addLast(satisfaction >= THRESHOLD);
        while (departures.size() > SIZE) {
            departures.removeFirst();
        }
    }

    public int size() {
        return departures.size();
    }

    public int recommended() {
        int count = 0;
        for (boolean yes : departures) {
            if (yes) {
                count++;
            }
        }
        return count;
    }

    /** "1" or "0" per departure, oldest first. */
    public String encode() {
        StringBuilder text = new StringBuilder(departures.size());
        departures.forEach(yes -> text.append(yes ? '1' : '0'));
        return text.toString();
    }

    public static RecommendationWindow decode(String text) {
        RecommendationWindow window = new RecommendationWindow();
        for (int i = 0; i < text.length(); i++) {
            window.departures.addLast(text.charAt(i) == '1');
        }
        while (window.departures.size() > SIZE) {
            window.departures.removeFirst();
        }
        return window;
    }
}
