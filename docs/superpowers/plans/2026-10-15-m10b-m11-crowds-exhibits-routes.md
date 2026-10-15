# M10b + M11 Crowds, Exhibits and Routes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Visitors spread out at viewing spots, gate queues and shop counters, recover from crowding, and look at the animals rather than at players (spec 7.2 to 7.6). Exhibits get enclosures found by a bounded flood fill, viewing spots just outside the enclosure edge, a full-height animal search, fairer route choice and a plain-language rating (spec 8). Done when the acceptance tests in spec 7.7 and 8.6 pass and the harness shows, for Big Park: 200 visitors add at most 8 ms over the 0-visitor baseline with P99 under 50 ms, no visitor stuck for more than 60 seconds, at least 95% of exhibits viewed in 20 minutes at 100 visitors with none above 4 times the median, and the share of path plans that fall short reported (it should drop sharply from 80%).

**Architecture:** One generic, unit-tested claim counter (`core.crowd.Occupancy`) backs viewing-spot claims (held by each `Exhibit`), gate queue slots (`visitor.GateQueues`) and shop counters plus toilets (`shop.FacilityOccupancy`, replacing `ToiletOccupancy`). Choices that use the counts are pure functions (`core.choice.SpotChoice`, `core.crowd.GateChoice`, `core.choice.ExhibitChoice`, `core.visitor.LookChoice`). Enclosures come from a pure `core.exhibit.EnclosureFill` over a `FloorGrid` interface; `animal.LevelFloorGrid` adapts the world, `AnimalCensus` queues fills and runs at most `enclosureFillsPerTick` per tick under `PerfSlot.ENCLOSURE`, and `core.exhibit.ViewingSpots` picks spots along the enclosure edge. `core.exhibit.ExhibitRating` turns the enclosure stats and visit history into a score and warnings shown on the Animals tab. A new `ChooseLookTarget` SmartBrainLib behaviour replaces the player/random look pair.

**Tech Stack:** Java 21, NeoForge 21.1.251, SmartBrainLib 1.16.11, JUnit 5, NeoForge GameTests, the `tools/perf` harness.

**Spec:** `docs/superpowers/specs/2026-10-15-phase3-scale-compatibility-design.md` (sections 1, 3.4, 5, 7.2 to 7.7, 8, 10, 12)

**Execution note:** Autonomous build (user memory). Native execution on branch `phase3`, one commit per task. Every deviation from this plan or the spec goes in the ledger at the end with a `Ruling:` line.

## Scope decisions made before starting

- The re-plan gap and the follow-range bump in spec 8.4 are not built: M10a measured them as unnecessary and the follow range stays 48. The plans-per-visitor-minute figure is added to `/zoo debug perf`.
- Gate queue slots are computed when first needed and cached per gate, and the cache is dropped whenever the zoo changes (gates, boundary). They are not saved with the gate. Same behaviour as "found when designated and refreshed when the boundary changes", without changing the `Gate` codec.
- Exhibit warnings for cramped and bare pens are shown under the exhibit on the Animals tab. The existing "can't see" warning stays an alert as well. Big parks would otherwise fill the alert list.

## Global Constraints

- New config keys (spec section 10), read live: `animals.maxViewingSpots` 12, `animals.viewingSpotCapacity` 2, `animals.maxEnclosureArea` 1,024, `animals.enclosureFillsPerTick` 2, `shops.counterCapacity` 2.
- `zoocraft:natural_floor` block tag: grass, dirt, coarse dirt, podzol, rooted dirt, mud, moss, sand, red sand, gravel, snow, clay, mycelium, `#minecraft:leaves`, optional `#c:` entries.
- Player-facing text in British English, plain language, naming the cause and place ("The {species} exhibit is too crowded. Give visitors more room to see it.", "{shop} is too busy. Add another counter or shop nearby.", "It's too crowded to see anything.", "The queue at {shop} was too long.", "The {species} exhibit looks cramped.", "The {species} exhibit has bare ground. Natural surroundings would help.").
- A sale still happens in one step at the counter (P2 7.5); occupancy only decides who stands there. Admission is unchanged (P1 7.4).
- Claims are runtime only, expire unless renewed, and are released when a visitor leaves its target, chooses again, leaves the zoo or is removed.
- Ratings never harm animals and nudge satisfaction by at most ±1 on first viewing.
- Visitors read caches only: no entity searches for looking, choosing or claiming. Fills never run for every visitor.
- GameTests: never call `runAfterDelay` or `onEachTick` from inside a scheduled task; schedule up front or use one `onEachTick` state machine. Stone bricks, not gravel, for paths on the test floor. Wait about 3 ticks after spawning before expecting a path. Tests that change config go in batch `config`.

## Review Focus

- **A visitor removed while holding claims** (killed, unloaded, released at the gate): its spot, counter and gate slot must free at once, not after expiry. Task 7 and Task 10 GameTests discard a claiming visitor and check the count drops.
- **A shop or exhibit that disappears while visitors queue or wait for it** (shop deleted, animals moved): waiting visitors must give up and carry on, never wait forever. Task 7 deletes a shop under a waiting visitor.
- **Pens bigger than the cap, open gates, animals in water or flying**: the fill must fall back to the open-exhibit sampling and the exhibit must still get spots. Task 3 covers the open gate and a start above the floor; Task 9 checks an unfenced exhibit still has spots.
- **Config values changed mid-run** (`maxViewingSpots` lowered below current claims, `counterCapacity` 1): no crash, no negative counts, claims above the new capacity simply aren't renewed beyond it. Task 1 tests a capacity drop.
- **Zoo closing while visitors wait at counters, spots or gate slots**: everyone leaves within the existing limits. Task 8's closing test runs with visitors exploring and errands active.

---

### Task 1: Occupancy, SpotChoice and GateChoice (pure)

**Files:**
- Create: `src/main/java/com/zoocraft/core/crowd/Occupancy.java`, `src/main/java/com/zoocraft/core/crowd/GateChoice.java`, `src/main/java/com/zoocraft/core/choice/SpotChoice.java`
- Test: `src/test/java/com/zoocraft/core/crowd/OccupancyTest.java`, `src/test/java/com/zoocraft/core/crowd/GateChoiceTest.java`, `src/test/java/com/zoocraft/core/choice/SpotChoiceTest.java`

**Interfaces:**
- Produces: `Occupancy<K>(long expiryTicks)` with `boolean claim(K, UUID, long now, int capacity)`, `void release(K, UUID)`, `void releaseAll(UUID)`, `int count(K, long now)`, `boolean holds(K, UUID)`, `int maxCount(long now)`, `void clear()`; `SpotChoice.Spot(int occupied, int capacity, boolean path, boolean unreachable, double distance)` and `int SpotChoice.pick(List<Spot>)`; `int GateChoice.pick(double[] distances, int[] heading)`.

- [ ] **Step 1: Failing tests**

```java
// OccupancyTest
class OccupancyTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);

    @Test
    void claimsUpToCapacityAndRenews() {
        Occupancy<String> o = new Occupancy<>(100);
        assertTrue(o.claim("spot", A, 0, 2));
        assertTrue(o.claim("spot", B, 0, 2));
        assertFalse(o.claim("spot", C, 0, 2));
        assertTrue(o.claim("spot", A, 50, 2), "a holder can always renew");
        assertEquals(2, o.count("spot", 50));
    }

    @Test
    void releaseAndReleaseAllFreePlaces() {
        Occupancy<String> o = new Occupancy<>(100);
        o.claim("x", A, 0, 2);
        o.claim("y", A, 0, 2);
        o.claim("x", B, 0, 2);
        o.release("x", B);
        assertEquals(1, o.count("x", 0));
        o.releaseAll(A);
        assertEquals(0, o.count("x", 0));
        assertEquals(0, o.count("y", 0));
        assertFalse(o.holds("y", A));
    }

    @Test
    void unrenewedClaimsExpire() {
        Occupancy<String> o = new Occupancy<>(100);
        o.claim("spot", A, 0, 1);
        assertFalse(o.claim("spot", B, 100, 1), "still held at exactly the expiry");
        assertTrue(o.claim("spot", B, 101, 1));
        assertFalse(o.holds("spot", A));
    }

    @Test
    void loweredCapacityKeepsHoldersButAdmitsNoOne() {
        Occupancy<String> o = new Occupancy<>(100);
        o.claim("spot", A, 0, 2);
        o.claim("spot", B, 0, 2);
        assertTrue(o.claim("spot", A, 1, 1));
        assertFalse(o.claim("spot", C, 1, 1));
        assertEquals(2, o.maxCount(1));
    }
}

// SpotChoiceTest
class SpotChoiceTest {
    @Test
    void picksTheLeastOccupied() {
        assertEquals(1, SpotChoice.pick(List.of(new SpotChoice.Spot(1, 2, false, false, 1), new SpotChoice.Spot(0, 2, false, false, 9))));
    }

    @Test
    void prefersPathsAmongTiesThenTheNearest() {
        List<SpotChoice.Spot> spots = List.of(new SpotChoice.Spot(0, 2, false, false, 1), new SpotChoice.Spot(0, 2, true, false, 8),
                new SpotChoice.Spot(0, 2, true, false, 5));
        assertEquals(2, SpotChoice.pick(spots));
    }

    @Test
    void skipsUnreachableSpotsAndReportsNone() {
        assertEquals(1, SpotChoice.pick(List.of(new SpotChoice.Spot(0, 2, true, true, 1), new SpotChoice.Spot(1, 2, false, false, 1))));
        assertEquals(-1, SpotChoice.pick(List.of(new SpotChoice.Spot(0, 2, true, true, 1))));
        assertEquals(-1, SpotChoice.pick(List.of()));
    }

    @Test
    void fullSpotsAreStillRankedSoTheCallerCanWait() {
        assertEquals(0, SpotChoice.pick(List.of(new SpotChoice.Spot(2, 2, false, false, 1), new SpotChoice.Spot(3, 2, false, false, 1))));
        assertTrue(SpotChoice.full(new SpotChoice.Spot(2, 2, false, false, 1)));
    }
}

// GateChoiceTest
class GateChoiceTest {
    @Test
    void nearestGateWithoutCrowds() {
        assertEquals(0, GateChoice.pick(new double[]{10, 30}, new int[]{0, 0}));
    }

    @Test
    void aGatesWeightHalvesForEveryEightHeadingToIt() {
        // 10 blocks with 16 heading there counts as 40; 30 blocks with none counts as 30.
        assertEquals(1, GateChoice.pick(new double[]{10, 30}, new int[]{16, 0}));
        assertEquals(0, GateChoice.pick(new double[]{10, 30}, new int[]{8, 0}));
        assertEquals(-1, GateChoice.pick(new double[]{}, new int[]{}));
    }
}
```

Run: `./gradlew test --tests "com.zoocraft.core.crowd.*" --tests "com.zoocraft.core.choice.SpotChoiceTest"`. Expected: compilation failure (classes missing).

- [ ] **Step 2: Implement**

```java
package com.zoocraft.core.crowd;

/**
 * Who holds a place at each spot (Phase 3 spec section 5.1): viewing spots, gate queue slots and counters. A claim
 * lasts {@code expiryTicks} unless renewed, so a holder that vanishes never blocks a spot for long. Not thread-safe;
 * used on the server thread.
 */
public final class Occupancy<K> {
    private final long expiryTicks;
    private final Map<K, Map<UUID, Long>> holders = new HashMap<>();
    private final Map<UUID, Set<K>> heldBy = new HashMap<>();

    public Occupancy(long expiryTicks) { this.expiryTicks = expiryTicks; }

    /** Takes or renews a place. A holder may always renew; others only while fewer than {@code capacity} hold it. */
    public boolean claim(K spot, UUID who, long now, int capacity) {
        Map<UUID, Long> at = holders.computeIfAbsent(spot, k -> new HashMap<>());
        expire(spot, at, now);
        if (!at.containsKey(who) && at.size() >= capacity) {
            return false;
        }
        at.put(who, now);
        heldBy.computeIfAbsent(who, k -> new HashSet<>()).add(spot);
        return true;
    }

    public void release(K spot, UUID who) { ... remove from both maps, drop empty entries ... }
    public void releaseAll(UUID who) { ... for each spot in heldBy.remove(who): remove who ... }
    public int count(K spot, long now) { ... expire then size ... }
    public boolean holds(K spot, UUID who) { ... }
    public int maxCount(long now) { ... expire all, max size ... }
    public void clear() { holders.clear(); heldBy.clear(); }

    private void expire(K spot, Map<UUID, Long> at, long now) {
        at.entrySet().removeIf(e -> {
            if (now - e.getValue() > expiryTicks) {
                Set<K> spots = heldBy.get(e.getKey());
                if (spots != null) { spots.remove(spot); if (spots.isEmpty()) heldBy.remove(e.getKey()); }
                return true;
            }
            return false;
        });
    }
}
```

```java
package com.zoocraft.core.choice;

/** Which viewing spot a visitor takes (spec 7.2): least occupied, then path blocks, then the nearest. */
public final class SpotChoice {
    public record Spot(int occupied, int capacity, boolean path, boolean unreachable, double distance) { }

    public static boolean full(Spot s) { return s.occupied() >= s.capacity(); }

    /** @return the chosen index, or -1 when every spot is unreachable */
    public static int pick(List<Spot> spots) {
        int best = -1;
        for (int i = 0; i < spots.size(); i++) {
            Spot s = spots.get(i);
            if (s.unreachable()) continue;
            if (best < 0 || better(s, spots.get(best))) best = i;
        }
        return best;
    }

    private static boolean better(Spot a, Spot b) {
        if (a.occupied() != b.occupied()) return a.occupied() < b.occupied();
        if (a.path() != b.path()) return a.path();
        return a.distance() < b.distance();
    }
}
```

```java
package com.zoocraft.core.crowd;

/** Which gate a leaving visitor heads for (spec 7.3): the nearest, with a gate's weight halving for every 8 heading to it. */
public final class GateChoice {
    public static final double HALVING = 8.0;

    /** @return the index of the chosen gate, or -1 when there are none */
    public static int pick(double[] distances, int[] heading) {
        int best = -1;
        double bestCost = Double.MAX_VALUE;
        for (int i = 0; i < distances.length; i++) {
            double cost = distances[i] * Math.pow(2, heading[i] / HALVING);
            if (cost < bestCost) { bestCost = cost; best = i; }
        }
        return best;
    }
}
```

