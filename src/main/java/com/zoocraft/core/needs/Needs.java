package com.zoocraft.core.needs;

import java.util.random.RandomGenerator;

/**
 * A visitor's hunger and need for the toilet, each 0 to 100 (Phase 2 spec section 8.1). They rise at a rate rolled
 * per visitor, so visitors don't all get hungry at once.
 */
public final class Needs {
    public static final double URGENT = 85;
    public static final double FULL = 100;
    public static final double OVERDUE_SECONDS = 60;
    public static final double FOOD_RELIEF = 60;
    public static final double DRINK_RELIEF = 35;

    private double hunger;
    private double toilet;
    private final double hungerRate;
    private final double toiletRate;
    private double hungerFullSeconds;
    private double toiletFullSeconds;

    public Needs(double hunger, double toilet, double hungerRate, double toiletRate, double hungerFullSeconds, double toiletFullSeconds) {
        this.hunger = clamp(hunger);
        this.toilet = clamp(toilet);
        this.hungerRate = hungerRate;
        this.toiletRate = toiletRate;
        this.hungerFullSeconds = hungerFullSeconds;
        this.toiletFullSeconds = toiletFullSeconds;
    }

    public static Needs roll(RandomGenerator random) {
        return new Needs(random.nextDouble() * 30, random.nextDouble() * 20,
                0.14 + random.nextDouble() * 0.11, 0.11 + random.nextDouble() * 0.09, 0, 0);
    }

    public void tick(double seconds) {
        hunger = clamp(hunger + hungerRate * seconds);
        toilet = clamp(toilet + toiletRate * seconds);
        hungerFullSeconds = hunger >= FULL ? hungerFullSeconds + seconds : 0;
        toiletFullSeconds = toilet >= FULL ? toiletFullSeconds + seconds : 0;
    }

    public double level(Need need) {
        return need == Need.HUNGER ? hunger : toilet;
    }

    public boolean wants(Need need, double threshold) {
        return level(need) >= threshold;
    }

    public boolean urgent(Need need) {
        return level(need) >= URGENT;
    }

    /** The need has been at its maximum for {@link #OVERDUE_SECONDS}: the visitor gives up and leaves. */
    public boolean overdue(Need need) {
        return (need == Need.HUNGER ? hungerFullSeconds : toiletFullSeconds) >= OVERDUE_SECONDS;
    }

    public void eat(boolean drink) {
        hunger = clamp(hunger - (drink ? DRINK_RELIEF : FOOD_RELIEF));
        hungerFullSeconds = 0;
    }

    public void useToilet() {
        toilet = 0;
        toiletFullSeconds = 0;
    }

    public void set(Need need, double value) {
        if (need == Need.HUNGER) {
            hunger = clamp(value);
            hungerFullSeconds = 0;
        } else {
            toilet = clamp(value);
            toiletFullSeconds = 0;
        }
    }

    public double hunger() {
        return hunger;
    }

    public double toilet() {
        return toilet;
    }

    public double hungerRate() {
        return hungerRate;
    }

    public double toiletRate() {
        return toiletRate;
    }

    public double hungerFullSeconds() {
        return hungerFullSeconds;
    }

    public double toiletFullSeconds() {
        return toiletFullSeconds;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(FULL, value));
    }
}
