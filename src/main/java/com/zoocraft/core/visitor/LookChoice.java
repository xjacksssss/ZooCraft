package com.zoocraft.core.visitor;

/**
 * Where a visitor looks next (Phase 3 spec section 7.6). A rare glance at a nearby player can interrupt anything;
 * otherwise exhibit animals while viewing, the way ahead (or an animal being passed) while walking, now and then the
 * group leader, and otherwise a random direction.
 */
public final class LookChoice {
    public enum Kind { PLAYER, EXHIBIT_ANIMAL, PASSING_ANIMAL, AHEAD, LEADER, RANDOM }

    /**
     * @param playerNear    a player within {@link #PLAYER_RANGE}
     * @param playerAllowed the visitor's player-glance cooldown is over
     * @param exhibitAnimal an animal of the exhibit the visitor is visiting is within viewing distance
     * @param walking       the visitor is following a path
     * @param passingAnimal an animal of another exhibit is within viewing distance
     * @param hasLeader     the visitor is a group member whose leader is exploring
     */
    public record Input(boolean playerNear, boolean playerAllowed, boolean exhibitAnimal, boolean walking, boolean passingAnimal,
                        boolean hasLeader) {
    }

    public static final double PLAYER_RANGE = 6;
    public static final double PLAYER_CHANCE = 0.1;
    public static final double PASSING_CHANCE = 0.3;
    public static final double LEADER_CHANCE = 0.2;
    /** How long a visitor looks at a player who right-clicks it. */
    public static final int INTERACT_LOOK_TICKS = 60;

    private LookChoice() {
    }

    /** @param roll1 decides the player glance, {@code roll2} the other chances; both in [0, 1) */
    public static Kind choose(Input in, double roll1, double roll2) {
        if (in.playerNear() && in.playerAllowed() && roll1 < PLAYER_CHANCE) {
            return Kind.PLAYER;
        }
        if (in.exhibitAnimal()) {
            return Kind.EXHIBIT_ANIMAL;
        }
        if (in.walking()) {
            return in.passingAnimal() && roll2 < PASSING_CHANCE ? Kind.PASSING_ANIMAL : Kind.AHEAD;
        }
        if (in.hasLeader() && roll2 < LEADER_CHANCE) {
            return Kind.LEADER;
        }
        return Kind.RANDOM;
    }

    /** How long to keep looking, in ticks. */
    public static int duration(Kind kind, double roll) {
        return switch (kind) {
            case PLAYER, AHEAD, RANDOM -> between(20, 40, roll);
            case EXHIBIT_ANIMAL -> between(60, 120, roll);
            case PASSING_ANIMAL, LEADER -> between(40, 60, roll);
        };
    }

    /** Ticks after a glance before the visitor may look at a player again: 20 to 40 seconds. */
    public static int playerCooldown(double roll) {
        return between(400, 800, roll);
    }

    private static int between(int min, int max, double roll) {
        return min + (int) Math.floor(roll * (max - min + 1));
    }
}