- [ ] **Step 3: Run tests** (same command). Expected: PASS.
- [ ] **Step 4: Commit** `git commit -m "M10b: Occupancy, SpotChoice and GateChoice"`

---

### Task 2: LookChoice (pure)

**Files:**
- Create: `src/main/java/com/zoocraft/core/visitor/LookChoice.java`
- Test: `src/test/java/com/zoocraft/core/visitor/LookChoiceTest.java`

**Interfaces:**
- Produces: `LookChoice.Kind { PLAYER, EXHIBIT_ANIMAL, PASSING_ANIMAL, AHEAD, LEADER, RANDOM }`, `LookChoice.Input(boolean playerNear, boolean playerAllowed, boolean exhibitAnimal, boolean walking, boolean passingAnimal, boolean hasLeader)`, `Kind choose(Input, double roll1, double roll2)`, `int duration(Kind, double roll)` (ticks), `int playerCooldown(double roll)` (ticks), constants `PLAYER_RANGE = 6`, `INTERACT_LOOK_TICKS = 60`.

- [ ] **Step 1: Failing tests**

```java
class LookChoiceTest {
    private static LookChoice.Input in(boolean player, boolean allowed, boolean exhibit, boolean walking, boolean passing, boolean leader) {
        return new LookChoice.Input(player, allowed, exhibit, walking, passing, leader);
    }

    @Test
    void aNearbyPlayerOnlyGetsAGlanceOneTimeInTen() {
        assertEquals(LookChoice.Kind.PLAYER, LookChoice.choose(in(true, true, true, false, false, false), 0.05, 0.5));
        assertEquals(LookChoice.Kind.EXHIBIT_ANIMAL, LookChoice.choose(in(true, true, true, false, false, false), 0.15, 0.5));
    }

    @Test
    void noGlanceDuringTheCooldown() {
        assertNotEquals(LookChoice.Kind.PLAYER, LookChoice.choose(in(true, false, false, false, false, false), 0.0, 0.5));
    }

    @Test
    void walkingVisitorsLookAheadOrAtAnimalsTheyPass() {
        assertEquals(LookChoice.Kind.PASSING_ANIMAL, LookChoice.choose(in(false, true, false, true, true, false), 0.5, 0.2));
        assertEquals(LookChoice.Kind.AHEAD, LookChoice.choose(in(false, true, false, true, true, false), 0.5, 0.4));
        assertEquals(LookChoice.Kind.AHEAD, LookChoice.choose(in(false, true, false, true, false, false), 0.5, 0.1));
    }

    @Test
    void groupMembersSometimesLookAtTheLeader() {
        assertEquals(LookChoice.Kind.LEADER, LookChoice.choose(in(false, true, false, false, false, true), 0.5, 0.1));
        assertEquals(LookChoice.Kind.RANDOM, LookChoice.choose(in(false, true, false, false, false, true), 0.5, 0.3));
        assertEquals(LookChoice.Kind.RANDOM, LookChoice.choose(in(false, true, false, false, false, false), 0.5, 0.1));
    }

    @Test
    void durationsAndCooldownMatchTheSpec() {
        assertEquals(20, LookChoice.duration(LookChoice.Kind.PLAYER, 0.0));
        assertEquals(40, LookChoice.duration(LookChoice.Kind.PLAYER, 0.999), 1);
        assertEquals(60, LookChoice.duration(LookChoice.Kind.EXHIBIT_ANIMAL, 0.0));
        assertEquals(120, LookChoice.duration(LookChoice.Kind.EXHIBIT_ANIMAL, 0.999), 1);
        assertEquals(400, LookChoice.playerCooldown(0.0));
        assertEquals(800, LookChoice.playerCooldown(0.999), 1);
    }
}
```

Run: `./gradlew test --tests "com.zoocraft.core.visitor.LookChoiceTest"`. Expected: compilation failure.

- [ ] **Step 2: Implement**

```java
package com.zoocraft.core.visitor;

/**
 * Where a visitor looks next (Phase 3 spec section 7.6). A rare glance at a nearby player can interrupt anything;
 * otherwise exhibit animals while viewing, the way ahead (or an animal being passed) while walking, the group
 * leader now and then, and a random direction.
 */
public final class LookChoice {
    public enum Kind { PLAYER, EXHIBIT_ANIMAL, PASSING_ANIMAL, AHEAD, LEADER, RANDOM }

    /** @param playerAllowed the visitor's player-glance cooldown is over */
    public record Input(boolean playerNear, boolean playerAllowed, boolean exhibitAnimal, boolean walking, boolean passingAnimal,
                        boolean hasLeader) { }

    public static final double PLAYER_RANGE = 6;
    public static final double PLAYER_CHANCE = 0.1;
    public static final double PASSING_CHANCE = 0.3;
    public static final double LEADER_CHANCE = 0.2;
    public static final int INTERACT_LOOK_TICKS = 60;

    public static Kind choose(Input in, double roll1, double roll2) {
        if (in.playerNear() && in.playerAllowed() && roll1 < PLAYER_CHANCE) return Kind.PLAYER;
        if (in.exhibitAnimal()) return Kind.EXHIBIT_ANIMAL;
        if (in.walking()) return in.passingAnimal() && roll2 < PASSING_CHANCE ? Kind.PASSING_ANIMAL : Kind.AHEAD;
        if (in.hasLeader() && roll2 < LEADER_CHANCE) return Kind.LEADER;
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

    /** Ticks after a glance before the visitor may look at a player again. */
    public static int playerCooldown(double roll) { return between(400, 800, roll); }

    private static int between(int min, int max, double roll) { return min + (int) Math.floor(roll * (max - min + 1)); }
}
```

- [ ] **Step 3: Run tests.** Expected: PASS.
- [ ] **Step 4: Commit** `git commit -m "M10b: LookChoice"`

---

### Task 3: EnclosureFill (pure)

**Files:**
- Create: `src/main/java/com/zoocraft/core/exhibit/FloorGrid.java`, `Enclosure.java`, `EnclosureFill.java`
- Test: `src/test/java/com/zoocraft/core/exhibit/EnclosureFillTest.java`, `src/test/java/com/zoocraft/core/exhibit/GridFixture.java`

**Interfaces:**
- Produces: `FloorGrid.stepTo(int x, int z, int fromY)` returning feet height or `FloorGrid.NO_FLOOR`; `Enclosure(boolean bounded, Map<Long, Integer> floor)` with `area()`, `contains(x, z)`, `feetY(x, z)`, `edge()` (list of `int[]{x, y, z}` floor cells with a 4-neighbour outside), `centreX()`, `centreZ()`, `static long key(int x, int z)`; `Enclosure EnclosureFill.fill(FloorGrid, List<int[]> starts, int maxArea)`.

- [ ] **Step 1: Fixture and failing tests**

`GridFixture` builds a `FloorGrid` from rows of characters: `.` floor at height 0, digits `1`-`9` floor at that height, `#` fence (no floor), `o` an open gate (floor at 0). Column x is the character index, z the row index. Outside the rows is open floor at height 0, so anything that escapes runs on until the cap.

```java
final class GridFixture implements FloorGrid {
    private final String[] rows;
    GridFixture(String... rows) { this.rows = rows; }

    int height(int x, int z) {
        if (z < 0 || z >= rows.length || x < 0 || x >= rows[z].length()) return 0;
        char c = rows[z].charAt(x);
        if (c == '#') return NO_FLOOR;
        return Character.isDigit(c) ? c - '0' : 0;
    }

    @Override
    public int stepTo(int x, int z, int fromY) {
        int h = height(x, z);
        return h != NO_FLOOR && Math.abs(h - fromY) <= 1 ? h : NO_FLOOR;
    }
}

class EnclosureFillTest {
    static String[] pen(int w, int d) { ... "#" * (w + 2) top and bottom rows, "#" + "." * w + "#" between ... }

    @Test
    void closedEightByEightPenIsBounded() {
        Enclosure e = EnclosureFill.fill(new GridFixture(pen(8, 8)), List.of(new int[]{4, 0, 4}), 1024);
        assertTrue(e.bounded());
        assertEquals(64, e.area());
        assertEquals(28, e.edge().size());
    }

    @Test
    void twentyByTwentyPenIsBounded() { ... 400 ... }

    @Test
    void lShapedPen() {
        GridFixture g = new GridFixture(
                "######",
                "#....#",
                "#....#",
                "#..###",
                "#..#",
                "####");
        Enclosure e = EnclosureFill.fill(g, List.of(new int[]{1, 0, 1}), 1024);
        assertTrue(e.bounded());
        assertEquals(12, e.area());
    }

    @Test
    void anOpenGateLetsTheFillEscapeToTheCap() {
        String[] rows = pen(8, 8);
        rows[0] = "####o#####";
        Enclosure e = EnclosureFill.fill(new GridFixture(rows), List.of(new int[]{4, 0, 4}), 200);
        assertFalse(e.bounded());
        assertEquals(200, e.area());
    }

    @Test
    void stepsOfOneBlockAreClimbedButTwoAreNot() {
        GridFixture g = new GridFixture(
                "#######",
                "#..1..#",
                "#..#..#",
                "#######");
        // Height 1 in the middle of row 1 is climbable, so both halves join.
        assertEquals(9, EnclosureFill.fill(g, List.of(new int[]{1, 0, 1}), 1024).area());
        GridFixture cliff = new GridFixture(
                "#######",
                "#..2..#",
                "#..#..#",
                "#######");
        assertEquals(4, EnclosureFill.fill(cliff, List.of(new int[]{1, 0, 1}), 1024).area());
    }

    @Test
    void aStartAboveTheFloorDropsToIt() {
        Enclosure e = EnclosureFill.fill(new GridFixture(pen(4, 4)), List.of(new int[]{2, 3, 2}), 1024);
        assertEquals(16, e.area(), "a flying or jumping animal still finds its pen");
    }

    @Test
    void noFloorAtAllIsOpen() {
        Enclosure e = EnclosureFill.fill((x, z, y) -> FloorGrid.NO_FLOOR, List.of(new int[]{0, 0, 0}), 1024);
        assertFalse(e.bounded());
        assertEquals(0, e.area());
    }
}
```

Run: `./gradlew test --tests "com.zoocraft.core.exhibit.*"`. Expected: compilation failure.

- [ ] **Step 2: Implement**

```java
/** The floor animals can walk on, as seen by {@link EnclosureFill}. */
@FunctionalInterface
public interface FloorGrid {
    int NO_FLOOR = Integer.MIN_VALUE;
    /** Feet height on column (x, z) for an animal arriving from feet height {@code fromY} (at most one block up or down), or NO_FLOOR. */
    int stepTo(int x, int z, int fromY);
}

/** An exhibit's enclosure (spec 8.1): its floor columns and whether the fill stopped by itself. */
public record Enclosure(boolean bounded, Map<Long, Integer> floor) {
    public static final Enclosure NONE = new Enclosure(false, Map.of());
    public static long key(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }
    public int area() { return floor.size(); }
    public boolean contains(int x, int z) { return floor.containsKey(key(x, z)); }
    public int feetY(int x, int z) { return floor.getOrDefault(key(x, z), FloorGrid.NO_FLOOR); }
    /** Floor cells with at least one of their four neighbours outside the enclosure. */
    public List<int[]> edge() { ... }
    public double centreX() { ... mean x + 0.5 ... }
    public double centreZ() { ... }
}

/** Bounded flood fill over the floor animals can walk on (spec 8.1, decision 4.4). */
public final class EnclosureFill {
    private static final int START_DROP = 4;

    public static Enclosure fill(FloorGrid grid, List<int[]> starts, int maxArea) {
        Map<Long, Integer> floor = new HashMap<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int[] s : starts) {
            int y = startHeight(grid, s[0], s[1], s[2]);
            if (y != FloorGrid.NO_FLOOR && floor.size() < maxArea && floor.putIfAbsent(Enclosure.key(s[0], s[2]), y) == null) {
                queue.add(new int[]{s[0], y, s[2]});
            }
        }
        if (floor.isEmpty()) return Enclosure.NONE;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], nz = c[2] + d[1];
                long k = Enclosure.key(nx, nz);
                if (floor.containsKey(k)) continue;
                int ny = grid.stepTo(nx, nz, c[1]);
                if (ny == FloorGrid.NO_FLOOR) continue;
                if (floor.size() >= maxArea) return new Enclosure(false, Map.copyOf(floor));
                floor.put(k, ny);
                queue.add(new int[]{nx, ny, nz});
            }
        }
        return new Enclosure(floor.size() < maxArea, Map.copyOf(floor));
    }

    /** The animal's own floor, or the floor up to a few blocks below a flying or jumping animal. */
    private static int startHeight(FloorGrid grid, int x, int y, int z) {
        for (int from = y; from >= y - START_DROP; from--) {
            int h = grid.stepTo(x, z, from);
            if (h != FloorGrid.NO_FLOOR && h <= y) return h;
        }
        return FloorGrid.NO_FLOOR;
    }
}
```

Note: a fill that exactly fills `maxArea` with nothing left to spread to is open (`floor.size() < maxArea` is false). A 32 by 32 pen at the default cap of 1,024 therefore counts as open, matching "up to 32 by 32" only for pens strictly smaller; the spec calls 32 by 32 the edge of the range, and the open fallback still gives it spots.

- [ ] **Step 3: Run tests.** Expected: PASS.
- [ ] **Step 4: Commit** `git commit -m "M11: EnclosureFill over a FloorGrid"`

---

### Task 4: ViewingSpots (pure)

**Files:**
- Create: `src/main/java/com/zoocraft/core/exhibit/SpotGrid.java`, `src/main/java/com/zoocraft/core/exhibit/ViewingSpots.java`
- Test: `src/test/java/com/zoocraft/core/exhibit/ViewingSpotsTest.java`

**Interfaces:**
- Consumes: `Enclosure` (Task 3).
- Produces: `SpotGrid.standY(int x, int z, int nearY)` (visitor feet height within 2 of `nearY`, inside the zoo, or `FloorGrid.NO_FLOOR`), `SpotGrid.path(int x, int y, int z)`; `List<int[]> ViewingSpots.pick(Enclosure, SpotGrid, int maxSpots)` returning `{x, y, z}` spots.

- [ ] **Step 1: Failing tests**

```java
class ViewingSpotsTest {
    /** An 8 by 8 pen at x 0..7, z 0..7, fenced at x -1 and 8, z -1 and 8; visitors stand anywhere else; a path along z = 9. */
    private static Enclosure pen() {
        Map<Long, Integer> floor = new HashMap<>();
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) floor.put(Enclosure.key(x, z), 0);
        return new Enclosure(true, floor);
    }

    private static final SpotGrid GRID = new SpotGrid() {
        public int standY(int x, int z, int nearY) {
            boolean fence = (x == -1 || x == 8) && z >= -1 && z <= 8 || (z == -1 || z == 8) && x >= -1 && x <= 8;
            boolean inside = x >= 0 && x < 8 && z >= 0 && z < 8;
            return fence || inside ? FloorGrid.NO_FLOOR : 0;
        }
        public boolean path(int x, int y, int z) { return z == 9; }
    };

    @Test
    void spotsAreOutsideTheFenceWithinTwoBlocks() {
        List<int[]> spots = ViewingSpots.pick(pen(), GRID, 12);
        assertEquals(12, spots.size());
        for (int[] s : spots) {
            assertFalse(s[0] >= -1 && s[0] <= 8 && s[2] >= -1 && s[2] <= 8, "spot inside or on the fence: " + Arrays.toString(s));
            int dx = Math.max(Math.max(-s[0], s[0] - 7), 0), dz = Math.max(Math.max(-s[2], s[2] - 7), 0);
            assertTrue(Math.max(dx, dz) <= 2, "spot too far from the edge: " + Arrays.toString(s));
        }
    }

    @Test
    void spotsSpreadRoundThePen() {
        List<int[]> spots = ViewingSpots.pick(pen(), GRID, 8);
        long north = spots.stream().filter(s -> s[2] < 0).count(), south = spots.stream().filter(s -> s[2] > 7).count();
        long west = spots.stream().filter(s -> s[0] < 0).count(), east = spots.stream().filter(s -> s[0] > 7).count();
        assertTrue(north > 0 && south > 0 && west > 0 && east > 0, "not spread: " + north + " " + south + " " + west + " " + east);
    }

    @Test
    void pathBlocksWinOnTheirSide() {
        List<int[]> spots = ViewingSpots.pick(pen(), GRID, 8);
        assertTrue(spots.stream().filter(s -> s[2] > 7).allMatch(s -> s[2] == 9), "a grass spot beat the path on the south side");
    }

    @Test
    void noStandableGroundGivesNoSpots() {
        assertTrue(ViewingSpots.pick(pen(), (x, z, y) -> FloorGrid.NO_FLOOR, 12).isEmpty());
    }
}
```

`SpotGrid` needs a functional shape for the last test: make `path` a default method returning false, and `standY` the single abstract method.

Run: `./gradlew test --tests "com.zoocraft.core.exhibit.ViewingSpotsTest"`. Expected: compilation failure.

- [ ] **Step 2: Implement**

```java
/** Where visitors can stand, as seen by {@link ViewingSpots}. */
@FunctionalInterface
public interface SpotGrid {
    /** Visitor feet height on column (x, z) within 2 blocks of {@code nearY}, inside the zoo, or FloorGrid.NO_FLOOR. */
    int standY(int x, int z, int nearY);
    default boolean path(int x, int y, int z) { return false; }
}

/**
 * Viewing spots along an enclosure's edge (spec 8.1): standable blocks outside the enclosure, within 2 blocks of its
 * edge, spread round it (one per run of roughly equal length), path blocks first, then the closest to the edge.
 */
public final class ViewingSpots {
    public static final int EDGE_DISTANCE = 2;

    public static List<int[]> pick(Enclosure enclosure, SpotGrid grid, int maxSpots) {
        Map<Long, int[]> candidates = new LinkedHashMap<>();   // key -> {x, y, z, distanceToEdge}
        for (int[] edge : enclosure.edge()) {
            for (int dx = -EDGE_DISTANCE; dx <= EDGE_DISTANCE; dx++) {
                for (int dz = -EDGE_DISTANCE; dz <= EDGE_DISTANCE; dz++) {
                    int x = edge[0] + dx, z = edge[2] + dz;
                    if (enclosure.contains(x, z)) continue;
                    int d = Math.max(Math.abs(dx), Math.abs(dz));
                    long k = Enclosure.key(x, z);
                    int[] known = candidates.get(k);
                    if (known != null) { known[3] = Math.min(known[3], d); continue; }
                    int y = grid.standY(x, z, edge[1]);
                    if (y != FloorGrid.NO_FLOOR) candidates.put(k, new int[]{x, y, z, d});
                }
            }
        }
        if (candidates.isEmpty() || maxSpots <= 0) return List.of();
        double cx = enclosure.centreX(), cz = enclosure.centreZ();
        List<int[]> ordered = new ArrayList<>(candidates.values());
        ordered.sort(Comparator.comparingDouble(c -> Math.atan2(c[2] + 0.5 - cz, c[0] + 0.5 - cx)));
        int n = Math.min(maxSpots, ordered.size());
        List<int[]> spots = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            List<int[]> run = ordered.subList(i * ordered.size() / n, (i + 1) * ordered.size() / n);
            int[] best = run.stream().min(Comparator.<int[]>comparingInt(c -> grid.path(c[0], c[1], c[2]) ? 0 : 1)
                    .thenComparingInt(c -> c[3])).orElseThrow();
            spots.add(new int[]{best[0], best[1], best[2]});
        }
        return spots;
    }
}
```

- [ ] **Step 3: Run tests.** Expected: PASS.
- [ ] **Step 4: Commit** `git commit -m "M11: ViewingSpots along an enclosure edge"`

---

### Task 5: ExhibitChoice with route scale, popularity and crowding (pure)

**Files:**
- Modify: `src/main/java/com/zoocraft/core/choice/ExhibitOption.java`, `ExhibitChoice.java`
- Test: `src/test/java/com/zoocraft/core/choice/ExhibitChoiceTest.java`

**Interfaces:**
- Produces: `ExhibitOption(Set<String> species, double distance, boolean recentlyVisited, double routeScale, int viewsToday, int medianViewsToday, double occupiedShare, boolean full)` plus the old three-argument constructor (route scale 16, no views, no crowd); `static double ExhibitChoice.routeScale(int zooWidth, int zooDepth)`; `pick` unchanged.

- [ ] **Step 1: Failing tests** (added to `ExhibitChoiceTest`)

```java
    @Test
    void farExhibitsInABigZooAreNotStarved() {
        double scale = ExhibitChoice.routeScale(240, 240); // 60
        ExhibitOption near = new ExhibitOption(Set.of("cow"), 10, false, scale, 0, 0, 0, false);
        ExhibitOption far = new ExhibitOption(Set.of("cow"), 150, false, scale, 0, 0, 0, false);
        double ratio = ExhibitChoice.weight(near, Set.of(), false) / ExhibitChoice.weight(far, Set.of(), false);
        assertTrue(ratio < 3.0, "near/far ratio " + ratio);
        assertEquals(16.0, ExhibitChoice.routeScale(30, 40));
    }

    @Test
    void overViewedExhibitsLoseWeight() {
        ExhibitOption popular = new ExhibitOption(Set.of("cow"), 10, false, 16, 40, 10, 0, false);
        ExhibitOption ignored = new ExhibitOption(Set.of("cow"), 10, false, 16, 0, 10, 0, false);
        assertTrue(ExhibitChoice.weight(ignored, Set.of(), false) > 2 * ExhibitChoice.weight(popular, Set.of(), false));
    }

    @Test
    void aFullExhibitDropsToATenth() {
        ExhibitOption full = new ExhibitOption(Set.of("cow"), 10, false, 16, 0, 0, 1.0, true);
        ExhibitOption empty = new ExhibitOption(Set.of("cow"), 10, false, 16, 0, 0, 0, false);
        assertEquals(0.1, ExhibitChoice.weight(full, Set.of(), false) / ExhibitChoice.weight(empty, Set.of(), false), 1e-9);
        ExhibitOption half = new ExhibitOption(Set.of("cow"), 10, false, 16, 0, 0, 0.5, false);
        assertEquals(0.75, ExhibitChoice.weight(half, Set.of(), false) / ExhibitChoice.weight(empty, Set.of(), false), 1e-9);
    }
```

Run: `./gradlew test --tests "com.zoocraft.core.choice.*"`. Expected: compilation failure.

- [ ] **Step 2: Implement**

```java
/** One exhibit a visitor could go to next (spec 8.3). */
public record ExhibitOption(Set<String> species, double distance, boolean recentlyVisited, double routeScale, int viewsToday,
                            int medianViewsToday, double occupiedShare, boolean full) {
    public ExhibitOption(Set<String> species, double distance, boolean recentlyVisited) {
        this(species, distance, recentlyVisited, ExhibitChoice.MIN_ROUTE_SCALE, 0, 0, 0, false);
    }
}
```

```java
    public static final double MIN_ROUTE_SCALE = 16;

    /** A quarter of the zoo's larger dimension, at least 16 blocks. */
    public static double routeScale(int width, int depth) {
        return Math.max(MIN_ROUTE_SCALE, Math.max(width, depth) / 4.0);
    }

    /** base × unseenBonus × recentPenalty × distanceFactor × popularityFactor × crowdFactor */
    public static double weight(ExhibitOption option, Set<String> seen, boolean enthusiast) {
        long unseen = option.species().stream().filter(s -> !seen.contains(s)).count();
        double weight = 1.0 + unseen * (enthusiast ? 6.0 : 3.0);
        if (option.recentlyVisited()) weight *= 0.2;
        weight /= 1.0 + option.distance() / option.routeScale();
        weight /= Math.sqrt(1.0 + option.viewsToday() / (option.medianViewsToday() + 1.0));
        weight *= option.full() ? 0.1 : 1.0 - 0.5 * option.occupiedShare();
        return weight;
    }
```

- [ ] **Step 3: Run tests.** Expected: PASS (old tests unchanged).
- [ ] **Step 4: Commit** `git commit -m "M11: exhibit choice weighs route scale, popularity and crowding"`

---

### Task 6: ExhibitRating (pure)

**Files:**
- Create: `src/main/java/com/zoocraft/core/exhibit/ExhibitRating.java`
- Test: `src/test/java/com/zoocraft/core/exhibit/ExhibitRatingTest.java`

**Interfaces:**
- Produces: `ExhibitRating.Input(int spots, int spotsReached, boolean viewedFromSpots, int attempts, int species, boolean open, int floorBlocks, double animalWeight, double naturalShare, boolean waterOrLeaves)`; `ExhibitRating.Result(int score, Grade grade, Set<Warning> warnings)`; `enum Grade { EXCELLENT, GOOD, FAIR, POOR }`; `enum Warning { CRAMPED, BARE, CANT_SEE }`; `Result rate(Input)`; constants `FULL_SPACE = 12`, `CRAMPED_BELOW = 6`, `BARE_BELOW = 0.25`, `CANT_SEE_BELOW = 0.25`, `MIN_ATTEMPTS = 10`.

- [ ] **Step 1: Failing tests**

```java
class ExhibitRatingTest {
    private static ExhibitRating.Input input(int spots, int reached, boolean viewed, int attempts, int species, boolean open, int floor,
                                             double animals, double natural, boolean water) {
        return new ExhibitRating.Input(spots, reached, viewed, attempts, species, open, floor, animals, natural, water);
    }

    @Test
    void aGoodPenScoresExcellent() {
        ExhibitRating.Result r = ExhibitRating.rate(input(12, 12, true, 20, 3, false, 64, 2, 1.0, true));
        assertEquals(100, r.score());
        assertEquals(ExhibitRating.Grade.EXCELLENT, r.grade());
        assertTrue(r.warnings().isEmpty());
    }

    @Test
    void varietySteps() {
        assertEquals(60 * 20 / 100, ExhibitRating.variety(1) * 20, 1e-9);
        assertEquals(0.85, ExhibitRating.variety(2), 1e-9);
        assertEquals(1.0, ExhibitRating.variety(5), 1e-9);
    }

    @Test
    void spaceIsFullAtTwelveAndHalfAtSix() {
        assertEquals(1.0, ExhibitRating.space(false, 24, 2), 1e-9);
        assertEquals(0.5, ExhibitRating.space(false, 12, 2), 1e-9);
        assertEquals(1.0, ExhibitRating.space(true, 0, 5), 1e-9, "open exhibits get full marks");
    }

    @Test
    void crampedBelowSixPerAnimal() {
        ExhibitRating.Result r = ExhibitRating.rate(input(4, 4, true, 20, 1, false, 9, 4, 1.0, false));
        assertTrue(r.warnings().contains(ExhibitRating.Warning.CRAMPED));
    }

    @Test
    void bareGroundBelowAQuarterNatural() {
        assertTrue(ExhibitRating.rate(input(4, 4, true, 20, 1, false, 64, 2, 0.2, false)).warnings().contains(ExhibitRating.Warning.BARE));
        assertFalse(ExhibitRating.rate(input(4, 4, true, 20, 1, false, 64, 2, 0.2, true)).warnings().contains(ExhibitRating.Warning.BARE),
                "water or leaves lift the surroundings above the bare line");
    }

    @Test
    void cantSeeOnlyAfterTenAttempts() {
        assertFalse(ExhibitRating.rate(input(8, 0, false, 9, 1, false, 64, 2, 1, false)).warnings().contains(ExhibitRating.Warning.CANT_SEE));
        assertTrue(ExhibitRating.rate(input(8, 1, false, 10, 1, false, 64, 2, 1, false)).warnings().contains(ExhibitRating.Warning.CANT_SEE));
    }

    @Test
    void noAttemptsYetMeansNoAccessPenalty() {
        assertEquals(1.0, ExhibitRating.access(8, 0, false, 0), 1e-9);
        assertEquals(0.5, ExhibitRating.access(8, 8, false, 10), 1e-9, "reached but nothing seen counts half");
    }

    @Test
    void gradesFollowTheScore() {
        assertEquals(ExhibitRating.Grade.GOOD, ExhibitRating.grade(60));
        assertEquals(ExhibitRating.Grade.FAIR, ExhibitRating.grade(59));
        assertEquals(ExhibitRating.Grade.POOR, ExhibitRating.grade(39));
        assertEquals(ExhibitRating.Grade.EXCELLENT, ExhibitRating.grade(80));
    }
}
```

Run: `./gradlew test --tests "com.zoocraft.core.exhibit.ExhibitRatingTest"`. Expected: compilation failure.

- [ ] **Step 2: Implement**

```java
/**
 * An exhibit's rating from 0 to 100 (spec 8.5): viewing access 40%, variety 20%, space 25%, surroundings 15%. Generic
 * rules only, so nothing species-specific is invented (GDD 5.3). Grades: Excellent from 80, Good from 60, Fair from 40.
 */
public final class ExhibitRating {
    public enum Grade { EXCELLENT, GOOD, FAIR, POOR }
    public enum Warning { CRAMPED, BARE, CANT_SEE }
    public record Input(int spots, int spotsReached, boolean viewedFromSpots, int attempts, int species, boolean open, int floorBlocks,
                        double animalWeight, double naturalShare, boolean waterOrLeaves) { }
    public record Result(int score, Grade grade, Set<Warning> warnings) {
        public static final Result UNRATED = new Result(100, Grade.EXCELLENT, Set.of());
    }

    public static final double FULL_SPACE = 12, CRAMPED_BELOW = 6, BARE_BELOW = 0.25, CANT_SEE_BELOW = 0.25, WATER_BONUS = 0.25;
    public static final int MIN_ATTEMPTS = 10;

    public static Result rate(Input in) {
        double access = access(in.spots(), in.spotsReached(), in.viewedFromSpots(), in.attempts());
        double surroundings = Math.min(1.0, in.naturalShare() + (in.waterOrLeaves() ? WATER_BONUS : 0));
        double space = space(in.open(), in.floorBlocks(), in.animalWeight());
        int score = (int) Math.round(100 * (0.40 * access + 0.20 * variety(in.species()) + 0.25 * space + 0.15 * surroundings));
        EnumSet<Warning> warnings = EnumSet.noneOf(Warning.class);
        if (!in.open() && in.animalWeight() > 0 && in.floorBlocks() / in.animalWeight() < CRAMPED_BELOW) warnings.add(Warning.CRAMPED);
        if (surroundings < BARE_BELOW) warnings.add(Warning.BARE);
        if (in.attempts() >= MIN_ATTEMPTS && access < CANT_SEE_BELOW) warnings.add(Warning.CANT_SEE);
        return new Result(score, grade(score), Set.copyOf(warnings));
    }

    /** Share of spots reached recently, halved when nobody saw an animal from them. Unknown until visitors try. */
    public static double access(int spots, int reached, boolean viewed, int attempts) {
        if (attempts == 0) return 1.0;
        if (spots == 0) return 0.0;
        return Math.min(1.0, (double) reached / spots) * (viewed ? 1.0 : 0.5);
    }

    public static double variety(int species) { return species >= 3 ? 1.0 : species == 2 ? 0.85 : 0.6; }

    public static double space(boolean open, int floor, double animalWeight) {
        if (open || animalWeight <= 0) return 1.0;
        return Math.min(1.0, floor / animalWeight / FULL_SPACE);
    }

    public static Grade grade(int score) {
        return score >= 80 ? Grade.EXCELLENT : score >= 60 ? Grade.GOOD : score >= 40 ? Grade.FAIR : Grade.POOR;
    }
}
```

`varietySteps` asserts 0.6 via `variety(1) * 20 == 12`.

- [ ] **Step 3: Run tests.** Expected: PASS.
- [ ] **Step 4: Commit** `git commit -m "M11: ExhibitRating"`

---

### Task 7: Shop counters share the toilet-style occupancy

**Files:**
- Create: `src/main/java/com/zoocraft/shop/FacilityOccupancy.java` (replaces `ToiletOccupancy.java`, deleted)
- Modify: `src/main/java/com/zoocraft/config/ZooConfig.java` (`COUNTER_CAPACITY`), `feedback/CommentType.java` (`SHOP_QUEUE`), `visitor/VisitorErrands.java`, `visitor/Visitor.java` (release on removal), `dashboard/AdviceBuilder.java`, `core/report/Advice.java` (weight), `src/main/resources/assets/zoocraft/lang/en_us.json`
- Test: `src/main/java/com/zoocraft/gametest/NeedsGameTests.java` (toilet tests switch to `FacilityOccupancy`; new counter tests)

**Interfaces:**
- Consumes: `Occupancy` (Task 1).
- Produces: `FacilityOccupancy.tryOccupy(UUID facility, UUID visitor, long now, int capacity)`, `release(UUID, UUID)`, `releaseAll(UUID visitor)`, `count(UUID, long now)`, `maxCount(long now)`; `ZooConfig.COUNTER_CAPACITY` (`shops.counterCapacity`, default 2, 1 to 32); `CommentType.SHOP_QUEUE`; `VisitorErrands.releaseClaims()`.

Behaviour:
- Choosing a shop: if a counter place can be claimed now, walk to the counter. Otherwise walk to a waiting spot (a standable block 2 to 3 blocks from the counter inside the zoo, the one nearest the visitor; the counter itself if there is none) and set `counterWaitSince`.
- While errand FOOD or SOUVENIR is active, the claim is renewed every step.
- Arriving at the waiting spot: retry the claim every 2 seconds (`RETRY_TICKS = 40`). On success, walk to the counter. After `COUNTER_WAIT_TICKS` (20 seconds): add the shop to `triedShops`; if another shop sells what the visitor wants it goes there, otherwise `remark(SHOP_QUEUE, shopId)` and give up food for the cooldown.
- Arriving at the counter: buy as now; the claim is kept through `SERVE_TICKS` and released at the next `choose`.
- The shop disappearing while waiting: `choose` sees it gone from the zoo and gives up the errand.
- Visitor removed (`Visitor.remove`): `FacilityOccupancy.releaseAll(uuid)`.
- `tickHeld`'s eating sound and particles fire at most every 10 ticks whatever the step (deferred M9 minor), via `lastHeldEffect`.

- [ ] **Step 1: Failing GameTests** (add to `NeedsGameTests`; tests that change `counterCapacity` use `batch = "config"`)

```java
    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1600, batch = "config")
    public static void aOnePlaceCounterServesTwoVisitorsInTurn(GameTestHelper helper) {
        int original = ZooConfig.COUNTER_CAPACITY.get();
        ZooConfig.COUNTER_CAPACITY.set(1);
        Scene s = scene(helper, Items.BREAD, 5, 10, 70);
        Visitor second = extraHungryVisitor(helper, s, START.offset(0, 0, 2));
        helper.succeedWhen(() -> {
            helper.assertTrue(TestShops.count(helper, CHEST, Items.BREAD) == 3, "both should have bought one bread");
            ZooConfig.COUNTER_CAPACITY.set(original);
            second.discard();
            finish(helper, s);
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1200)
    public static void aFullCounterMakesTheVisitorComplainAboutTheQueue(GameTestHelper helper) {
        Scene s = scene(helper, Items.BREAD, 5, 10, 70);
        UUID shop = s.zoo().shops().getFirst().id();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            FacilityOccupancy.tryOccupy(shop, a, now, 2);
            FacilityOccupancy.tryOccupy(shop, b, now, 2);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(tally(s.zoo(), CommentType.SHOP_QUEUE) >= 1, "no queue complaint yet");
            helper.assertTrue(TestShops.count(helper, CHEST, Items.BREAD) == 5, "bought through a full counter");
            helper.assertTrue(s.zoo().reports().subjectsToday().containsKey("shop_queue|" + shop), "complaint doesn't name the shop");
            finish(helper, s);
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1600)
    public static void aVisitorWaitingTooLongTriesAnotherShop(GameTestHelper helper) {
        // Shop A (COUNTER) is kept full; shop B further east also sells bread. The visitor ends up buying at B.
        Scene s = scene(helper, Items.BREAD, 5, 10, 70);
        BlockPos counterB = new BlockPos(40, 2, 30), chestB = new BlockPos(41, 2, 30);
        TestShops.assertOk(helper, s.zoos().createShop(s.owner(), helper.getLevel(), helper.absolutePos(counterB)));
        TestShops.placeChest(helper, chestB, Items.BREAD, 5);
        TestShops.assertOk(helper, s.zoos().linkContainer(s.owner(), helper.getLevel(), helper.absolutePos(chestB)));
        FacilityCensus.refresh(s.zoo(), helper.getLevel());
        UUID shopA = s.zoo().shops().getFirst().id();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            FacilityOccupancy.tryOccupy(shopA, a, now, 2);
            FacilityOccupancy.tryOccupy(shopA, b, now, 2);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(TestShops.count(helper, chestB, Items.BREAD) == 4, "didn't buy at the other shop");
            helper.assertTrue(TestShops.count(helper, CHEST, Items.BREAD) == 5, "bought through a full counter");
            finish(helper, s);
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 400)
    public static void removingAVisitorFreesItsCounterPlace(GameTestHelper helper) {
        Scene s = scene(helper, Items.BREAD, 5, 10, 70);
        UUID shop = s.zoo().shops().getFirst().id();
        helper.runAfterDelay(3, () -> {
            long now = helper.getLevel().getGameTime();
            helper.assertTrue(FacilityOccupancy.tryOccupy(shop, s.visitor().getUUID(), now, 2), "claim refused");
            s.visitor().discard();
        });
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(FacilityOccupancy.count(shop, helper.getLevel().getGameTime()) == 0, "place still held after removal");
            TestZoos.cleanUp(helper, s.owner(), s.zoo());
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 800)
    public static void aShopDeletedUnderAWaitingVisitorIsGivenUp(GameTestHelper helper) {
        Scene s = scene(helper, Items.BREAD, 5, 10, 70);
        UUID shop = s.zoo().shops().getFirst().id();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (s.zoo().shop(shop).isPresent()) {
                FacilityOccupancy.tryOccupy(shop, a, now, 2);
                FacilityOccupancy.tryOccupy(shop, b, now, 2);
            }
        });
        helper.runAfterDelay(200, () -> s.zoos().removeShop(s.owner(), helper.getLevel(), helper.absolutePos(COUNTER)));
        helper.runAfterDelay(400, () -> {
            helper.assertTrue(s.visitor().errands().current() != Errand.FOOD, "still waiting for a deleted shop");
            finish(helper, s);
        });
    }
```

`extraHungryVisitor` is a helper in `NeedsGameTests` that spawns a second exploring visitor in the same zoo with hunger 70, budget 10. Check `ZooManager` for the exact shop-removal method name before writing the last test (`removeShop` or the designation tool path) and use it. Replace `ToiletOccupancy` with `FacilityOccupancy` in the two toilet tests. Remove the meaningless `|| true` assertion line when writing it.

Run: `./gradlew compileJava`. Expected: compilation failure (`FacilityOccupancy`, `COUNTER_CAPACITY`, `SHOP_QUEUE` missing).

- [ ] **Step 2: Implement**

`FacilityOccupancy`:

```java
/**
 * Who is using each toilet point and shop counter (Phase 2 spec 8.4, Phase 3 spec 7.3): an availability model, not
 * individual stalls or tills. Claims expire unless renewed, so a visitor that vanishes never blocks a facility for long,
 * and removed visitors release theirs at once. Runtime only.
 */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class FacilityOccupancy {
    public static final long EXPIRY_TICKS = 10 * 20;
    private static final Occupancy<UUID> PLACES = new Occupancy<>(EXPIRY_TICKS);

    public static boolean tryOccupy(UUID facilityId, UUID visitorId, long now, int capacity) { return PLACES.claim(facilityId, visitorId, now, capacity); }
    public static void release(UUID facilityId, UUID visitorId) { PLACES.release(facilityId, visitorId); }
    public static void releaseAll(UUID visitorId) { PLACES.releaseAll(visitorId); }
    public static int count(UUID facilityId, long now) { return PLACES.count(facilityId, now); }
    public static int maxCount(long now) { return PLACES.maxCount(now); }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) { PLACES.clear(); }
}
```

`ZooConfig` in `shops`:

```java
        COUNTER_CAPACITY = b.comment("How many visitors one shop counter serves at once. The others wait nearby.")
                .defineInRange("counterCapacity", 2, 1, 32);
```

`CommentType`: `SHOP_QUEUE(-4, true),` after `TOILET_QUEUE`. Lang: `"zoocraft.comment.shop_queue": "The queue here was too long."`, `"zoocraft.advice.shop_queue": "%s is too busy. Add another counter or shop nearby."`. (The comment shows above the visitor, so "here" names the shop; the advice names it.) `Advice.WEIGHTS`: `Map.entry("shop_queue", 2)`. `AdviceBuilder` subject switch: `case "shop_queue" -> shop(zoo, subject).ifPresent(shop -> add(issues, text, kind, count, Component.translatable("zoocraft.advice.shop_queue", shop.name())));`

`VisitorErrands` changes:

```java
    static final long COUNTER_WAIT_TICKS = 20 * 20;
    static final long COUNTER_RETRY_TICKS = 40;
    private @Nullable BlockPos counterPos;
    private @Nullable BlockPos waitPos;
    private long counterWaitSince;      // 0 when not waiting
    private boolean atCounter;          // heading for (or at) the counter itself, holding a place
    private long lastHeldEffect = Long.MIN_VALUE / 2;
```

- `startShopping` (CHOSEN): store `counterPos`, then `return Optional.of(approachCounter(zoo, level, now))` where

```java
    /** Claims a counter place and heads for the counter, or heads for a waiting spot nearby when it is full. */
    private BlockPos approachCounter(Zoo zoo, ServerLevel level, long now) {
        if (FacilityOccupancy.tryOccupy(shopId, visitor.getUUID(), now, ZooConfig.COUNTER_CAPACITY.get())) {
            atCounter = true;
            counterWaitSince = 0;
            return counterPos;
        }
        atCounter = false;
        if (counterWaitSince == 0) counterWaitSince = now;
        if (waitPos == null) waitPos = waitingSpot(zoo, level, counterPos).orElse(counterPos);
        return waitPos;
    }

    /** A standable block 2 to 3 blocks from the counter, inside the zoo, nearest the visitor. */
    private Optional<BlockPos> waitingSpot(Zoo zoo, ServerLevel level, BlockPos counter) {
        BlockPos best = null;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            int d = Math.max(Math.abs(dx), Math.abs(dz));
            if (d < 2) continue;
            Optional<BlockPos> feet = Standing.findStandable(level, counter.offset(dx, 0, dz), 1);
            if (feet.isPresent() && VisitorController.goodSpot(level, zoo, feet.get())
                    && (best == null || feet.get().distSqr(visitor.blockPosition()) < best.distSqr(visitor.blockPosition()))) best = feet.get();
        }
        return Optional.ofNullable(best);
    }
```

- `choose`: first, if `(current == FOOD || current == SOUVENIR) && counterWaitSince != 0`: if the shop is gone (`zoo.shop(shopId).isEmpty()`) → `clearCounter(); retryOrGiveUp(current, null, now)` and fall through to normal choosing; else if `now - counterWaitSince >= COUNTER_WAIT_TICKS` → `queueTooLong(zoo, snapshot, now)` (below) and fall through; else `return new Choice(current, Optional.of(approachCounter(zoo, level, now)))`. Before any other choice, release a counter place from the last purchase: `if (!active() && shopId != null) FacilityOccupancy.release(shopId, visitor.getUUID())`.

```java
    /** Waited too long: try another shop that sells what's wanted, or complain about the queue (spec 7.3). */
    private void queueTooLong(Zoo zoo, FacilitySnapshot snapshot, long now) {
        Errand errand = current;
        UUID shop = shopId;
        clearCounter();
        triedShops.add(shop);
        Set<ProductCategory> wanted = errand == Errand.SOUVENIR ? SOUVENIRS : REFRESHMENTS;
        if (!anyOtherShop(zoo, snapshot, wanted, now)) {
            visitor.remark(CommentType.SHOP_QUEUE, shop.toString());
            if (errand == Errand.FOOD) giveUpOnFood(now);
        }
        current = Errand.EXHIBIT; // the next choose() picks the other shop, or something else
    }
```

`anyOtherShop` is `anyStocked` filtered by `!triedShops.contains`. `clearCounter()` releases the place and resets `counterWaitSince`, `waitPos`, `atCounter`.

- `onArrived` for FOOD/SOUVENIR: `if (!atCounter) return COUNTER_RETRY_TICKS;` (the controller then lingers and calls `choose`, which retries the claim) else `buy(...)` as now.
- `renewClaims(long now)` called from `VisitorController.stepExploring` each step: if `atCounter && shopId != null && active()` → `FacilityOccupancy.tryOccupy(shopId, uuid, now, Integer.MAX_VALUE)`.
- `onUnreachable` and `giveUpOnFood` call `clearCounter()`.
- `tickHeld`: wrap the sound and particles in `if (now - lastHeldEffect >= 10) { lastHeldEffect = now; ... }`.
- `releaseClaims()`: `FacilityOccupancy.releaseAll(visitor.getUUID())`.

`Visitor`:

```java
    @Override
    public void remove(RemovalReason reason) {
        releaseClaims();
        super.remove(reason);
    }

    /** Frees every spot, counter, toilet and gate place this visitor holds. */
    void releaseClaims() {
        errands.releaseClaims();
    }
```

(Tasks 8 and 10 add the gate and spot releases to `releaseClaims`.)

`VisitorController.stepExploring`: after `errands.tickRubbish(...)` add `errands.renewClaims(now);`.

- [ ] **Step 3: Build and run everything**

Run: `./gradlew build` then `tools/gametest.sh`. Expected: all pass; count `@GameTest(` goes up by 5.

- [ ] **Step 4: Commit** `git commit -m "M10b: shop counters serve counterCapacity visitors at once; the rest wait nearby, try another shop or complain about the queue"`

---

### Task 8: Gate queue slots and exit-gate weighting

**Files:**
- Create: `src/main/java/com/zoocraft/visitor/GateQueues.java`
- Modify: `visitor/VisitorController.java`, `visitor/Visitor.java` (`releaseClaims`), `visitor/VisitorTracker.java` (drop caches on change)
- Test: `src/main/java/com/zoocraft/gametest/VisitorGameTests.java`

**Interfaces:**
- Consumes: `Occupancy`, `GateChoice` (Task 1).
- Produces: `GateQueues.slots(ServerLevel, Zoo, Gate)` (up to 8 `BlockPos`), `GateQueues.claim(ServerLevel, Zoo, Gate, UUID visitor, @Nullable BlockPos near, long now)` → `Optional<BlockPos>`, `GateQueues.overflow(ServerLevel, Zoo, Gate)`, `GateQueues.release(UUID visitor)`, `GateQueues.forget(Zoo)`, `GateQueues.maxWaiting(long now)`; `VisitorController.queueSlot()` and `exitGateId()` (for tests and the exit weighting).

Behaviour:
- Slots: for distances 2, 3, 4 along the gate's outward normal and offsets 0, ±1, ±2 along the edge, the standable block (within 2 up or down) outside the zoo; first 8 by distance to `gate.outside()`. Cached per gate id; `GateQueues.forget(zoo)` on any zoo change (VisitorTracker's change and boundary listeners).
- Claims: one visitor per slot, expiring after 10 seconds unless renewed. A follower asks for the free slot nearest its leader's slot; a leader (or a visitor without a group) asks for the free slot nearest `gate.outside()`.
- Arriving: walk to the claimed slot; with none free, walk to `overflow` (3 blocks beyond the arc) and retry the claim every step once there. At the overflow point the walk isn't tracked for progress, so waiting never marks the gate blocked. After `QUEUE_WAIT_TICKS * 2` at the overflow point the visitor queues where it stands.
- QUEUEING: renew the slot every step. Entering, being refused, any phase change and removal release it.
- Leaving: `chooseExitGate` builds distances and heading counts (visitors of this zoo in LEAVING with that `exitGateId`) and uses `GateChoice.pick`.

- [ ] **Step 1: Failing GameTests**

```java
    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1200)
    public static void aGroupOfFourQueuesOnSeparateSlots(GameTestHelper helper) {
        Setup setup = openZoo(helper, 0);
        UUID group = UUID.randomUUID();
        Visitor[] members = new Visitor[4];
        for (int i = 0; i < 4; i++) {
            members[i] = visitor(helper, setup, group, i == 0, 4, 20 + i * 2, 10);
        }
        java.util.Map<Visitor, BlockPos> slots = new java.util.HashMap<>();
        helper.onEachTick(() -> {
            for (Visitor v : members) {
                BlockPos slot = v.controller().queueSlot();
                if (slot != null) slots.put(v, slot);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(java.util.Arrays.stream(members).allMatch(v -> v.phase() == VisitorPhase.EXPLORING), "not all admitted yet");
            helper.assertTrue(slots.size() == 4 && new java.util.HashSet<>(slots.values()).size() == 4, "slots shared: " + slots.values());
                helper.assertTrue(java.util.Arrays.stream(members).allMatch(v -> v.controller().queueSlot() == null), "slots kept after entering");
            for (Visitor v : members) v.discard();
            TestZoos.cleanUp(helper, setup.owner(), setup.zoo());
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 2400)
    public static void closingSendsVisitorsToBothGates(GameTestHelper helper) {
        // Two gates on the west edge, 20 blocks apart; all 30 visitors start near the northern one.
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Exits", TestZoos.rectangle(helper, 16, 4, 44, 44));
        ZooManager zoos = TestZoos.manager(helper);
        TestShops.assertOk(helper, zoos.addGate(owner, helper.getLevel(), helper.absolutePos(new BlockPos(16, 2, 12))));
        TestShops.assertOk(helper, zoos.addGate(owner, helper.getLevel(), helper.absolutePos(new BlockPos(16, 2, 32))));
        zoos.setPrice(owner, zoo, 0);
        zoos.setOpen(owner, zoo, true);
        Setup setup = new Setup(owner, zoo, zoo.gates().getFirst());
        List<Visitor> visitors = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            Visitor v = visitor(helper, setup, UUID.randomUUID(), true, 20 + i % 6, 8 + i / 6, 10);
            v.testSetPhase(VisitorPhase.EXPLORING);
            visitors.add(v);
        }
        java.util.Set<UUID> gatesUsed = new java.util.HashSet<>();
        helper.runAfterDelay(20, () -> zoos.setOpen(owner, zoo, false));
        helper.onEachTick(() -> visitors.forEach(v -> { if (v.controller().exitGateId() != null) gatesUsed.add(v.controller().exitGateId()); }));
        helper.succeedWhen(() -> {
            helper.assertTrue(visitors.stream().allMatch(Visitor::isRemoved), "visitors still inside");
            helper.assertTrue(gatesUsed.size() == 2, "only one gate used");
            TestZoos.cleanUp(helper, owner, zoo);
        });
    }
```

`visitor(...)` here is the existing VisitorGameTests helper. The 30-visitor test's timeout (2,400 ticks) is the existing leave limit (60 s) plus the exit walk (20 s) plus margin.

Run: `./gradlew compileJava`. Expected: compilation failure (`queueSlot`, `exitGateId` missing).

- [ ] **Step 2: Implement**

```java
/**
 * Where arriving visitors queue outside each gate (spec 7.3): up to eight standable slots in an arc 2 to 4 blocks
 * outside it, one visitor each, with an overflow point 3 blocks further out. Slots are found when first needed and
 * dropped whenever the zoo changes. Runtime only.
 */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class GateQueues {
    public static final int MAX_SLOTS = 8;
    private static final Map<UUID, List<BlockPos>> SLOTS = new HashMap<>();
    private static final Occupancy<BlockPos> TAKEN = new Occupancy<>(10 * 20);

    public static List<BlockPos> slots(ServerLevel level, Zoo zoo, Gate gate) {
        return SLOTS.computeIfAbsent(gate.id(), id -> find(level, zoo, gate));
    }

    private static List<BlockPos> find(ServerLevel level, Zoo zoo, Gate gate) {
        double[] n = zoo.shape().outwardNormalAt(gate.pos().getX() + 0.5, gate.pos().getZ() + 0.5);
        double tx = -n[1], tz = n[0];
        List<BlockPos> found = new ArrayList<>();
        for (int d = 2; d <= 4; d++) for (int s : new int[]{0, -1, 1, -2, 2}) {
            int x = (int) Math.floor(gate.pos().getX() + 0.5 + n[0] * d + tx * s);
            int z = (int) Math.floor(gate.pos().getZ() + 0.5 + n[1] * d + tz * s);
            if (zoo.shape().containsBlock(x, z) || !level.isLoaded(new BlockPos(x, 0, z))) continue;
            Standing.findStandable(level, new BlockPos(x, gate.pos().getY(), z), 2)
                    .filter(p -> !found.contains(p)).ifPresent(found::add);
        }
        found.sort(Comparator.comparingDouble(p -> p.distSqr(gate.outside())));
        return List.copyOf(found.size() > MAX_SLOTS ? found.subList(0, MAX_SLOTS) : found);
    }

    public static Optional<BlockPos> claim(ServerLevel level, Zoo zoo, Gate gate, UUID visitor, @Nullable BlockPos near, long now) {
        List<BlockPos> slots = slots(level, zoo, gate);
        for (BlockPos slot : slots) if (TAKEN.holds(slot, visitor) && TAKEN.claim(slot, visitor, now, 1)) return Optional.of(slot);
        BlockPos anchor = near != null ? near : gate.outside();
        return slots.stream().filter(s -> TAKEN.count(s, now) == 0).min(Comparator.comparingDouble(s -> s.distSqr(anchor)))
                .filter(s -> TAKEN.claim(s, visitor, now, 1));
    }

    public static BlockPos overflow(ServerLevel level, Zoo zoo, Gate gate) { ... gate.pos + normal * 7, findStandable within 3, else gate.outside() ... }
    public static void release(UUID visitor) { TAKEN.releaseAll(visitor); }
    public static void forget(Zoo zoo) { zoo.gates().forEach(g -> SLOTS.remove(g.id())); }
    public static int maxWaiting(long now) { return TAKEN.maxCount(now); }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) { SLOTS.clear(); TAKEN.clear(); }
}
```

`forget` must also run for gates that were removed: key the cache by gate id and, in `forget`, remove every key whose gate is no longer in the zoo as well as the current ones (keep a `Map<UUID zooId, Set<UUID gateId>>` or simply clear the whole cache; slots are cheap to recompute). Use `SLOTS.clear()` — simplest and correct.

`VisitorController`:

```java
    private @Nullable BlockPos queueSlot;
    private long overflowSince;

    private void stepArriving(ServerLevel level, Zoo zoo, long now) {
        ... gate as before ...
        BlockPos near = leaderSlot();   // the group leader's slot, if this visitor is a follower
        queueSlot = GateQueues.claim(level, zoo, gate.get(), visitor.getUUID(), near, now).orElse(null);
        BlockPos queueSpot = queueSlot != null ? queueSlot : GateQueues.overflow(level, zoo, gate.get());
        if (closeTo(queueSpot, 2.0)) {
            if (queueSlot != null) { setPhase(VisitorPhase.QUEUEING); return; }
            // Waiting beyond the full queue: don't count it against the gate; queue here after a while.
            if (overflowSince == 0) overflowSince = now;
            target = null;
            if (now - overflowSince > QUEUE_WAIT_TICKS * 2) setPhase(VisitorPhase.QUEUEING);
            return;
        }
        overflowSince = 0;
        if (walkTowards(queueSpot, now)) { ... as before ... }
    }
```

`setPhase` must not release the slot when moving ARRIVING → QUEUEING. In `setPhase`, after updating: `if (phase != VisitorPhase.QUEUEING) { GateQueues.release(visitor.getUUID()); queueSlot = null; }`. In `stepQueueing`, renew: `if (queueSlot != null) GateQueues.claim(level, zoo, gate, uuid, null, now)` using the visitor's gate. `leaderSlot()` finds the leader among `groupMembers()` and returns its `controller.queueSlot`.

`chooseExitGate`:

```java
        List<Gate> list = candidates;
        double[] distances = new double[list.size()];
        int[] heading = new int[list.size()];
        List<Visitor> others = VisitorTracker.visitors(zoo.id());
        for (int i = 0; i < list.size(); i++) {
            Gate g = list.get(i);
            distances[i] = Math.sqrt(g.pos().distSqr(visitor.blockPosition()));
            for (Visitor o : others) if (o != visitor && o.phase() == VisitorPhase.LEAVING && g.id().equals(o.controller().exitGateId)) heading[i]++;
        }
        int index = GateChoice.pick(distances, heading);
        return index < 0 ? null : list.get(index);
```

`Visitor.releaseClaims()` adds `GateQueues.release(getUUID());`. `VisitorTracker.onZooChanged` and `onBoundaryChanged` call `GateQueues.forget(zoo)` first.

- [ ] **Step 3: Build and run everything.** `./gradlew build`, `tools/gametest.sh`. Expected: all pass, two new tests. Run the suite twice (scheduling touched).
- [ ] **Step 4: Commit** `git commit -m "M10b: visitors queue on separate slots outside the gate and spread over exits when leaving"`

---

### Task 9: Enclosures, edge spots and the full-height census

**Files:**
- Create: `src/main/java/com/zoocraft/animal/LevelFloorGrid.java`, `src/main/resources/data/zoocraft/tags/block/natural_floor.json`
- Modify: `config/ZooConfig.java` (`MAX_VIEWING_SPOTS`, `VIEWING_SPOT_CAPACITY`, `MAX_ENCLOSURE_AREA`, `ENCLOSURE_FILLS_PER_TICK`), `registry/ZooTags.java` (`NATURAL_FLOOR`), `animal/AnimalCensus.java`, `animal/Exhibit.java`
- Test: `src/main/java/com/zoocraft/gametest/AnimalGameTests.java`

**Interfaces:**
- Consumes: `EnclosureFill`, `Enclosure`, `FloorGrid`, `ViewingSpots`, `SpotGrid` (Tasks 3, 4).
- Produces: `Exhibit.enclosure()` (`Enclosure`, `Enclosure.NONE` until filled), `Exhibit.surroundings()` (`record Surroundings(int natural, boolean waterOrLeaves)`), `AnimalCensus.runPendingFills(MinecraftServer, int max)` (also used by tests), `AnimalCensus.pendingFills()`.

Behaviour:
- `LevelFloorGrid` (animals): a column is floor at feet height y when the block below has a collision shape and isn't a fence, wall or fence gate, and the feet block has an empty collision shape (water counts as empty). `stepTo(x, z, fromY)` checks fromY, fromY+1, fromY-1 in that order; unloaded chunks are no floor. Open fence gates have an empty collision shape, so the fill escapes through them; closed ones block.
- `SpotGrid` for visitors: `Standing.findStandable` within 2 of `nearY`, inside the zoo, no fluid; `path` is `ZooTags.VISITOR_PATHS` under the feet.
- Census: the entity query covers the zoo's box from the level's minimum to maximum build height. After grouping, each exhibit whose enclosure is `NONE`, or bounded with any animal's column outside it, or open with any animal outside the explored area and its last fill over 60 seconds ago, is queued (once). Exhibits without a bounded enclosure get Phase 1 spots each census, sampled around each animal (round-robin, up to `maxViewingSpots`).
- `ServerTickEvent.Post`: `runPendingFills(server, ENCLOSURE_FILLS_PER_TICK)`, each timed under `PerfSlot.ENCLOSURE` for the exhibit's zoo. A fill starts from every animal's feet, uses `maxEnclosureArea`, and on a bounded result replaces the spots with `ViewingSpots.pick(...)` and records surroundings (natural floor blocks under the enclosure, water or leaves at feet height or below on any enclosure or edge-neighbour column). An open result stores the explored area and keeps the sampled spots.
- `MAX_SPOTS` constant goes; `maxViewingSpots` comes from config.

- [ ] **Step 1: Failing GameTests**

```java
    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1600)
    public static void aTwentyByTwentyPenGetsSpotsOutsideTheFenceAndIsViewed(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Big pen", TestZoos.rectangle(helper, 2, 2, 45, 45));
        ZooManager zoos = TestZoos.manager(helper);
        zoos.addGate(owner, helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 24)));
        zoos.setPrice(owner, zoo, 0);
        zoos.setOpen(owner, zoo, true);
        // Fence ring x 13..34, z 13..34: a 20 by 20 floor inside.
        for (int i = 13; i <= 34; i++) {
            helper.setBlock(new BlockPos(i, 2, 13), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(i, 2, 34), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(13, 2, i), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(34, 2, i), Blocks.OAK_FENCE);
        }
        helper.spawnWithNoFreeWill(EntityType.COW, at(16, 16));
        helper.spawnWithNoFreeWill(EntityType.COW, at(17, 15));
        helper.runAfterDelay(2, () -> {
            ZooCensus census = AnimalCensus.refresh(zoo, helper.getLevel());
            AnimalCensus.runPendingFills(helper.getLevel().getServer(), 100);
            Exhibit exhibit = census.exhibits().getFirst();
            helper.assertTrue(exhibit.enclosure().bounded() && exhibit.enclosure().area() == 400, "enclosure " + exhibit.enclosure().area());
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            for (BlockPos spot : exhibit.viewingSpots()) {
                int x = spot.getX() - origin.getX(), z = spot.getZ() - origin.getZ();
                helper.assertFalse(x >= 13 && x <= 34 && z >= 13 && z <= 34, "spot inside the fence at " + x + "," + z);
            }
            Visitor visitor = PerfGameTests.exploringVisitor(helper, new PerfGameTests.Setup(owner, zoo, zoo.gates().getFirst()), 8, 8);
            visitor.testSetBudget(VisitorProfile.ANIMAL_ENTHUSIAST, 10, 1.0);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(zoo.stats().commentTally().getOrDefault("amazing_exhibit", 0) >= 1, "the cows haven't been viewed");
            TestZoos.cleanUp(helper, owner, zoo);
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void animalsAreFoundAtAnyHeight(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Deep", TestZoos.rectangle(helper, 2, 2, 29, 29));
        BlockPos floor = helper.absolutePos(at(10, 10));
        int y = floor.getY() - 60 >= helper.getLevel().getMinBuildHeight() + 2 ? floor.getY() - 60 : floor.getY() + 70;
        Mob cow = EntityType.COW.create(helper.getLevel());
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.moveTo(floor.getX() + 0.5, y, floor.getZ() + 0.5, 0, 0);
        helper.getLevel().addFreshEntity(cow);
        ZooCensus census = AnimalCensus.refresh(zoo, helper.getLevel());
        helper.assertTrue(census.animals().contains(cow), "the cow " + (y - floor.getY()) + " blocks away vertically wasn't counted");
        cow.discard();
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void unfencedAnimalsStillGetSpots(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Open", TestZoos.rectangle(helper, 2, 2, 29, 29));
        helper.spawnWithNoFreeWill(EntityType.SHEEP, at(15, 15));
        ZooCensus census = AnimalCensus.refresh(zoo, helper.getLevel());
        AnimalCensus.runPendingFills(helper.getLevel().getServer(), 100);
        Exhibit exhibit = census.exhibits().getFirst();
        helper.assertFalse(exhibit.enclosure().bounded(), "an unfenced sheep has a bounded enclosure");
        helper.assertFalse(AnimalCensus.refresh(zoo, helper.getLevel()).exhibits().getFirst().viewingSpots().isEmpty(), "no spots");
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }
```

`PerfGameTests.Setup` and `exploringVisitor` are package-private already; make `Setup` package-private record if needed. The floor template sits a few blocks above the world's minimum height, so the deep test usually uses 70 blocks above (still outside the old 64-block window). If the vertical choice turns out the other way, the assertion message says which.

Run: `./gradlew compileJava`. Expected: compilation failure (`Exhibit.enclosure`, `runPendingFills` missing).

- [ ] **Step 2: Implement**

`ZooConfig` in `animals`:

```java
        MAX_VIEWING_SPOTS = b.comment("Most places visitors stand to see one exhibit.").defineInRange("maxViewingSpots", 12, 1, 64);
        VIEWING_SPOT_CAPACITY = b.comment("How many visitors one viewing spot holds.").defineInRange("viewingSpotCapacity", 2, 1, 16);
        MAX_ENCLOSURE_AREA = b.comment("Largest pen floor, in blocks, searched for one exhibit. Bigger pens count as open land.")
                .defineInRange("maxEnclosureArea", 1024, 64, 16384);
        ENCLOSURE_FILLS_PER_TICK = b.comment("Most exhibit enclosure searches per server tick, across all zoos.")
                .defineInRange("enclosureFillsPerTick", 2, 1, 32);
```

`natural_floor.json`:

```json
{
  "values": [
    "minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:podzol", "minecraft:rooted_dirt",
    "minecraft:mud", "minecraft:moss_block", "minecraft:sand", "minecraft:red_sand", "minecraft:gravel", "minecraft:snow_block",
    "minecraft:snow", "minecraft:clay", "minecraft:mycelium", "#minecraft:leaves",
    {"id": "#c:sands", "required": false}, {"id": "#c:gravels", "required": false}
  ]
}
```

`ZooTags.NATURAL_FLOOR = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MOD_ID, "natural_floor"))` (follow the existing `VISITOR_PATHS` declaration).

`LevelFloorGrid`:

```java
/** The world as {@link FloorGrid} for animals (spec 8.1): solid, non-fence floor under an empty feet block. */
final class LevelFloorGrid implements FloorGrid {
    private final ServerLevel level;
    LevelFloorGrid(ServerLevel level) { this.level = level; }

    @Override
    public int stepTo(int x, int z, int fromY) {
        for (int y : new int[]{fromY, fromY + 1, fromY - 1}) if (floor(x, y, z)) return y;
        return NO_FLOOR;
    }

    boolean floor(int x, int y, int z) {
        BlockPos feet = new BlockPos(x, y, z);
        if (!level.isLoaded(feet)) return false;
        BlockState below = level.getBlockState(feet.below());
        if (below.getCollisionShape(level, feet.below()).isEmpty() || below.is(BlockTags.FENCES) || below.is(BlockTags.WALLS)
                || below.is(BlockTags.FENCE_GATES)) return false;
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty();
    }
}
```

`Exhibit` gains:

```java
    private Enclosure enclosure = Enclosure.NONE;
    private long lastFill = Long.MIN_VALUE / 2;
    private Surroundings surroundings = new Surroundings(0, false);
    public record Surroundings(int natural, boolean waterOrLeaves) { }

    public Enclosure enclosure() { return enclosure; }
    public Surroundings surroundings() { return surroundings; }
    void filled(Enclosure enclosure, Surroundings surroundings, List<BlockPos> spots, long now) { ... sets; spots replaced only when bounded ... }
    void sampledSpots(List<BlockPos> spots) { this.viewingSpots = List.copyOf(spots); }
    boolean needsFill(long now) { ... rules above ... }
```

`update(...)` stops taking spots; the census calls `sampledSpots` for exhibits that aren't bounded.

`AnimalCensus`: replace `searchBox` Y range with `level.getMinBuildHeight()` .. `level.getMaxBuildHeight()`; remove `SEARCH_BELOW`/`SEARCH_ABOVE`. Add:

```java
    private record FillJob(UUID zooId, UUID exhibitId) { }
    private static final LinkedHashSet<FillJob> PENDING = new LinkedHashSet<>();

    @SubscribeEvent
    static void onFillTick(ServerTickEvent.Post event) {
        runPendingFills(event.getServer(), ZooConfig.ENCLOSURE_FILLS_PER_TICK.get());
    }

    public static void runPendingFills(MinecraftServer server, int max) {
        Iterator<FillJob> it = PENDING.iterator();
        for (int done = 0; done < max && it.hasNext(); done++) {
            FillJob job = it.next();
            it.remove();
            Optional<Zoo> zoo = ZooManager.get(server).byId(job.zooId());
            List<Exhibit> exhibits = EXHIBITS.get(job.zooId());
            if (zoo.isEmpty() || exhibits == null) continue;
            ServerLevel level = server.getLevel(zoo.get().dimension());
            exhibits.stream().filter(e -> e.id().equals(job.exhibitId())).findFirst().ifPresent(e -> {
                long timer = SimulationTimers.start();
                try { fill(level, zoo.get(), e); } finally { SimulationTimers.stop(PerfSlot.ENCLOSURE, job.zooId(), timer); }
            });
        }
    }
```

`fill(level, zoo, exhibit)` runs `EnclosureFill.fill(new LevelFloorGrid(level), starts, MAX_ENCLOSURE_AREA)`; when bounded, spots from `ViewingSpots.pick(enclosure, spotGrid(level, zoo), MAX_VIEWING_SPOTS)` mapped to `BlockPos`; surroundings counted over the enclosure cells (block below feet in `NATURAL_FLOOR`; `waterOrLeaves` when any enclosure cell or its 4-neighbours has water at feet level or leaves at feet or below). `forgetForTest` and `onServerStopped` also clear `PENDING`.

`viewingSpots(level, zoo, centre)` becomes `sampledSpots(level, zoo, List<Vec3> around)`: up to `SPOT_SAMPLES` angles per animal for the first `maxViewingSpots` animals, the same candidate rules, sorted path-first then nearest to any animal, limited to `MAX_VIEWING_SPOTS`.

- [ ] **Step 3: Build and run everything.** Expected: all pass, three new tests.
- [ ] **Step 4: Commit** `git commit -m "M11: exhibits find their enclosure with a bounded flood fill and get viewing spots just outside its edge; animals are counted at any height"`

---

### Task 10: Viewing-spot claims, crowding and fair route choice

**Files:**
- Modify: `animal/Exhibit.java` (claims, per-spot reachability, daily views and attempts), `zoo/SavedExhibit.java` (optional `today` and `day`), `animal/AnimalCensus.java` (saves them), `visitor/VisitorExploration.java`, `visitor/VisitorController.java`, `visitor/Visitor.java`, `feedback/CommentType.java` (`CROWDED`), `dashboard/AdviceBuilder.java`, `dashboard/DashboardService.java` (`speciesLabel` package-visible), `core/report/Advice.java`, `lang/en_us.json`
- Test: `src/main/java/com/zoocraft/gametest/AnimalGameTests.java`

**Interfaces:**
- Consumes: `Occupancy`, `SpotChoice`, `ExhibitOption` (new fields), `ExhibitChoice.routeScale`.
- Produces: `Exhibit.claims()` (`Occupancy<BlockPos>`), `Exhibit.claimCount(BlockPos, long now)`, `Exhibit.full(long now)`, `Exhibit.occupiedShare(long now)`, `Exhibit.markSpotUnreachable(BlockPos, long until)`, `Exhibit.spotUnreachable(BlockPos, long now)`, `Exhibit.markSpotReached(BlockPos, long now)`, `Exhibit.spotsReachedSince(long since)`, `Exhibit.viewsToday(long now)`, `Exhibit.attemptsToday(long now)`, `Exhibit.addView(long now)` (replaces `addView()`), `Exhibit.addAttempt(long now)`; `Visitor.claimedSpot()`; `CommentType.CROWDED`.

Behaviour:
- `chooseTarget`: release any held spot first. Options carry `routeScale` (zoo shape), `viewsToday`, the median of usable exhibits' `viewsToday`, `occupiedShare` and `full`. After picking an exhibit, `SpotChoice.pick` over its spots (occupied counts, capacity from config, path flag from the block below, per-spot unreachable, distance to the visitor). Claim it; if the claim fails (full), still walk to that spot without a claim. Record an attempt on the exhibit.
- Every exploring step while `currentExhibit` and `claimedSpot` are set: renew the claim.
- Arrival: with a claim, mark the spot reached and proceed as before. Without one: try `SpotChoice` again among free spots; a free one is claimed and becomes the new target; none free → wait (linger 20 ticks, keep the target) until 5 seconds have passed since the first try, then `remarkOnce(CROWDED, exhibitId)`, add the exhibit to `recentExhibits`, clear the target and choose again.
- `onUnreachable`: marks only the spot unreachable for 60 s; `Exhibit.isUnreachable(now)` is true when every spot is.
- Release on: choosing again, phase change, removal (`Visitor.releaseClaims`), unreachable.
- Daily counts: game day is `gameTime / 24000`. `viewsToday` and `attemptsToday` reset on a new day; both are saved in `SavedExhibit` (optional fields, so older saves load with 0).
- Advice: `"crowded"` subjects name the exhibit's species: "The %s exhibit is too crowded. Give visitors more room to see it." Comment text: "It's too crowded to see anything." Weight 2.

- [ ] **Step 1: Failing GameTests** (in `AnimalGameTests`; the first changes config, so `batch = "config"`)

```java
    /** A 4 by 4 pen at x 20..23, z 20..23 (fence one block outside) with 2 pigs; spots are capped at 4. */
    private static Zoo crowdScene(GameTestHelper helper, ServerPlayer owner) { ... zoo 2..45, gate at (2, 24), free, open; fence; pigs; refresh + runPendingFills ... }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1600, batch = "config")
    public static void eightVisitorsFillFourSpotsTwoEach(GameTestHelper helper) {
        int original = ZooConfig.MAX_VIEWING_SPOTS.get();
        ZooConfig.MAX_VIEWING_SPOTS.set(4);
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = crowdScene(helper, owner);
        List<Visitor> visitors = new ArrayList<>();
        for (int i = 0; i < 8; i++) visitors.add(PerfGameTests.exploringVisitor(helper, setupOf(owner, zoo), 6 + i, 6));
        helper.succeedWhen(() -> {
            Exhibit exhibit = AnimalCensus.cached(zoo).exhibits().getFirst();
            long now = helper.getLevel().getGameTime();
            helper.assertTrue(exhibit.viewingSpots().size() == 4, "spots " + exhibit.viewingSpots().size());
            helper.assertTrue(exhibit.viewingSpots().stream().allMatch(s -> exhibit.claimCount(s, now) == 2), "not two on every spot");
            ZooConfig.MAX_VIEWING_SPOTS.set(original);
            visitors.forEach(Visitor::discard);
            TestZoos.cleanUp(helper, owner, zoo);
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1200)
    public static void aVisitorFindingEverySpotFullSaysItIsTooCrowded(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = crowdScene(helper, owner);
        Exhibit exhibit = AnimalCensus.cached(zoo).exhibits().getFirst();
        List<UUID> fakes = new ArrayList<>();
        for (int i = 0; i < 2 * exhibit.viewingSpots().size(); i++) fakes.add(UUID.randomUUID());
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            for (int i = 0; i < fakes.size(); i++) exhibit.claims().claim(exhibit.viewingSpots().get(i / 2), fakes.get(i), now, 2);
        });
        Visitor visitor = PerfGameTests.exploringVisitor(helper, setupOf(owner, zoo), 8, 8);
        helper.succeedWhen(() -> {
            helper.assertTrue(zoo.stats().commentTally().getOrDefault("crowded", 0) == 1, "no crowded comment");
            helper.assertTrue(zoo.reports().subjectsToday().containsKey("crowded|" + exhibit.id()), "complaint doesn't name the exhibit");
            visitor.discard();
            TestZoos.cleanUp(helper, owner, zoo);
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 400)
    public static void removingAVisitorFreesItsViewingSpot(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = crowdScene(helper, owner);
        Visitor visitor = PerfGameTests.exploringVisitor(helper, setupOf(owner, zoo), 8, 8);
        Exhibit exhibit = AnimalCensus.cached(zoo).exhibits().getFirst();
        int[] claimedAt = {-1};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (claimedAt[0] < 0 && visitor.claimedSpot() != null) {
                claimedAt[0] = (int) now;
                visitor.discard();
            } else if (claimedAt[0] >= 0 && now > claimedAt[0]) {
                helper.assertTrue(exhibit.viewingSpots().stream().allMatch(s -> exhibit.claimCount(s, now) == 0), "spot still held");
                TestZoos.cleanUp(helper, owner, zoo);
                helper.succeed();
            }
        });
    }
```

`setupOf(owner, zoo)` builds a `PerfGameTests.Setup` with the zoo's first gate. With one exhibit, every visitor chooses it, and `SpotChoice` fills spots evenly.

Run: `./gradlew compileJava`. Expected: compilation failure.

- [ ] **Step 2: Implement** the behaviour above. Key code:

```java
    // Exhibit
    private final Occupancy<BlockPos> claims = new Occupancy<>(10 * 20);
    private final Map<BlockPos, Long> spotUnreachable = new HashMap<>();
    private final Map<BlockPos, Long> spotReached = new HashMap<>();
    private long day = Long.MIN_VALUE;
    private int viewsToday, attemptsToday;

    public boolean isUnreachable(long now) {
        if (viewingSpots.isEmpty()) return unreachableUntil > now;
        return viewingSpots.stream().allMatch(s -> spotUnreachable(s, now));
    }
    public boolean full(long now) {
        int cap = ZooConfig.VIEWING_SPOT_CAPACITY.get();
        return !viewingSpots.isEmpty() && viewingSpots.stream().allMatch(s -> claims.count(s, now) >= cap);
    }
    public double occupiedShare(long now) { ... sum of counts / (spots × capacity) ... }
    private void roll(long now) { long d = now / 24000L; if (d != day) { day = d; viewsToday = 0; attemptsToday = 0; } }
```

`VisitorExploration.chooseTarget` (exhibit branch):

```java
            int[] views = usable.stream().mapToInt(e -> e.viewsToday(now)).sorted().toArray();
            int median = views[views.length / 2];
            double scale = ExhibitChoice.routeScale(zoo.shape().maxX() - zoo.shape().minX(), zoo.shape().maxZ() - zoo.shape().minZ());
            ... options.add(new ExhibitOption(exhibit.species(), distance, recent, scale, exhibit.viewsToday(now), median,
                    exhibit.occupiedShare(now), exhibit.full(now)));
            ...
            Exhibit chosen = usable.get(index);
            chosen.addAttempt(now);
            visitor.currentExhibit = chosen.id();
            return Optional.ofNullable(takeSpot(visitor, level, chosen, now, false));
```

```java
    /** The least occupied reachable spot; claimed when there is room. With {@code freeOnly}, null when none is free. */
    static @Nullable BlockPos takeSpot(Visitor visitor, ServerLevel level, Exhibit exhibit, long now, boolean freeOnly) {
        int capacity = ZooConfig.VIEWING_SPOT_CAPACITY.get();
        List<SpotChoice.Spot> options = new ArrayList<>();
        for (BlockPos s : exhibit.viewingSpots()) {
            options.add(new SpotChoice.Spot(exhibit.claimCount(s, now), capacity, level.getBlockState(s.below()).is(ZooTags.VISITOR_PATHS),
                    exhibit.spotUnreachable(s, now), Math.sqrt(s.distSqr(visitor.blockPosition()))));
        }
        int i = SpotChoice.pick(options);
        if (i < 0) return null;
        BlockPos spot = exhibit.viewingSpots().get(i);
        if (exhibit.claims().claim(spot, visitor.getUUID(), now, capacity)) {
            visitor.claimedSpot = spot;
            return spot;
        }
        return freeOnly ? null : spot;
    }
```

`VisitorExploration.onArrived` returns `record Arrival(long linger, @Nullable BlockPos retarget, boolean keepTarget)`; the controller's arrival branch for exhibits becomes:

```java
                VisitorExploration.Arrival a = VisitorExploration.onArrived(visitor, zoo, now);
                if (a.retarget() != null) { setTarget(a.retarget()); return; }
                if (!a.keepTarget()) { target = null; failedRoutes = 0; }
                lingerUntil = now + a.linger();
```

(the errand branch keeps clearing `target` before `errands.onArrived`). `CommentType.CROWDED(-3, true)`; lang `"zoocraft.comment.crowded": "It's too crowded to see anything."`, `"zoocraft.advice.crowded": "The %s exhibit is too crowded. Give visitors more room to see it."`. `AdviceBuilder`: `case "crowded" -> exhibitLabel(zoo, subject).ifPresent(label -> add(issues, text, kind, count, Component.translatable("zoocraft.advice.crowded", label)));` with `exhibitLabel` looking the id up in `AnimalCensus.cached(zoo)` and calling `DashboardService.speciesLabel`.

`SavedExhibit`: add `Codec.INT.optionalFieldOf("today", 0)` and `Codec.LONG.optionalFieldOf("day", -1L)`; keep the 3-argument constructor for callers. `Exhibit.restored(id, views, today, day)`.

- [ ] **Step 3: Build and run everything.** Delete `run-gametest/config/zoocraft-server.toml` first (new defaults). Expected: all pass, three new tests; existing `visitorGoesToSeeTheAnimals` still passes. Run twice.
- [ ] **Step 4: Commit** `git commit -m "M10b+M11: visitors claim the least crowded viewing spot, say when an exhibit is too crowded, and choose exhibits by route scale, popularity and crowding"`

---

### Task 11: Exhibit ratings on the Animals tab

**Files:**
- Modify: `animal/Exhibit.java` (`rating()`), `animal/AnimalCensus.java` (rates each exhibit every census), `visitor/VisitorExploration.java` (±1 on first viewing), `network/DashboardSnapshot.java` (`ExhibitLine` gains `int score`, `int grade`, `List<Component> warnings`), `dashboard/DashboardService.java`, `client/DashboardScreen.java`, `lang/en_us.json`
- Test: `src/main/java/com/zoocraft/gametest/AnimalGameTests.java`, `DashboardGameTests.java`

**Interfaces:**
- Consumes: `ExhibitRating` (Task 6), `Exhibit.enclosure()`, `surroundings()`, spot reach and attempt counts (Tasks 9, 10).
- Produces: `Exhibit.rating()` (`ExhibitRating.Result`), lang keys `zoocraft.rating.excellent|good|fair|poor`, `zoocraft.screen.dashboard.exhibit_rating` ("    Rating: %s (%s)"), `zoocraft.warning.cramped`, `zoocraft.warning.bare`.

Inputs per exhibit at each census: `spots` = spot count; `spotsReached` = spots reached in the last 24,000 ticks; `viewedFromSpots` = `viewsToday > 0`; `attempts` = `attemptsToday`; `species`; `open` = `!enclosure.bounded()`; `floorBlocks` = enclosure area; `animalWeight` = animals, counting those with `getBbWidth() > 1.5` twice; `naturalShare` = `surroundings.natural / area` (0 when area is 0); `waterOrLeaves`. `viewingProblem(now)` becomes `viewingSpots.isEmpty() || isUnreachable(now) || rating.warnings().contains(CANT_SEE)`.

Satisfaction: in `observe`, where `visitor.exhibitsViewed.add(exhibit.id())` is true, add `visitor.changeSatisfaction(grade == EXCELLENT ? 1 : grade == POOR ? -1 : 0)`.

Dashboard: each exhibit shows "Rating: Good (72)" (MUTED) and its cramped/bare warnings (WARNING) under the existing lines. Warnings text: "The %s exhibit looks cramped." and "The %s exhibit has bare ground. Natural surroundings would help." with the species label.

- [ ] **Step 1: Failing GameTest**

```java
    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 100)
    public static void aCrampedPenRaisesTheCrampedWarning(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Cramped", TestZoos.rectangle(helper, 2, 2, 45, 45));
        // A 3 by 3 floor at x 20..22, z 20..22, fenced round.
        for (int i = 19; i <= 23; i++) {
            helper.setBlock(new BlockPos(i, 2, 19), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(i, 2, 23), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(19, 2, i), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(23, 2, i), Blocks.OAK_FENCE);
        }
        for (int i = 0; i < 4; i++) helper.spawnWithNoFreeWill(EntityType.COW, at(20 + i % 3, 20 + i / 3));
        helper.runAfterDelay(2, () -> {
            AnimalCensus.refresh(zoo, helper.getLevel());
            AnimalCensus.runPendingFills(helper.getLevel().getServer(), 100);
            Exhibit exhibit = AnimalCensus.refresh(zoo, helper.getLevel()).exhibits().getFirst();
            helper.assertTrue(exhibit.enclosure().bounded() && exhibit.enclosure().area() == 9, "area " + exhibit.enclosure().area());
            helper.assertTrue(exhibit.rating().warnings().contains(ExhibitRating.Warning.CRAMPED), "no cramped warning: " + exhibit.rating());
            TestZoos.cleanUp(helper, owner, zoo);
            helper.succeed();
        });
    }
```

In `DashboardGameTests`, extend an existing Animals-tab snapshot test (or add one) to assert the exhibit line has a grade between 0 and 3 and the snapshot round-trips through its codec with warnings.

Run: `./gradlew compileJava`. Expected: compilation failure.

- [ ] **Step 2: Implement** as described. Snapshot codec: write `score` and `grade` as var ints and `warnings` with the existing `writeList`/`TEXT` helpers.
- [ ] **Step 3: Build and run everything.** Expected: all pass.
- [ ] **Step 4: Commit** `git commit -m "M11: exhibits are rated for viewing access, variety, space and surroundings, with cramped and bare-ground warnings on the Animals tab"`

---

### Task 12: Crowd recovery, real stuck durations and the perf report

**Files:**
- Modify: `visitor/VisitorController.java`, `visitor/Visitor.java` (`stuckTicks()`), `perf/PerfReport.java`, `command/PerfCommands.java` (`/zoo debug perf exhibits`)
- Test: `src/main/java/com/zoocraft/gametest/PerfGameTests.java`

**Interfaces:**
- Produces: `VisitorController.stepAsides()`, `VisitorController.stuckTicks(long now)`, `Visitor.stuckTicks()`; `/zoo debug perf exhibits [zoo]` writes `logs/zoocraft-exhibits.csv` (`zoo,exhibit,species,x,z,views_total,views_today,rating,spots,bounded,area`).

Behaviour:
- Crowd recovery (spec 7.4): inside `walkTowards`, once a visitor has made no progress for 5 seconds (`CROWD_TICKS = 100`), count other visitors within 1 block (one `getEntitiesOfClass(Visitor.class, box)` call, only then). More than 2, and no step-aside yet on this target: pick a random standable block within 3 blocks inside the zoo (or outside, for arriving and exiting phases, using the phase's path rules: `goodSpot` for exploring phases, `Standing.findInColumn` otherwise), set it as the walk target for up to 3 seconds (`STEP_ASIDE_TICKS = 60`), then restore the real target and reset `lastProgressTime` (one re-plan). Count it in `stepAsides`. The step-aside resets on a new target.
- Stuck duration: `stuckSince` set on a new target and on progress only; `routeFailed` doesn't reset it. `PerfReport` uses `stuckTicks` for the stuck list and counts, so a visitor stuck for 70 seconds shows 70 seconds (deferred M9 minor).
- `PerfReport.zoo`: crowding line from real counts: "Crowding: most on one viewing spot N, gate slots in use N, most at one counter N"; plan line adds "N per visitor-minute"; exhibits line adds today's views (min, median, max, never viewed today) next to the totals.

- [ ] **Step 1: Failing GameTests**

```java
    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 600)
    public static void aVisitorHemmedInByOthersStepsAsideBeforeFailing(GameTestHelper helper) {
        Setup setup = openZoo(helper, "Hemmed");
        // A one-block-wide corridor of stone bricks walls along z 23 and 25 from x 20 to 34; three still visitors block it.
        for (int x = 20; x <= 34; x++) for (int y = 2; y <= 3; y++) {
            helper.setBlock(new BlockPos(x, y, 23), Blocks.STONE_BRICKS);
            helper.setBlock(new BlockPos(x, y, 25), Blocks.STONE_BRICKS);
        }
        for (int i = 0; i < 3; i++) {
            Visitor blocker = helper.spawnWithNoFreeWill(ZooEntities.VISITOR.get(), new BlockPos(27 + i, 2, 24));
            blocker.setNoAi(true);
        }
        Visitor visitor = exploringVisitor(helper, setup, 22, 24);
        helper.runAfterDelay(3, () -> visitor.controller().testWalkTo(helper.absolutePos(new BlockPos(33, 2, 24))));
        helper.succeedWhen(() -> {
            helper.assertTrue(visitor.controller().stepAsides() >= 1, "never stepped aside");
            helper.assertTrue(visitor.controller().failedRoutesForTest() == 0, "counted a failure before stepping aside");
            visitor.discard();
            TestZoos.cleanUp(helper, setup.owner(), setup.zoo());
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void stuckTimeKeepsCountingThroughRetries(GameTestHelper helper) {
        // Pure check of the controller's bookkeeping through its test hooks: a failed route doesn't reset the stuck time.
        ...spawn a visitor, testWalkTo a target inside a sealed stone box, advance via testSimulateNoProgress(700 ticks) which calls
        walkTowards with the game time moved on; assert stuckTicks >= 700 while ticksWithoutProgress < 600...
    }
```

`testWalkTo(BlockPos)` sets the controller target (exploring, no exhibit); `failedRoutesForTest()` exposes `failedRoutes`. If simulating time in the second test proves awkward, replace it with a JUnit test on a small pure `core.visitor.Progress` tracker (`onTarget`, `onProgress`, `onRouteFailed`, `stuckTicks`, `ticksWithoutProgress`) that the controller delegates to; ledger the choice.

Run: `./gradlew compileJava`. Expected: compilation failure.

- [ ] **Step 2: Implement** as described.
- [ ] **Step 3: Build and run everything.** Expected: all pass.
- [ ] **Step 4: Commit** `git commit -m "M10b: visitors hemmed in by others step aside before counting a failure; stuck time no longer resets on retries; perf report shows real crowding, plans per visitor-minute and today's exhibit coverage"`

---

### Task 13: Where visitors look

**Files:**
- Create: `src/main/java/com/zoocraft/visitor/ChooseLookTarget.java`
- Modify: `visitor/Visitor.java` (`getIdleTasks`, `mobInteract`, look state), `visitor/VisitorController.java` (`lingering()` accessor)
- Test: `src/main/java/com/zoocraft/gametest/VisitorGameTests.java`

**Interfaces:**
- Consumes: `LookChoice` (Task 2), `AnimalCensus.cached`, `Exhibit.animals()`.
- Produces: `ChooseLookTarget extends ExtendedBehaviour<Visitor>`; `Visitor.playerLookAllowedAt` (runtime long).

Behaviour (`start`):
- `exhibitAnimal`: the visitor has a `currentExhibit` and one of its living animals is within `viewingRadius` horizontally and 3 vertically. Choose a random one of those.
- `walking`: the navigation has a path that isn't done; "ahead" = the path node 3 past the next one (or the end), looked at 1.5 blocks up.
- `passingAnimal`: an exhibit (from the cached census) whose centre is within `viewingRadius + 4`, with a living animal within `viewingRadius`.
- `playerNear`: `level.getNearestPlayer(visitor, 6)` exists and isn't a spectator; `playerAllowed`: `now >= playerLookAllowedAt`. A PLAYER choice sets `playerLookAllowedAt = now + playerCooldown`.
- `hasLeader`: `VisitorExploration.leaderOf(visitor)` present.
- Memory: `BrainUtils.setForgettableMemory(visitor, LOOK_TARGET, tracker, duration)` with `EntityTracker(entity, true)` for animals, players and the leader, `BlockPosTracker` for the path point, `FreePositionTracker` for random (eye position plus a random horizontal unit vector, as `SetRandomLookTarget` does).
- Memory requirement: `LOOK_TARGET` absent. No run chance: a new target is chosen as soon as the last expires.
- `mobInteract`: `BrainUtils.setForgettableMemory(this, LOOK_TARGET, new EntityTracker(player, true), LookChoice.INTERACT_LOOK_TICKS)` (no cooldown).
- `getIdleTasks` returns `BrainActivityGroup.idleTasks(new ChooseLookTarget())`.

- [ ] **Step 1: Failing GameTest**

```java
    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1400)
    public static void lingeringVisitorsLookAtAnimalsAndOnlyGlanceAtPlayers(GameTestHelper helper) {
        Setup setup = openZoo(helper, 0);
        helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(30, 2, 30));
        helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(31, 2, 31));
        AnimalCensus.refresh(setup.zoo(), helper.getLevel());
        Visitor visitor = visitor(helper, setup, UUID.randomUUID(), true, 28, 28, 10);
        visitor.testSetPhase(VisitorPhase.EXPLORING);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos p = helper.absolutePos(new BlockPos(28, 2, 25));
        player.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        int[] ticks = new int[3]; // total, at player, at animals
        helper.onEachTick(() -> {
            if (helper.getTick() < 100 || ticks[0] >= 1200) return;
            ticks[0]++;
            BrainUtils.getMemory(visitor, MemoryModuleType.LOOK_TARGET) instanceof EntityTracker t ... count player / Pig
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ticks[0] >= 1200, "still measuring");
            helper.assertTrue(ticks[1] < 0.15 * ticks[0], "looked at the player " + ticks[1] + " of " + ticks[0] + " ticks");
            helper.assertTrue(ticks[2] > 0.5 * ticks[0], "looked at the animals only " + ticks[2] + " of " + ticks[0] + " ticks");
            visitor.discard();
            player.discard();
            TestZoos.cleanUp(helper, setup.owner(), setup.zoo());
        });
    }
```

(The `onEachTick` is registered at the top and skips the first 100 ticks itself; never register it from inside `runAfterDelay`.) The visitor starts 2 blocks from the pigs, so it views and lingers at that exhibit for the whole minute: it is the only exhibit, so it returns there after each linger. Check whether `makeMockServerPlayerInLevel` exists in this version; if not, use `FakePlayerFactory` plus `level.addNewPlayer` or skip the player part and ledger it.

Run: `./gradlew compileJava`. Expected: compilation failure (`ChooseLookTarget` absent is not referenced by the test, so the test compiles; run it and expect FAIL on the player share or the animal share, which shows the old behaviour).

- [ ] **Step 2: Implement** `ChooseLookTarget` and the `Visitor` changes.
- [ ] **Step 3: Build and run everything.** Expected: all pass.
- [ ] **Step 4: Commit** `git commit -m "M10b: visitors look at the animals, the way ahead or their group, and only glance at players"`

---

### Task 14: Housekeeping

**Files:**
- Modify: `src/main/java/com/zoocraft/gametest/PerfGameTests.java` (config-changing tests to `batch = "config"`), `.gitignore`, `tools/perf/README.md`
- Create: `tools/perf/coverage.py`

- [ ] **Step 1:** Move `followRangeComesFromConfig`, `visitorsStillPreferAPathAlongside` (if it changes config), `throttledShortcutsWalkAsFastAsEveryTick`, `aNewPathGetsAShortcutAttemptStraightAway`, `changingTheStepKeepsNeedsRisingAtTheSameRate` and any other test calling `ZooConfig.*.set` to `batch = "config"` (deferred M9 minor). Run the suite twice.
- [ ] **Step 2:** `git rm -r --cached tools/perf/__pycache__` and add `tools/perf/__pycache__/` to `.gitignore` (deferred minor).
- [ ] **Step 3:** `coverage.py <tag> "<zoo>" <visitors> <minutes>`: sets the limit, spawns to 90%, runs `/zoo debug perf exhibits "<zoo>"` at the start and end, waits the given minutes sampling `/zoo debug perf "<zoo>"` stuck lines each minute (keeps the worst stuck duration), and prints: exhibits, share viewed during the run (views delta > 0), median and maximum delta, max/median ratio, worst stuck duration. Results appended to `tools/perf/results/<tag>.md`. README gets a "Coverage" section.
- [ ] **Step 4: Commit** `git commit -m "M10b+M11: config-changing GameTests in their own batch, coverage script, perf caches no longer tracked"`

---

### Task 15: Measure

Follow the harness steps in the user's notes (prepare, `runServer`, `runClient -PjoinServer`, wait for "Dev joined the game", spectator and tp). Delete `run-server/config/zoocraft-server.toml`'s new keys only if they are missing (the server writes defaults on start).

- [ ] **Step 1:** `python ramp.py m11-big "Big Park" 0,100,200`. At the 200 level, `/zoo debug perf "Big Park"`: keep slots, the plans line (per second, % short, ms each, per visitor-minute), stuck count and worst duration, crowding, most-missed targets, exhibit coverage, and the enclosure and census slots.
- [ ] **Step 2:** `kill @e[type=zoocraft:visitor]`, then `python coverage.py m11-cov "Big Park" 100 20`.
- [ ] **Step 3:** Four zoos: kill visitors, close Big Park, open the four small zoos, `python ramp.py m11-four "North West,North East,South West,South East" 0,50` (50 each = 200 total). Close them and reopen Big Park afterwards.
- [ ] **Step 4:** Stop the server, kill the client, `perf_world.py restore`. Compare with the targets in the goal. If one is missed, find the cause with `/zoo debug perf` and the JFR summary, fix it as a new task in the ledger, and measure again.

---

### Task 16: Review, fixes and records

- [ ] **Step 1:** Fresh whole-branch review (subagent, most capable model) of the M10b+M11 commits against this plan and the spec. Fix what it finds (TDD for behaviour fixes), rerun the suite twice, and remeasure if a fix touches visitor movement, choice or the census.
- [ ] **Step 2:** Records:
  - `docs/performance.md`: "M10b and M11" section with the tables, plans line, stuck, coverage, four zoos, and what changed; update "Reading `/zoo debug perf`" (crowding line, enclosure slot, plans per visitor-minute, today's coverage).
  - Spec: 3.4 note for M10b/M11; section 10 table gains `visitorFollowRange` 48, `offPathCost` 0.75, `pathShortcutMode` `EVERY_NODE`, marks `pathReplanGap` and the far-visitor settings as not built; section 12 notes from play-testing are left for the user, with any thresholds this milestone chose (rating grades) listed.
  - `docs/playtests/m10b-m11-crowds-exhibits.md`: crowd spreading at spots, gates, counters; crowded comment and advice; shop queue; exits; look behaviour (stand among visitors at an exhibit); side-by-side movement check (`pathShortcutMode` `EVERY_TICK` against `EVERY_NODE`); how well visitors keep to paths at `offPathCost` 0.75; ratings and warnings on the Animals tab (cramped pen, bare stone pen, a pen with water); a 20 by 20 pen; an animal on a high platform.
  - `CLAUDE.md` code map: `core.crowd`, `core.exhibit` additions, `core.visitor.LookChoice`, `shop.FacilityOccupancy`, `visitor.GateQueues`, `visitor.ChooseLookTarget`, `animal.LevelFloorGrid`, `/zoo debug perf exhibits`, `tools/perf/coverage.py`.
- [ ] **Step 3: Commit** `git commit -m "M10b+M11: performance doc, spec notes, play-test checklist and code map"`

---

## Ledger

Every deviation from this plan or the spec, as it happens. Each entry ends with a `Ruling:` line.
