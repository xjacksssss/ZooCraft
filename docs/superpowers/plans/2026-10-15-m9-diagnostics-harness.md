# M9 Diagnostics and Harness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** ZooCraft measures its own cost per tick and per zoo, `/zoo debug perf` and its CSV log report it along with stuck visitors, crowding and exhibit coverage, `/zoo debug recognise` lists the recognition verdict for every entity type, the hard-coded visitor intervals become `simulation` settings, and the measurement harness from the Phase 3 investigation is committed so section 3 of the spec can be reproduced.

**Architecture:** The pure logic goes in `core.perf`: `TimerSlots` (fixed slots, a 600-tick ring buffer, and nesting so a ZooCraft total counts nested time once), `PerfSlot` (the slot list) and `LimitRule` (used in M12). The same goes for `core.visitor.StepRates` (per-step rates from a configurable step) and `core.animal.RecognitionRules.decide` (the verdict with its reason). On the Minecraft side, a new `perf` package holds `SimulationTimers` (a static facade over a global `TimerSlots` plus one per zoo, driven by server tick events), `PerfReport` (builds the command output), `PerfLog` (CSV writer) and `PerfCommands` (the `/zoo debug perf` subtree). `animal.RecognitionReport` runs the recognition check. The existing systems are wrapped with `start()` and `stop()` in `try/finally` blocks. The harness is Python under `tools/perf/` and talks to the dev server over RCON.

**Tech Stack:** Java 21, NeoForge 21.1.251 events and config (`ModConfigSpec`), Brigadier, JUnit 5, NeoForge GameTests, Python 3 standard library, the JDK's `jcmd` and `jfr`.

**Spec:** `docs/superpowers/specs/2026-10-15-phase3-scale-compatibility-design.md` (section 6; `LimitRule` from section 9.2 because section 6.6 tests it)

**Execution note:** Autonomous build (user memory). Native execution on branch `phase3`.

## Global Constraints

- Timers use `System.nanoTime()` with no allocation per call. They are always on.
- Each slot reports its average per tick over the last 30 seconds (600 ticks), its worst single tick in that window, and its call count in that window.
- The ZooCraft total counts time only at the outermost timed call, so nested timers (pathfinding inside the brain, a census refreshed from inside the controller) are never counted twice in the total.
- `/zoo debug ...` commands need permission level 2, like the rest of `/zoo debug`. `/zoo debug perf` with no zoo works from RCON and the console.
- CSV files go in the server's `logs` folder: `logs/zoocraft-perf.csv` and `logs/zoocraft-recognition.csv`.
- New config values are read live, like the existing ones. Their defaults reproduce today's behaviour exactly: `visitorStepTicks` 10, `viewingIntervalTicks` 20, `litterCheckIntervalTicks` 40.
- Player-facing text uses British spelling. The command is `recognise`.
- No new dependencies. Spark is not bundled or required.

## Review Focus

- **A config value changed while visitors are walking** (for example `visitorStepTicks` from 10 to 3). Needs, interest and urgent-need losses must keep their per-second rates, and no visitor may skip or double its step logic. Task 5 pins this with JUnit `StepRates` equivalence tests and a GameTest that changes the step mid-visit.
- **An exception inside a timed system** (a modded entity throwing during the census). The timer nesting must stay balanced, or every later ZooCraft total is wrong. Task 1 pins `TimerSlots` close-after-throw behaviour, and Task 2 wraps every timer in `try/finally`.
- **A modded entity type whose constructor throws or returns null** during `/zoo debug recognise`. The report must record "could not be checked" with the category-only verdict and carry on through the rest of the registry. Task 4 pins this with a GameTest that passes a throwing factory.
- **A zoo deleted while its timers exist.** The per-zoo slots must be pruned, and later `stop` calls for that zoo must not throw. Task 2 pins it with a GameTest.
- **`/zoo debug perf log` started twice, or the server stopping mid-log.** The second start replaces the first cleanly, and stopping closes the file. Task 3 pins it with a GameTest.

---

### Task 1: Timer slots, slot list and limit rule (pure logic)

**Files:**
- Create: `src/main/java/com/zoocraft/core/perf/TimerSlots.java`
- Create: `src/main/java/com/zoocraft/core/perf/PerfSlot.java`
- Create: `src/main/java/com/zoocraft/core/perf/LimitRule.java`
- Test: `src/test/java/com/zoocraft/core/perf/TimerSlotsTest.java`
- Test: `src/test/java/com/zoocraft/core/perf/LimitRuleTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:

```java
public enum PerfSlot {
    SERVER_TICK("server tick"), ZOOCRAFT_TOTAL("ZooCraft total"),
    VISITOR_CONTROLLER("visitor decisions"), VISITOR_BRAIN("visitor brain"),
    VISITOR_NAVIGATION("visitor path following"), VISITOR_PATHFINDING("visitor path planning"),
    ANIMAL_CENSUS("animal census"), FACILITY_CENSUS("facility census"), ENCLOSURE("enclosure checks"),
    SPAWNER("visitor spawner"), DASHBOARD("dashboard"), LITTER("litter"), CHUNK_LOADER("chunk loading");
    public String label();
}
public final class TimerSlots {
    public static final int WINDOW = 600;
    public TimerSlots(int slots, int window);
    public void open();                                  // entering a timed section
    public void close(int slot, long nanos, int totalSlot); // leaving it; adds nanos to slot, and to totalSlot when outermost
    public void add(int slot, long nanos);               // adds without nesting (server tick)
    public void endTick();
    public int ticks();                                  // ticks recorded, at most the window
    public double averageNanos(int slot);                // per tick over the window
    public double averageNanos(int slot, int lastTicks); // per tick over the most recent lastTicks
    public long worstNanos(int slot);
    public long calls(int slot);
    public int depth();
}
public final class LimitRule {
    public static final double BUDGET_MS = 10.0;
    public static final int MAX_TOTAL = 2000;
    public static final int MAX_PER_ZOO = 500;
    public static double costPerVisitor(double baselineMs, double at100Ms, double at200Ms);
    public static int serverDefault(double costPerVisitorMs);
    public static int perZooDefault(int serverDefault);
}
```

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/zoocraft/core/perf/TimerSlotsTest.java`:

```java
package com.zoocraft.core.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimerSlotsTest {
    private static final int A = 0, B = 1, TOTAL = 2;

    @Test
    void averagesPerTickOverTheWindow() {
        TimerSlots t = new TimerSlots(3, 4);
        t.add(A, 100);
        t.add(A, 100);
        t.endTick();
        t.endTick(); // a tick with nothing in it
        assertEquals(2, t.ticks());
        assertEquals(100.0, t.averageNanos(A), 1e-9);
        assertEquals(200, t.worstNanos(A));
        assertEquals(2, t.calls(A));
    }

    @Test
    void oldTicksFallOutOfTheWindow() {
        TimerSlots t = new TimerSlots(3, 2);
        t.add(A, 1000);
        t.endTick();
        t.add(A, 10);
        t.endTick();
        t.add(A, 20);
        t.endTick();
        assertEquals(2, t.ticks());
        assertEquals(15.0, t.averageNanos(A), 1e-9);
        assertEquals(20, t.worstNanos(A));
        assertEquals(2, t.calls(A));
    }

    @Test
    void recentAverageUsesOnlyTheLastTicks() {
        TimerSlots t = new TimerSlots(3, 10);
        for (int i = 0; i < 5; i++) {
            t.add(A, 100);
            t.endTick();
        }
        t.add(A, 400);
        t.endTick();
        assertEquals(250.0, t.averageNanos(A, 2), 1e-9);
        assertEquals(400.0, t.averageNanos(A, 1), 1e-9);
        assertEquals(150.0, t.averageNanos(A, 100), 1e-9); // more than recorded: uses what there is
    }

    @Test
    void nestedTimeCountsOnceInTheTotal() {
        TimerSlots t = new TimerSlots(3, 4);
        t.open();          // outer A
        t.open();          // inner B
        t.close(B, 30, TOTAL);
        t.close(A, 100, TOTAL);
        t.endTick();
        assertEquals(100.0, t.averageNanos(A), 1e-9);
        assertEquals(30.0, t.averageNanos(B), 1e-9);
        assertEquals(100.0, t.averageNanos(TOTAL), 1e-9);
        assertEquals(0, t.depth());
    }

    @Test
    void closingMoreThanOpenedNeverGoesNegative() {
        TimerSlots t = new TimerSlots(3, 4);
        t.close(A, 5, TOTAL);
        assertEquals(0, t.depth());
        t.open();
        t.close(A, 7, TOTAL);
        t.endTick();
        assertEquals(12.0, t.averageNanos(A), 1e-9);
        assertEquals(12.0, t.averageNanos(TOTAL), 1e-9);
    }

    @Test
    void emptyTimersReportZero() {
        TimerSlots t = new TimerSlots(3, 4);
        assertEquals(0.0, t.averageNanos(A), 1e-9);
        assertEquals(0, t.worstNanos(A));
        assertEquals(0, t.calls(A));
    }
}
```

`src/test/java/com/zoocraft/core/perf/LimitRuleTest.java`:

```java
package com.zoocraft.core.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LimitRuleTest {
    @Test
    void usesTheWorseOfTheTwoMeasurements() {
        // Phase 3 spec section 3.2: 9.1 ms empty, 17.5 ms at 100, 29.8 ms at 200.
        assertEquals(0.1035, LimitRule.costPerVisitor(9.1, 17.5, 29.8), 1e-9);
    }

    @Test
    void todaysNumbersGiveFortyFive() {
        assertEquals(45, LimitRule.serverDefault(0.1035));
        assertEquals(20, LimitRule.perZooDefault(45));
    }

    @Test
    void theOptimisationTargetGivesOneHundredAndTwentyFive() {
        assertEquals(125, LimitRule.serverDefault(0.04));
        assertEquals(60, LimitRule.perZooDefault(125));
    }

    @Test
    void perZooDefaultIsAtLeastTwelve() {
        assertEquals(12, LimitRule.perZooDefault(10));
    }

    @Test
    void freeOrNegativeCostsHitTheCeilings() {
        assertEquals(LimitRule.MAX_TOTAL / 2, LimitRule.serverDefault(0.0));
        assertEquals(LimitRule.MAX_TOTAL / 2, LimitRule.serverDefault(-1));
        assertEquals(LimitRule.MAX_PER_ZOO, LimitRule.perZooDefault(LimitRule.MAX_TOTAL * 2));
    }
}
```

- [ ] **Step 2: Run them and check they fail**

Run: `./gradlew test --tests "com.zoocraft.core.perf.*"`
Expected: compilation failure, `TimerSlots`, `LimitRule` not found.

- [ ] **Step 3: Implement**

`src/main/java/com/zoocraft/core/perf/PerfSlot.java`:

```java
package com.zoocraft.core.perf;

/** What ZooCraft times (Phase 3 spec section 6.1). Order is the CSV column order. */
public enum PerfSlot {
    SERVER_TICK("server tick"),
    ZOOCRAFT_TOTAL("ZooCraft total"),
    VISITOR_CONTROLLER("visitor decisions"),
    VISITOR_BRAIN("visitor brain"),
    VISITOR_NAVIGATION("visitor path following"),
    VISITOR_PATHFINDING("visitor path planning"),
    ANIMAL_CENSUS("animal census"),
    FACILITY_CENSUS("facility census"),
    ENCLOSURE("enclosure checks"),
    SPAWNER("visitor spawner"),
    DASHBOARD("dashboard"),
    LITTER("litter"),
    CHUNK_LOADER("chunk loading");

    private final String label;

    PerfSlot(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
```

`src/main/java/com/zoocraft/core/perf/TimerSlots.java`:

```java
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

    public int depth() {
        return depth;
    }
}
```

`src/main/java/com/zoocraft/core/perf/LimitRule.java`:

```java
package com.zoocraft.core.perf;

/** How default visitor limits are set from measurements (Phase 3 spec section 9.2). */
public final class LimitRule {
    /** Visitors may cost at most this much tick time on the reference machine before halving. */
    public static final double BUDGET_MS = 10.0;
    public static final int MAX_TOTAL = 2000;
    public static final int MAX_PER_ZOO = 500;

    private LimitRule() {
    }

    /** Milliseconds per tick each visitor adds: the worse of the 100 and 200 visitor measurements. */
    public static double costPerVisitor(double baselineMs, double at100Ms, double at200Ms) {
        return Math.max((at100Ms - baselineMs) / 100.0, (at200Ms - baselineMs) / 200.0);
    }

    /** The largest multiple of 10 visitors within the budget, halved for weaker servers. */
    public static int serverDefault(double costPerVisitorMs) {
        if (costPerVisitorMs <= 0) {
            return MAX_TOTAL / 2;
        }
        int withinBudget = (int) Math.floor(BUDGET_MS / costPerVisitorMs / 10.0) * 10;
        return Math.min(withinBudget, MAX_TOTAL) / 2;
    }

    /** Half the server default, rounded down to a multiple of 4, at least 12. */
    public static int perZooDefault(int serverDefault) {
        int half = (serverDefault / 2) / 4 * 4;
        return Math.min(MAX_PER_ZOO, Math.max(12, half));
    }
}
```

- [ ] **Step 4: Run the tests and check they pass**

Run: `./gradlew test --tests "com.zoocraft.core.perf.*"`
Expected: PASS (11 tests).

Check by hand: `serverDefault(0.1035)` gives 10 / 0.1035 = 96.6, then 90, halved to 45. `perZooDefault(45)` gives 22, rounded down to 20. `serverDefault(0.04)` gives 250, halved to 125. `perZooDefault(125)` gives 62, rounded down to 60.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/zoocraft/core/perf src/test/java/com/zoocraft/core/perf
git commit -m "M9: timer slots with nesting and a rolling window, perf slot list, limit rule"
```

---

### Task 2: Simulation timers wired into every system

**Files:**
- Create: `src/main/java/com/zoocraft/perf/SimulationTimers.java`
- Modify: `src/main/java/com/zoocraft/visitor/Visitor.java` (`customServerAiStep`, two public accessors)
- Modify: `src/main/java/com/zoocraft/visitor/VisitorController.java` (`ticksWithoutProgress`)
- Modify: `src/main/java/com/zoocraft/visitor/VisitorNavigation.java` (time `tick` and `createPath`)
- Modify: `src/main/java/com/zoocraft/animal/AnimalCensus.java` (`refresh`)
- Modify: `src/main/java/com/zoocraft/shop/FacilityCensus.java` (`refresh`)
- Modify: `src/main/java/com/zoocraft/visitor/VisitorSpawner.java` (`onServerTick`)
- Modify: `src/main/java/com/zoocraft/dashboard/DashboardService.java` (`onServerTick`)
- Modify: `src/main/java/com/zoocraft/visitor/VisitorExploration.java` (`noticeLitter`)
- Modify: `src/main/java/com/zoocraft/litter/Littering.java` (`drop`)
- Modify: `src/main/java/com/zoocraft/chunk/ZooChunkLoader.java` (`onServerTick`)
- Test: `src/main/java/com/zoocraft/gametest/PerfGameTests.java`

**Interfaces:**
- Consumes: `TimerSlots`, `PerfSlot` (Task 1).
- Produces:

```java
public final class SimulationTimers {          // package com.zoocraft.perf
    public static long start();                // System.nanoTime(), and opens a nesting level
    public static void stop(PerfSlot slot, @Nullable UUID zooId, long start);
    public static TimerSlots global();
    public static Optional<TimerSlots> forZoo(UUID zooId);
    public static void prune(Set<UUID> liveZoos);
    public static void resetForTest();
}
// Visitor
public long ticksWithoutProgress();            // 0 while not walking towards a target
public @Nullable BlockPos walkTarget();
// VisitorController
public long ticksWithoutProgress(long now);
```

The usage pattern everywhere, with `try/finally` so an exception can't unbalance the nesting:

```java
long timer = SimulationTimers.start();
try {
    // existing body
} finally {
    SimulationTimers.stop(PerfSlot.X, zooIdOrNull, timer);
}
```

- [ ] **Step 1: Write the failing GameTest**

`src/main/java/com/zoocraft/gametest/PerfGameTests.java`:

```java
package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import com.zoocraft.core.perf.PerfSlot;
import com.zoocraft.core.perf.TimerSlots;
import com.zoocraft.core.visitor.VisitorProfile;
import com.zoocraft.perf.SimulationTimers;
import com.zoocraft.registry.ZooEntities;
import com.zoocraft.visitor.Visitor;
import com.zoocraft.visitor.VisitorPhase;
import com.zoocraft.zoo.Gate;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.UUID;

/** M9: timers, the perf report and log, and the recognition report. */
@GameTestHolder(ZooCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PerfGameTests {
    private static final BlockPos GATE = new BlockPos(16, 2, 24);

    record Setup(ServerPlayer owner, Zoo zoo, Gate gate) {
    }

    static Setup openZoo(GameTestHelper helper, String name) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, name, TestZoos.rectangle(helper, 16, 4, 44, 44));
        ZooManager zoos = TestZoos.manager(helper);
        helper.assertTrue(zoos.addGate(owner, helper.getLevel(), helper.absolutePos(GATE)).success(), "gate not added");
        zoos.setPrice(owner, zoo, 0);
        zoos.setOpen(owner, zoo, true);
        return new Setup(owner, zoo, zoo.gates().getFirst());
    }

    static Visitor exploringVisitor(GameTestHelper helper, Setup setup, int x, int z) {
        Visitor visitor = ZooEntities.VISITOR.get().create(helper.getLevel());
        BlockPos at = helper.absolutePos(new BlockPos(x, 2, z));
        visitor.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        visitor.begin(setup.zoo(), setup.gate(), UUID.randomUUID(), true);
        visitor.testSetBudget(VisitorProfile.GENERAL, 10, 1.0);
        visitor.testSetPhase(VisitorPhase.EXPLORING);
        helper.getLevel().addFreshEntity(visitor);
        return visitor;
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 400)
    public static void visitorTimeIsRecordedPerZoo(GameTestHelper helper) {
        Setup setup = openZoo(helper, "Timed");
        Visitor visitor = exploringVisitor(helper, setup, 30, 24);
        helper.runAfterDelay(100, () -> {
            TimerSlots zoo = SimulationTimers.forZoo(setup.zoo().id()).orElse(null);
            helper.assertTrue(zoo != null, "no timers for the zoo");
            for (PerfSlot slot : new PerfSlot[]{PerfSlot.VISITOR_CONTROLLER, PerfSlot.VISITOR_BRAIN, PerfSlot.VISITOR_NAVIGATION}) {
                helper.assertTrue(zoo.calls(slot.ordinal()) > 0, slot + " has no calls");
                helper.assertTrue(zoo.calls(slot.ordinal()) <= SimulationTimers.global().calls(slot.ordinal()),
                        slot + ": the zoo has more calls than the whole server");
            }
            helper.assertTrue(SimulationTimers.global().calls(PerfSlot.SERVER_TICK.ordinal()) > 0, "server tick not timed");
            helper.assertTrue(SimulationTimers.global().depth() == 0, "timer nesting unbalanced: " + SimulationTimers.global().depth());
            visitor.discard();
            TestZoos.cleanUp(helper, setup.owner(), setup.zoo());
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void deletedZoosLoseTheirTimers(GameTestHelper helper) {
        UUID gone = UUID.randomUUID();
        long start = SimulationTimers.start();
        SimulationTimers.stop(PerfSlot.ANIMAL_CENSUS, gone, start);
        helper.assertTrue(SimulationTimers.forZoo(gone).isPresent(), "timers not created for the zoo");
        SimulationTimers.prune(Set.of());
        helper.assertTrue(SimulationTimers.forZoo(gone).isEmpty(), "timers kept for a deleted zoo");
        // Late stops for a pruned zoo are fine.
        long again = SimulationTimers.start();
        SimulationTimers.stop(PerfSlot.ANIMAL_CENSUS, gone, again);
        helper.succeed();
    }
}
```

- [ ] **Step 2: Run it and check it fails**

Run: `tools/gametest.sh`
Expected: compilation failure, `com.zoocraft.perf.SimulationTimers` not found.

- [ ] **Step 3: Implement `SimulationTimers`**

`src/main/java/com/zoocraft/perf/SimulationTimers.java`:

```java
package com.zoocraft.perf;

import com.zoocraft.ZooCraft;
import com.zoocraft.core.perf.PerfSlot;
import com.zoocraft.core.perf.TimerSlots;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * ZooCraft's own cost per tick, server-wide and per zoo (Phase 3 spec section 4.2 and 6.1). Always on: a timed call
 * costs two {@code nanoTime} reads and a few array writes. Server thread only.
 */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class SimulationTimers {
    private static final int SLOTS = PerfSlot.values().length;
    private static final int TOTAL = PerfSlot.ZOOCRAFT_TOTAL.ordinal();
    private static final int PRUNE_TICKS = 600;

    private static TimerSlots global = new TimerSlots(SLOTS, TimerSlots.WINDOW);
    private static final Map<UUID, TimerSlots> ZOOS = new HashMap<>();
    private static long tickStart;

    private SimulationTimers() {
    }

    /** Starts timing; pair with {@link #stop} in a {@code finally} block. */
    public static long start() {
        global.open();
        return System.nanoTime();
    }

    public static void stop(PerfSlot slot, @Nullable UUID zooId, long start) {
        long nanos = System.nanoTime() - start;
        boolean outermost = global.depth() == 1;
        global.close(slot.ordinal(), nanos, TOTAL);
        if (zooId != null) {
            TimerSlots zoo = ZOOS.computeIfAbsent(zooId, id -> new TimerSlots(SLOTS, TimerSlots.WINDOW));
            zoo.add(slot.ordinal(), nanos);
            if (outermost) {
                zoo.add(TOTAL, nanos);
            }
        }
    }

    public static TimerSlots global() {
        return global;
    }

    public static Optional<TimerSlots> forZoo(UUID zooId) {
        return Optional.ofNullable(ZOOS.get(zooId));
    }

    /** Drops the timers of zoos that no longer exist. */
    public static void prune(Set<UUID> liveZoos) {
        ZOOS.keySet().retainAll(liveZoos);
    }

    public static void resetForTest() {
        global = new TimerSlots(SLOTS, TimerSlots.WINDOW);
        ZOOS.clear();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onTickStart(ServerTickEvent.Pre event) {
        tickStart = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onTickEnd(ServerTickEvent.Post event) {
        global.add(PerfSlot.SERVER_TICK.ordinal(), System.nanoTime() - tickStart);
        global.endTick();
        ZOOS.values().forEach(TimerSlots::endTick);
        if (event.getServer().getTickCount() % PRUNE_TICKS == 0) {
            Set<UUID> live = new HashSet<>();
            for (Zoo zoo : ZooManager.get(event.getServer()).all()) {
                live.add(zoo.id());
            }
            prune(live);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        resetForTest();
    }
}
```

The per-zoo `TimerSlots` uses `add`, not `open`/`close`. Nesting is tracked once, on the global timers, and the zoo's total is credited when the global nesting says the call was the outermost.

- [ ] **Step 4: Visitor brain, controller and accessors**

In `Visitor.java`, replace `customServerAiStep`:

```java
    @Override
    protected void customServerAiStep() {
        long brain = SimulationTimers.start();
        try {
            tickBrain(this);
        } finally {
            SimulationTimers.stop(PerfSlot.VISITOR_BRAIN, zooId, brain);
        }
        long decisions = SimulationTimers.start();
        try {
            controller.tick();
        } finally {
            SimulationTimers.stop(PerfSlot.VISITOR_CONTROLLER, zooId, decisions);
        }
        if (commentTicks > 0 && --commentTicks == 0) {
            entityData.set(DATA_COMMENT, "");
        }
    }
```

and add, next to `pathRules()`:

```java
    /** Ticks since this visitor last got closer to its target; 0 while it isn't walking anywhere. */
    public long ticksWithoutProgress() {
        return controller.ticksWithoutProgress(level().getGameTime());
    }

    public @Nullable BlockPos walkTarget() {
        return controller.target();
    }
```

Add the imports `com.zoocraft.core.perf.PerfSlot`, `com.zoocraft.perf.SimulationTimers` and `net.minecraft.core.BlockPos`.

In `VisitorController.java`, next to `target()`:

```java
    public long ticksWithoutProgress(long now) {
        return target == null ? 0 : now - lastProgressTime;
    }
```

- [ ] **Step 5: Navigation and path planning**

Replace `VisitorNavigation.java`:

```java
package com.zoocraft.visitor;

import com.zoocraft.core.perf.PerfSlot;
import com.zoocraft.perf.SimulationTimers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.tslat.smartbrainlib.api.core.navigation.SmoothGroundNavigation;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/** SmartBrainLib's smooth ground navigation with the visitor node evaluator, timed for {@code /zoo debug perf}. */
public class VisitorNavigation extends SmoothGroundNavigation {
    public VisitorNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new VisitorNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        this.nodeEvaluator.setCanOpenDoors(true);
        return createSmoothPathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    @Override
    public void tick() {
        long timer = SimulationTimers.start();
        try {
            super.tick();
        } finally {
            SimulationTimers.stop(PerfSlot.VISITOR_NAVIGATION, zooId(), timer);
        }
    }

    @Override
    protected @Nullable Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange) {
        long timer = SimulationTimers.start();
        try {
            return super.createPath(targets, regionOffset, offsetUpward, accuracy, followRange);
        } finally {
            SimulationTimers.stop(PerfSlot.VISITOR_PATHFINDING, zooId(), timer);
        }
    }

    private @Nullable UUID zooId() {
        return mob instanceof Visitor visitor ? visitor.zooId() : null;
    }
}
```

If the compiler reports that `createPath(Set, int, boolean, int, float)` doesn't match a superclass method, open `net.minecraft.world.entity.ai.navigation.PathNavigation` in the IDE's decompiled sources and override the overload that every other `createPath` delegates to.

- [ ] **Step 6: Censuses**

In `AnimalCensus.java`, rename the existing `public static ZooCensus refresh(Zoo zoo, ServerLevel level)` to `private static ZooCensus doRefresh(Zoo zoo, ServerLevel level)` and add above it:

```java
    public static ZooCensus refresh(Zoo zoo, ServerLevel level) {
        long timer = SimulationTimers.start();
        try {
            return doRefresh(zoo, level);
        } finally {
            SimulationTimers.stop(PerfSlot.ANIMAL_CENSUS, zoo.id(), timer);
        }
    }
```

Do the same in `FacilityCensus.java`: rename `refresh` to `private static FacilitySnapshot doRefresh(Zoo zoo, ServerLevel level)` and add:

```java
    public static FacilitySnapshot refresh(Zoo zoo, ServerLevel level) {
        long timer = SimulationTimers.start();
        try {
            return doRefresh(zoo, level);
        } finally {
            SimulationTimers.stop(PerfSlot.FACILITY_CENSUS, zoo.id(), timer);
        }
    }
```

Add the `PerfSlot` and `SimulationTimers` imports to both.

- [ ] **Step 7: Spawner, dashboard, chunk loader, litter**

`VisitorSpawner.onServerTick`: rename the body to `private static void spawnTick(MinecraftServer server)`, taking `event.getServer()`, and make the handler:

```java
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        long timer = SimulationTimers.start();
        try {
            spawnTick(event.getServer());
        } finally {
            SimulationTimers.stop(PerfSlot.SPAWNER, null, timer);
        }
    }
```

`DashboardService.onServerTick`:

```java
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % REFRESH_TICKS == 0 && !SUBSCRIPTIONS.isEmpty()) {
            long timer = SimulationTimers.start();
            try {
                refreshAll(event.getServer());
            } finally {
                SimulationTimers.stop(PerfSlot.DASHBOARD, null, timer);
            }
        }
    }
```

`ZooChunkLoader.onServerTick`:

```java
    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % UPDATE_TICKS != 0) {
            return;
        }
        long timer = SimulationTimers.start();
        try {
            update(event.getServer());
        } finally {
            SimulationTimers.stop(PerfSlot.CHUNK_LOADER, null, timer);
        }
    }
```

`VisitorExploration.noticeLitter`: keep the early return for a missing server and the interval check (Task 5 changes the interval check). Wrap everything after them:

```java
        long timer = SimulationTimers.start();
        try {
            // existing body: count units, record the sample, react
        } finally {
            SimulationTimers.stop(PerfSlot.LITTER, zoo.id(), timer);
        }
```

`Littering.drop(ServerLevel level, Zoo zoo, Vec3 at, int variant)`: rename the existing body to `private static LitterRules.Outcome doDrop(...)` with the same parameters and add:

```java
    public static LitterRules.Outcome drop(ServerLevel level, Zoo zoo, Vec3 at, int variant) {
        long timer = SimulationTimers.start();
        try {
            return doDrop(level, zoo, at, variant);
        } finally {
            SimulationTimers.stop(PerfSlot.LITTER, zoo.id(), timer);
        }
    }
```

`PerfSlot.ENCLOSURE` stays unused until M11.

- [ ] **Step 8: Build and run the GameTests**

Run: `./gradlew build` then `tools/gametest.sh`
Expected: BUILD SUCCESSFUL. All GameTests pass, including `visitortimeisrecordedperzoo` and `deletedzooslosetheirtimers`. The test names appear in lower case in the summary.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/zoocraft/perf src/main/java/com/zoocraft/gametest/PerfGameTests.java src/main/java/com/zoocraft/visitor src/main/java/com/zoocraft/animal/AnimalCensus.java src/main/java/com/zoocraft/shop/FacilityCensus.java src/main/java/com/zoocraft/dashboard/DashboardService.java src/main/java/com/zoocraft/litter/Littering.java src/main/java/com/zoocraft/chunk/ZooChunkLoader.java
git commit -m "M9: ZooCraft times its systems per tick and per zoo"
```

---

### Task 3: `/zoo debug perf` and the CSV log

**Files:**
- Create: `src/main/java/com/zoocraft/perf/PerfReport.java`
- Create: `src/main/java/com/zoocraft/perf/PerfLog.java`
- Create: `src/main/java/com/zoocraft/command/PerfCommands.java`
- Modify: `src/main/java/com/zoocraft/command/ZooDebugCommands.java` (register the subtree)
- Test: `src/main/java/com/zoocraft/gametest/PerfGameTests.java` (add tests)

**Interfaces:**
- Consumes: `SimulationTimers`, `PerfSlot`, `TimerSlots` (Tasks 1 and 2); `Visitor.ticksWithoutProgress()`, `Visitor.walkTarget()` (Task 2); existing `VisitorTracker.visitors(UUID)`, `VisitorTracker.total()`, `ZooManager.all()`, `AnimalCensus.cached(Zoo)`, `Exhibit.viewCount()`, `ZooConfig.MAX_VISITORS_PER_ZOO`, `ZooConfig.MAX_VISITORS_TOTAL`.
- Produces:

```java
public final class PerfReport {                  // package com.zoocraft.perf
    public static final long STUCK_TICKS = 200;  // 10 seconds
    public static List<Component> server(MinecraftServer server);
    public static List<Component> zoo(MinecraftServer server, Zoo zoo);
    public static int stuckVisitors(List<Visitor> visitors);
    public static int mostOnOneTarget(List<Visitor> visitors);
    public static double ms(double nanos);
}
public final class PerfLog {
    public static Path start(MinecraftServer server, int seconds) throws IOException;
    public static boolean running();
    public static void stop();
}
```

- [ ] **Step 1: Write the failing GameTests**

Add to `PerfGameTests`:

```java
    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 300)
    public static void perfReportDescribesTheZoo(GameTestHelper helper) {
        Setup setup = openZoo(helper, "Reported");
        Visitor visitor = exploringVisitor(helper, setup, 30, 24);
        helper.runAfterDelay(60, () -> {
            String server = String.join("\n", PerfReport.server(helper.getLevel().getServer()).stream().map(Component::getString).toList());
            helper.assertTrue(server.contains("Server tick"), "no server tick line:\n" + server);
            helper.assertTrue(server.contains("Reported"), "zoo missing from the server report:\n" + server);
            String zoo = String.join("\n", PerfReport.zoo(helper.getLevel().getServer(), setup.zoo()).stream().map(Component::getString).toList());
            helper.assertTrue(zoo.contains("Stuck visitors: 0"), "stuck line missing:\n" + zoo);
            helper.assertTrue(zoo.contains("Exhibits"), "coverage line missing:\n" + zoo);
            visitor.discard();
            TestZoos.cleanUp(helper, setup.owner(), setup.zoo());
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR, timeoutTicks = 200)
    public static void perfLogWritesALinePerSecondAndRestartsCleanly(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path first;
        try {
            PerfLog.start(server, 10);
            first = PerfLog.start(server, 3); // starting again replaces the first log
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        helper.runAfterDelay(90, () -> {
            helper.assertFalse(PerfLog.running(), "log still running after its time");
            List<String> lines;
            try {
                lines = Files.readAllLines(first);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            helper.assertTrue(lines.getFirst().startsWith("seconds,server_tick_ms"), "bad header: " + lines.getFirst());
            helper.assertTrue(lines.size() >= 3 && lines.size() <= 5, "expected about 3 data lines, got " + (lines.size() - 1));
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void stuckAndCrowdingCountsComeFromVisitors(GameTestHelper helper) {
        helper.assertTrue(PerfReport.stuckVisitors(List.of()) == 0, "stuck in an empty list");
        helper.assertTrue(PerfReport.mostOnOneTarget(List.of()) == 0, "crowding in an empty list");
        helper.succeed();
    }
```

Add the imports `com.zoocraft.perf.PerfLog`, `com.zoocraft.perf.PerfReport`, `net.minecraft.network.chat.Component`, `net.minecraft.server.MinecraftServer`, `java.io.IOException`, `java.nio.file.Files`, `java.nio.file.Path` and `java.util.List`.

- [ ] **Step 2: Run them and check they fail**

Run: `./gradlew build`
Expected: compilation failure, `PerfReport` and `PerfLog` not found.

- [ ] **Step 3: Implement `PerfReport`**

`src/main/java/com/zoocraft/perf/PerfReport.java`:

```java
package com.zoocraft.perf;

import com.zoocraft.animal.AnimalCensus;
import com.zoocraft.animal.Exhibit;
import com.zoocraft.config.ZooConfig;
import com.zoocraft.core.perf.PerfSlot;
import com.zoocraft.core.perf.TimerSlots;
import com.zoocraft.visitor.Visitor;
import com.zoocraft.visitor.VisitorPhase;
import com.zoocraft.visitor.VisitorTracker;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The text of {@code /zoo debug perf} (Phase 3 spec section 6.2). Operator diagnostics, so plain literals. */
public final class PerfReport {
    /** A visitor this long without getting closer to its target counts as stuck. */
    public static final long STUCK_TICKS = 200;
    private static final int STUCK_SHOWN = 3;

    private PerfReport() {
    }

    public static List<Component> server(MinecraftServer server) {
        List<Component> lines = new ArrayList<>();
        TimerSlots t = SimulationTimers.global();
        double tick = ms(t.averageNanos(PerfSlot.SERVER_TICK.ordinal()));
        double total = ms(t.averageNanos(PerfSlot.ZOOCRAFT_TOTAL.ordinal()));
        lines.add(Component.literal("ZooCraft performance, last " + t.ticks() / 20 + " s").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        lines.add(Component.literal(String.format("Server tick: average %.1f ms, worst %.1f ms. ZooCraft: average %.2f ms (%.0f%%), worst %.1f ms.",
                tick, ms(t.worstNanos(PerfSlot.SERVER_TICK.ordinal())), total, tick > 0 ? 100 * total / tick : 0,
                ms(t.worstNanos(PerfSlot.ZOOCRAFT_TOTAL.ordinal())))));
        lines.addAll(slotLines(t));
        lines.add(Component.literal(String.format("Visitors: %d of %d allowed on the server.", VisitorTracker.total(),
                ZooConfig.MAX_VISITORS_TOTAL.get())));
        for (Zoo zoo : ZooManager.get(server).all()) {
            List<Visitor> visitors = VisitorTracker.visitors(zoo.id());
            if (visitors.isEmpty() && !zoo.open()) {
                continue;
            }
            double zooMs = SimulationTimers.forZoo(zoo.id()).map(z -> ms(z.averageNanos(PerfSlot.ZOOCRAFT_TOTAL.ordinal()))).orElse(0.0);
            lines.add(Component.literal(String.format(" - %s: %d of %d visitors (%s), %.2f ms per tick, %d stuck", zoo.name(),
                    visitors.size(), ZooConfig.MAX_VISITORS_PER_ZOO.get(), phases(visitors), zooMs, stuckVisitors(visitors))));
        }
        return lines;
    }

    public static List<Component> zoo(MinecraftServer server, Zoo zoo) {
        List<Component> lines = new ArrayList<>();
        List<Visitor> visitors = VisitorTracker.visitors(zoo.id());
        lines.add(Component.literal(zoo.name() + ": " + visitors.size() + " visitors (" + phases(visitors) + ")")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        SimulationTimers.forZoo(zoo.id()).ifPresent(t -> lines.addAll(slotLines(t)));

        List<Visitor> stuck = visitors.stream().filter(v -> v.admitted() && v.ticksWithoutProgress() > STUCK_TICKS)
                .sorted(Comparator.comparingLong(Visitor::ticksWithoutProgress).reversed()).toList();
        StringBuilder stuckLine = new StringBuilder("Stuck visitors: " + stuck.size());
        for (int i = 0; i < Math.min(STUCK_SHOWN, stuck.size()); i++) {
            Visitor v = stuck.get(i);
            stuckLine.append(i == 0 ? " (" : ", ").append(v.blockPosition().toShortString())
                    .append(" for ").append(v.ticksWithoutProgress() / 20).append(" s");
        }
        lines.add(Component.literal(stuck.isEmpty() ? stuckLine.toString() : stuckLine.append(")").toString()));
        lines.add(Component.literal("Most visitors heading for one spot: " + mostOnOneTarget(visitors)));

        List<Exhibit> exhibits = AnimalCensus.cached(zoo).exhibits();
        if (exhibits.isEmpty()) {
            lines.add(Component.literal("Exhibits: none counted yet."));
        } else {
            int[] views = exhibits.stream().mapToInt(Exhibit::viewCount).sorted().toArray();
            long never = Arrays.stream(views).filter(v -> v == 0).count();
            lines.add(Component.literal(String.format("Exhibits: %d, views per exhibit min %d, median %d, max %d, never viewed %d",
                    views.length, views[0], views[views.length / 2], views[views.length - 1], never)));
        }
        return lines;
    }

    /** Admitted visitors that have not got closer to their target for {@link #STUCK_TICKS}. */
    public static int stuckVisitors(List<Visitor> visitors) {
        int stuck = 0;
        for (Visitor v : visitors) {
            if (v.admitted() && v.ticksWithoutProgress() > STUCK_TICKS) {
                stuck++;
            }
        }
        return stuck;
    }

    /** The most visitors walking to, or standing at, the same block. M10 replaces this with real occupancy counts. */
    public static int mostOnOneTarget(List<Visitor> visitors) {
        Map<BlockPos, Integer> counts = new HashMap<>();
        int most = 0;
        for (Visitor v : visitors) {
            BlockPos target = v.walkTarget();
            if (target != null) {
                most = Math.max(most, counts.merge(target, 1, Integer::sum));
            }
        }
        return most;
    }

    public static double ms(double nanos) {
        return nanos / 1_000_000.0;
    }

    private static List<Component> slotLines(TimerSlots t) {
        List<PerfSlot> slots = new ArrayList<>();
        for (PerfSlot slot : PerfSlot.values()) {
            if (slot != PerfSlot.SERVER_TICK && slot != PerfSlot.ZOOCRAFT_TOTAL && t.calls(slot.ordinal()) > 0) {
                slots.add(slot);
            }
        }
        slots.sort(Comparator.comparingDouble((PerfSlot s) -> t.averageNanos(s.ordinal())).reversed());
        List<Component> lines = new ArrayList<>();
        for (PerfSlot slot : slots) {
            lines.add(Component.literal(String.format("   %s: %.3f ms per tick, worst %.2f ms, %d calls", slot.label(),
                    ms(t.averageNanos(slot.ordinal())), ms(t.worstNanos(slot.ordinal())), t.calls(slot.ordinal()))));
        }
        return lines;
    }

    private static String phases(List<Visitor> visitors) {
        Map<VisitorPhase, Integer> counts = new EnumMap<>(VisitorPhase.class);
        visitors.forEach(v -> counts.merge(v.phase(), 1, Integer::sum));
        List<String> parts = new ArrayList<>();
        counts.forEach((phase, n) -> parts.add(n + " " + phase.name().toLowerCase(java.util.Locale.ROOT)));
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }
}
```

- [ ] **Step 4: Implement `PerfLog`**

`src/main/java/com/zoocraft/perf/PerfLog.java`:

```java
package com.zoocraft.perf;

import com.zoocraft.ZooCraft;
import com.zoocraft.core.perf.PerfSlot;
import com.zoocraft.core.perf.TimerSlots;
import com.zoocraft.visitor.VisitorTracker;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** {@code /zoo debug perf log}: one CSV line per second to {@code logs/zoocraft-perf.csv} (Phase 3 spec section 6.2). */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class PerfLog {
    private static BufferedWriter out;
    private static int secondsLeft;
    private static int elapsed;

    private PerfLog() {
    }

    /** Starts a new log, replacing any running one. */
    public static Path start(MinecraftServer server, int seconds) throws IOException {
        stop();
        Path file = server.getServerDirectory().resolve("logs").resolve("zoocraft-perf.csv");
        Files.createDirectories(file.getParent());
        out = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        StringBuilder header = new StringBuilder("seconds");
        for (PerfSlot slot : PerfSlot.values()) {
            header.append(',').append(slot.name().toLowerCase(Locale.ROOT)).append("_ms");
        }
        out.write(header.append(",visitors\n").toString());
        secondsLeft = seconds;
        elapsed = 0;
        return file;
    }

    public static boolean running() {
        return out != null;
    }

    public static void stop() {
        if (out == null) {
            return;
        }
        try {
            out.close();
        } catch (IOException e) {
            ZooCraft.LOGGER.warn("Couldn't close the ZooCraft perf log", e);
        }
        out = null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onTick(ServerTickEvent.Post event) {
        if (out == null || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        TimerSlots t = SimulationTimers.global();
        StringBuilder line = new StringBuilder(Integer.toString(++elapsed));
        for (PerfSlot slot : PerfSlot.values()) {
            line.append(',').append(String.format(Locale.ROOT, "%.3f", PerfReport.ms(t.averageNanos(slot.ordinal(), 20))));
        }
        line.append(',').append(VisitorTracker.total()).append('\n');
        try {
            out.write(line.toString());
            out.flush();
        } catch (IOException e) {
            ZooCraft.LOGGER.warn("Couldn't write the ZooCraft perf log", e);
            stop();
            return;
        }
        if (--secondsLeft <= 0) {
            stop();
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        stop();
    }
}
```

If `ZooCraft.LOGGER` doesn't exist, add `public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();` to `ZooCraft.java` (check first with `grep -n LOGGER src/main/java/com/zoocraft/ZooCraft.java`).

- [ ] **Step 5: Commands**

`src/main/java/com/zoocraft/command/PerfCommands.java`:

```java
package com.zoocraft.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zoocraft.perf.PerfLog;
import com.zoocraft.perf.PerfReport;
import com.zoocraft.zoo.Zoo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;

/** {@code /zoo debug perf}, {@code /zoo debug perf <zoo>} and {@code /zoo debug perf log <seconds>}. */
final class PerfCommands {
    private PerfCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("perf")
                .executes(PerfCommands::server)
                .then(Commands.literal("log").then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                        .executes(PerfCommands::log)))
                .then(Commands.argument("zoo", StringArgumentType.string()).suggests(ZooCommands.ZOO_NAMES)
                        .executes(c -> zoo(c, ZooCommands.namedZoo(c))));
    }

    private static int server(CommandContext<CommandSourceStack> c) {
        PerfReport.server(c.getSource().getServer()).forEach(line -> c.getSource().sendSuccess(() -> line, false));
        return 1;
    }

    private static int zoo(CommandContext<CommandSourceStack> c, Zoo zoo) {
        server(c);
        PerfReport.zoo(c.getSource().getServer(), zoo).forEach(line -> c.getSource().sendSuccess(() -> line, false));
        return 1;
    }

    private static int log(CommandContext<CommandSourceStack> c) {
        int seconds = IntegerArgumentType.getInteger(c, "seconds");
        try {
            Path file = PerfLog.start(c.getSource().getServer(), seconds);
            c.getSource().sendSuccess(() -> Component.literal("Logging ZooCraft performance for " + seconds + " s to " + file), false);
            return 1;
        } catch (IOException e) {
            c.getSource().sendFailure(Component.literal("Couldn't start the perf log: " + e.getMessage()));
            return 0;
        }
    }
}
```

In `ZooDebugCommands.build()`, add `.then(PerfCommands.build())` to the chain, after `.then(ShopCommands.debugSell())`. The `log` literal sits before the `zoo` argument, so a zoo named "log" can't be reached through `/zoo debug perf`, only through the other commands. That's acceptable for an operator tool.

- [ ] **Step 6: Build and run the GameTests**

Run: `./gradlew build` then `tools/gametest.sh`
Expected: all pass, including `perfreportdescribesthezoo`, `perflogwritesalinepersecondandrestartscleanly` and `stuckandcrowdingcountscomefromvisitors`.

- [ ] **Step 7: Check by hand on the dev server**

Start `./gradlew runServer`, then run `python tools/rcon.py "zoo debug perf" "zoo debug perf log 3"`.
Expected: a "ZooCraft performance" header, a server tick line, and "Logging ZooCraft performance for 3 s". Three seconds later, `run-server/logs/zoocraft-perf.csv` has a header and three lines. Stop the server.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/zoocraft/perf src/main/java/com/zoocraft/command/PerfCommands.java src/main/java/com/zoocraft/command/ZooDebugCommands.java src/main/java/com/zoocraft/gametest/PerfGameTests.java src/main/java/com/zoocraft/ZooCraft.java
git commit -m "M9: /zoo debug perf with stuck visitors, crowding and exhibit coverage, and a per-second CSV log"
```

---

### Task 4: Recognition verdicts with reasons and `/zoo debug recognise`

**Files:**
- Create: `src/main/java/com/zoocraft/core/animal/RecognitionVerdict.java`
- Modify: `src/main/java/com/zoocraft/core/animal/RecognitionRules.java` (add `decide`)
- Modify: `src/main/java/com/zoocraft/animal/AnimalRecognition.java` (extract `facts`)
- Create: `src/main/java/com/zoocraft/animal/RecognitionReport.java`
- Create: `src/main/java/com/zoocraft/command/RecogniseCommand.java`
- Modify: `src/main/java/com/zoocraft/command/ZooDebugCommands.java` (register)
- Test: `src/test/java/com/zoocraft/core/animal/RecognitionRulesTest.java` (add tests)
- Test: `src/main/java/com/zoocraft/gametest/PerfGameTests.java` (add tests)

**Interfaces:**
- Consumes: existing `RecognitionFacts`, `RecognitionRules.isExhibitAnimal`, `AnimalRecognition.EXHIBIT_ANIMALS` and `NOT_EXHIBIT_ANIMALS`.
- Produces:

```java
public record RecognitionVerdict(boolean recognised, Reason reason) {
    public enum Reason {
        EXCLUDED_BY_TAG("tag zoocraft:not_exhibit_animals"), BOSS("boss tag"),
        INCLUDED_BY_TAG("tag zoocraft:exhibit_animals"), MONSTER_CATEGORY("monster category"),
        ANIMAL_CLASS("extends Animal"), WATER_ANIMAL_CLASS("extends WaterAnimal"),
        ANIMAL_CATEGORY("animal spawn category"), NO_RULE("no rule matched");
        public String label();
    }
}
public static RecognitionVerdict RecognitionRules.decide(RecognitionFacts facts);
public static RecognitionFacts AnimalRecognition.facts(Entity entity);
public static RecognitionFacts AnimalRecognition.factsForType(EntityType<?> type);
public final class RecognitionReport {
    public record Entry(ResourceLocation id, boolean recognised, String reason, boolean checked) {}
    public static List<Entry> run(ServerLevel level, @Nullable String namespace);
    public static List<Entry> run(ServerLevel level, @Nullable String namespace, Function<EntityType<?>, Entity> factory);
    public static Entry check(ResourceLocation id, EntityType<?> type, ServerLevel level, Function<EntityType<?>, Entity> factory);
    public static Path writeCsv(MinecraftServer server, List<Entry> entries) throws IOException;
}
```

- [ ] **Step 1: Write the failing JUnit tests**

Add to `RecognitionRulesTest.java` (keep the existing tests; add `import static org.junit.jupiter.api.Assertions.assertEquals;` if missing):

```java
    private static RecognitionFacts facts(boolean include, boolean exclude, boolean boss, boolean animal, boolean water, String category) {
        return new RecognitionFacts(include, exclude, boss, animal, water, category);
    }

    @Test
    void decideNamesTheRuleThatDecided() {
        assertEquals(new RecognitionVerdict(false, RecognitionVerdict.Reason.EXCLUDED_BY_TAG),
                RecognitionRules.decide(facts(true, true, false, true, false, "creature")));
        assertEquals(new RecognitionVerdict(false, RecognitionVerdict.Reason.BOSS),
                RecognitionRules.decide(facts(true, false, true, true, false, "creature")));
        assertEquals(new RecognitionVerdict(true, RecognitionVerdict.Reason.INCLUDED_BY_TAG),
                RecognitionRules.decide(facts(true, false, false, false, false, "monster")));
        assertEquals(new RecognitionVerdict(false, RecognitionVerdict.Reason.MONSTER_CATEGORY),
                RecognitionRules.decide(facts(false, false, false, true, false, "monster")));
        assertEquals(new RecognitionVerdict(true, RecognitionVerdict.Reason.ANIMAL_CLASS),
                RecognitionRules.decide(facts(false, false, false, true, false, "misc")));
        assertEquals(new RecognitionVerdict(true, RecognitionVerdict.Reason.WATER_ANIMAL_CLASS),
                RecognitionRules.decide(facts(false, false, false, false, true, "misc")));
        assertEquals(new RecognitionVerdict(true, RecognitionVerdict.Reason.ANIMAL_CATEGORY),
                RecognitionRules.decide(facts(false, false, false, false, false, "creature")));
        assertEquals(new RecognitionVerdict(false, RecognitionVerdict.Reason.NO_RULE),
                RecognitionRules.decide(facts(false, false, false, false, false, "misc")));
    }

    @Test
    void decideAgreesWithIsExhibitAnimalForEveryCombination() {
        String[] categories = {"creature", "monster", "misc", "water_creature", "ambient"};
        for (int bits = 0; bits < 32; bits++) {
            for (String category : categories) {
                RecognitionFacts f = facts((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0, (bits & 16) != 0, category);
                assertEquals(RecognitionRules.isExhibitAnimal(f), RecognitionRules.decide(f).recognised(), f.toString());
            }
        }
    }
```

- [ ] **Step 2: Run them and check they fail**

Run: `./gradlew test --tests "com.zoocraft.core.animal.*"`
Expected: compilation failure, `RecognitionVerdict` not found.

- [ ] **Step 3: Implement the verdict**

`src/main/java/com/zoocraft/core/animal/RecognitionVerdict.java`:

```java
package com.zoocraft.core.animal;

/** Whether an entity counts as an exhibit animal, and which rule decided (Phase 3 spec section 6.3). */
public record RecognitionVerdict(boolean recognised, Reason reason) {
    public enum Reason {
        EXCLUDED_BY_TAG("tag zoocraft:not_exhibit_animals"),
        BOSS("boss tag"),
        INCLUDED_BY_TAG("tag zoocraft:exhibit_animals"),
        MONSTER_CATEGORY("monster category"),
        ANIMAL_CLASS("extends Animal"),
        WATER_ANIMAL_CLASS("extends WaterAnimal"),
        ANIMAL_CATEGORY("animal spawn category"),
        NO_RULE("no rule matched");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
```

Replace the body of `RecognitionRules` with:

```java
    public static boolean isExhibitAnimal(RecognitionFacts facts) {
        return decide(facts).recognised();
    }

    /** The same rules as {@link #isExhibitAnimal}, in the same order, naming the one that decided. */
    public static RecognitionVerdict decide(RecognitionFacts facts) {
        if (facts.excludeTagged()) {
            return new RecognitionVerdict(false, RecognitionVerdict.Reason.EXCLUDED_BY_TAG);
        }
        if (facts.boss()) {
            return new RecognitionVerdict(false, RecognitionVerdict.Reason.BOSS);
        }
        if (facts.includeTagged()) {
            return new RecognitionVerdict(true, RecognitionVerdict.Reason.INCLUDED_BY_TAG);
        }
        if ("monster".equals(facts.spawnCategory())) {
            return new RecognitionVerdict(false, RecognitionVerdict.Reason.MONSTER_CATEGORY);
        }
        if (facts.animalClass()) {
            return new RecognitionVerdict(true, RecognitionVerdict.Reason.ANIMAL_CLASS);
        }
        if (facts.waterAnimalClass()) {
            return new RecognitionVerdict(true, RecognitionVerdict.Reason.WATER_ANIMAL_CLASS);
        }
        if (ANIMAL_CATEGORIES.contains(facts.spawnCategory())) {
            return new RecognitionVerdict(true, RecognitionVerdict.Reason.ANIMAL_CATEGORY);
        }
        return new RecognitionVerdict(false, RecognitionVerdict.Reason.NO_RULE);
    }
```

Keep the `ANIMAL_CATEGORIES` constant, the class Javadoc and the private constructor.

- [ ] **Step 4: Run the JUnit tests**

Run: `./gradlew test --tests "com.zoocraft.core.animal.*"`
Expected: PASS, including the existing recognition tests.

- [ ] **Step 5: Extract the facts in `AnimalRecognition`**

Replace `isExhibitAnimal` in `AnimalRecognition.java` with:

```java
    public static boolean isExhibitAnimal(Entity entity) {
        if (!(entity instanceof Mob) || entity instanceof Visitor || !entity.isAlive()) {
            return false;
        }
        return RecognitionRules.isExhibitAnimal(facts(entity));
    }

    /** What the rules need to know about a constructed entity. */
    public static RecognitionFacts facts(Entity entity) {
        EntityType<?> type = entity.getType();
        return new RecognitionFacts(
                type.is(EXHIBIT_ANIMALS),
                type.is(NOT_EXHIBIT_ANIMALS),
                type.is(Tags.EntityTypes.BOSSES),
                entity instanceof Animal,
                entity instanceof WaterAnimal,
                type.getCategory().getName());
    }

    /** What can be known without constructing the entity: tags and spawn category only. */
    public static RecognitionFacts factsForType(EntityType<?> type) {
        return new RecognitionFacts(
                type.is(EXHIBIT_ANIMALS),
                type.is(NOT_EXHIBIT_ANIMALS),
                type.is(Tags.EntityTypes.BOSSES),
                false,
                false,
                type.getCategory().getName());
    }
```

- [ ] **Step 6: Write the failing GameTests**

Add to `PerfGameTests`:

```java
    @GameTest(template = GameTestTemplates.FLOOR)
    public static void recognitionReportNamesTheRule(GameTestHelper helper) {
        List<RecognitionReport.Entry> entries = RecognitionReport.run(helper.getLevel(), "minecraft");
        RecognitionReport.Entry cow = find(helper, entries, "minecraft:cow");
        helper.assertTrue(cow.recognised() && cow.checked() && cow.reason().equals("extends Animal"), "cow: " + cow);
        RecognitionReport.Entry zombie = find(helper, entries, "minecraft:zombie");
        helper.assertTrue(!zombie.recognised() && zombie.checked(), "zombie: " + zombie);
        RecognitionReport.Entry villager = find(helper, entries, "minecraft:villager");
        helper.assertTrue(!villager.recognised() && villager.checked(), "villager: " + villager);
        helper.assertTrue(entries.stream().allMatch(e -> e.id().getNamespace().equals("minecraft")), "other namespaces included");
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void recognitionReportSurvivesBrokenEntities(GameTestHelper helper) {
        List<RecognitionReport.Entry> entries = RecognitionReport.run(helper.getLevel(), "minecraft", type -> {
            if (type == EntityType.COW) {
                throw new IllegalStateException("broken mod entity");
            }
            if (type == EntityType.PIG) {
                return null;
            }
            return type.create(helper.getLevel());
        });
        RecognitionReport.Entry cow = find(helper, entries, "minecraft:cow");
        helper.assertTrue(!cow.checked() && cow.recognised() && cow.reason().equals("animal spawn category"),
                "a cow that can't be built still gets its category verdict: " + cow);
        RecognitionReport.Entry pig = find(helper, entries, "minecraft:pig");
        helper.assertTrue(!pig.checked(), "a pig that came back null is unchecked: " + pig);
        RecognitionReport.Entry sheep = find(helper, entries, "minecraft:sheep");
        helper.assertTrue(sheep.checked() && sheep.recognised(), "the report carried on after the failures: " + sheep);
        helper.succeed();
    }

    private static RecognitionReport.Entry find(GameTestHelper helper, List<RecognitionReport.Entry> entries, String id) {
        return entries.stream().filter(e -> e.id().toString().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException(id + " missing from the report"));
    }
```

Add the imports `com.zoocraft.animal.RecognitionReport` and `net.minecraft.world.entity.EntityType`.

- [ ] **Step 7: Implement `RecognitionReport`**

`src/main/java/com/zoocraft/animal/RecognitionReport.java`:

```java
package com.zoocraft.animal;

import com.zoocraft.ZooCraft;
import com.zoocraft.core.animal.RecognitionRules;
import com.zoocraft.core.animal.RecognitionVerdict;
import com.zoocraft.visitor.Visitor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * {@code /zoo debug recognise}: the recognition verdict for every entity type (Phase 3 spec section 6.3). Entities are
 * constructed but never added to the world.
 */
public final class RecognitionReport {
    public record Entry(ResourceLocation id, boolean recognised, String reason, boolean checked) {
    }

    private RecognitionReport() {
    }

    public static List<Entry> run(ServerLevel level, @Nullable String namespace) {
        return run(level, namespace, type -> type.create(level));
    }

    public static List<Entry> run(ServerLevel level, @Nullable String namespace, Function<EntityType<?>, Entity> factory) {
        List<Entry> entries = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (namespace == null || id.getNamespace().equals(namespace)) {
                entries.add(check(id, type, level, factory));
            }
        }
        entries.sort(Comparator.comparing(e -> e.id().toString()));
        return entries;
    }

    public static Entry check(ResourceLocation id, EntityType<?> type, ServerLevel level, Function<EntityType<?>, Entity> factory) {
        Entity entity;
        try {
            entity = factory.apply(type);
        } catch (Exception | LinkageError e) {
            ZooCraft.LOGGER.debug("Couldn't construct {} for the recognition report", id, e);
            entity = null;
        }
        if (entity == null) {
            RecognitionVerdict verdict = RecognitionRules.decide(AnimalRecognition.factsForType(type));
            return new Entry(id, verdict.recognised(), verdict.reason().label(), false);
        }
        try {
            if (entity instanceof Visitor) {
                return new Entry(id, false, "ZooCraft visitor", true);
            }
            if (!(entity instanceof Mob)) {
                return new Entry(id, false, "not a mob", true);
            }
            RecognitionVerdict verdict = RecognitionRules.decide(AnimalRecognition.facts(entity));
            return new Entry(id, verdict.recognised(), verdict.reason().label(), true);
        } finally {
            entity.discard();
        }
    }

    public static Path writeCsv(MinecraftServer server, List<Entry> entries) throws IOException {
        Path file = server.getServerDirectory().resolve("logs").resolve("zoocraft-recognition.csv");
        Files.createDirectories(file.getParent());
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write("entity,recognised,reason,checked\n");
            for (Entry e : entries) {
                out.write(e.id() + "," + e.recognised() + "," + e.reason() + "," + e.checked() + "\n");
            }
        }
        return file;
    }
}
```

- [ ] **Step 8: The command**

`src/main/java/com/zoocraft/command/RecogniseCommand.java`:

```java
package com.zoocraft.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.zoocraft.animal.RecognitionReport;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** {@code /zoo debug recognise [namespace]} (Phase 3 spec section 6.3). */
final class RecogniseCommand {
    private static final int LISTED = 40;
    private static final SuggestionProvider<CommandSourceStack> NAMESPACES = (c, b) -> SharedSuggestionProvider.suggest(
            BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(id -> id.getNamespace()).distinct(), b);

    private RecogniseCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("recognise")
                .executes(c -> run(c, null))
                .then(Commands.argument("namespace", StringArgumentType.word()).suggests(NAMESPACES)
                        .executes(c -> run(c, StringArgumentType.getString(c, "namespace"))));
    }

    private static int run(CommandContext<CommandSourceStack> c, @Nullable String namespace) {
        CommandSourceStack source = c.getSource();
        List<RecognitionReport.Entry> entries = RecognitionReport.run(source.getLevel(), namespace);
        long recognised = entries.stream().filter(RecognitionReport.Entry::recognised).count();
        long unchecked = entries.stream().filter(e -> !e.checked()).count();
        String where;
        try {
            Path file = RecognitionReport.writeCsv(source.getServer(), entries);
            where = "Full list: " + file;
        } catch (IOException e) {
            where = "Couldn't write the full list: " + e.getMessage();
        }
        String scope = namespace == null ? "all mods" : namespace;
        String summary = String.format("%d entity types checked in %s: %d recognised, %d not, %d could not be checked. %s",
                entries.size(), scope, recognised, entries.size() - recognised, unchecked, where);
        source.sendSuccess(() -> Component.literal(summary), false);
        if (namespace != null) {
            entries.stream().limit(LISTED).forEach(e -> source.sendSuccess(() -> Component.literal(" - " + e.id() + ": "
                    + (e.recognised() ? "recognised" : "not recognised") + " (" + e.reason() + (e.checked() ? "" : ", not constructed") + ")"), false));
            if (entries.size() > LISTED) {
                source.sendSuccess(() -> Component.literal(" ... and " + (entries.size() - LISTED) + " more in the file."), false);
            }
        }
        return (int) recognised;
    }
}
```

In `ZooDebugCommands.build()`, add `.then(RecogniseCommand.build())` after `.then(PerfCommands.build())`.

- [ ] **Step 9: Build and run all tests**

Run: `./gradlew build` then `tools/gametest.sh`
Expected: all pass, including `recognitionreportnamestherule` and `recognitionreportsurvivesbrokenentities`.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/zoocraft/core/animal src/test/java/com/zoocraft/core/animal src/main/java/com/zoocraft/animal src/main/java/com/zoocraft/command src/main/java/com/zoocraft/gametest/PerfGameTests.java
git commit -m "M9: recognition verdicts name their rule; /zoo debug recognise reports every entity type"
```

---

### Task 5: Simulation settings

**Files:**
- Create: `src/main/java/com/zoocraft/core/visitor/StepRates.java`
- Test: `src/test/java/com/zoocraft/core/visitor/StepRatesTest.java`
- Modify: `src/main/java/com/zoocraft/config/ZooConfig.java` (new `simulation` section)
- Modify: `src/main/java/com/zoocraft/visitor/VisitorController.java` (configurable step)
- Modify: `src/main/java/com/zoocraft/visitor/VisitorErrands.java` (rates from `StepRates`)
- Modify: `src/main/java/com/zoocraft/visitor/VisitorExploration.java` (interval checks)
- Modify: `src/main/java/com/zoocraft/visitor/Visitor.java` (two runtime timestamps)
- Test: `src/main/java/com/zoocraft/gametest/PerfGameTests.java` (step change mid-visit)

**Interfaces:**
- Consumes: nothing new.
- Produces:

```java
public final class StepRates {                     // package com.zoocraft.core.visitor
    public static final double INTEREST_FULL_DECAY_SECONDS = 360;
    public static double interestDecay(int stepTicks);   // interest lost per step
    public static double urgentLoss(int stepTicks);      // satisfaction lost per step while a need is urgent
    public static double seconds(int stepTicks);
    public static boolean due(long now, long last, int intervalTicks);
}
// ZooConfig
public static final ModConfigSpec.IntValue VISITOR_STEP_TICKS, VIEWING_INTERVAL_TICKS, LITTER_CHECK_INTERVAL_TICKS;
// VisitorController
static int step();                                // current configured step
```

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/zoocraft/core/visitor/StepRatesTest.java`:

```java
package com.zoocraft.core.visitor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepRatesTest {
    @Test
    void defaultStepKeepsThePhaseTwoRates() {
        // Before M9: INTEREST_DECAY_PER_STEP = 100 / (6 * 60 * 20 / 10), URGENT_LOSS_PER_STEP = 1 / (10 * 20 / 10).
        assertEquals(100.0 / (6 * 60 * 20 / 10), StepRates.interestDecay(10), 1e-12);
        assertEquals(1.0 / (10 * 20 / 10), StepRates.urgentLoss(10), 1e-12);
        assertEquals(0.5, StepRates.seconds(10), 1e-12);
    }

    @Test
    void ratesPerSecondDoNotDependOnTheStep() {
        for (int step : new int[]{1, 3, 7, 10, 20, 40}) {
            double stepsPerSecond = 20.0 / step;
            assertEquals(100.0 / 360, StepRates.interestDecay(step) * stepsPerSecond, 1e-12, "interest, step " + step);
            assertEquals(0.1, StepRates.urgentLoss(step) * stepsPerSecond, 1e-12, "urgent loss, step " + step);
            assertEquals(1.0, StepRates.seconds(step) * stepsPerSecond, 1e-12, "seconds, step " + step);
        }
    }

    @Test
    void dueWhenTheIntervalHasPassed() {
        assertTrue(StepRates.due(100, 80, 20));
        assertFalse(StepRates.due(99, 80, 20));
        assertTrue(StepRates.due(0, Long.MIN_VALUE / 2, 20));
    }
}
```

- [ ] **Step 2: Run them and check they fail**

Run: `./gradlew test --tests "com.zoocraft.core.visitor.*"`
Expected: compilation failure, `StepRates` not found.

- [ ] **Step 3: Implement `StepRates`**

`src/main/java/com/zoocraft/core/visitor/StepRates.java`:

```java
package com.zoocraft.core.visitor;

/** Per-step amounts for a configurable visitor step, so rates per second never change (Phase 3 spec section 6.5). */
public final class StepRates {
    /** Interest falls from 100 to 0 in this many seconds when nothing interesting happens. */
    public static final double INTEREST_FULL_DECAY_SECONDS = 360;
    /** An urgent need costs 1 satisfaction every 10 seconds. */
    private static final double URGENT_LOSS_PER_SECOND = 0.1;

    private StepRates() {
    }

    public static double interestDecay(int stepTicks) {
        return 100.0 / INTEREST_FULL_DECAY_SECONDS * seconds(stepTicks);
    }

    public static double urgentLoss(int stepTicks) {
        return URGENT_LOSS_PER_SECOND * seconds(stepTicks);
    }

    public static double seconds(int stepTicks) {
        return stepTicks / 20.0;
    }

    public static boolean due(long now, long last, int intervalTicks) {
        return now - last >= intervalTicks;
    }
}
```

Run: `./gradlew test --tests "com.zoocraft.core.visitor.*"`
Expected: PASS.

- [ ] **Step 4: Config section**

In `ZooConfig.java`, declare:

```java
    public static final ModConfigSpec.IntValue VISITOR_STEP_TICKS;
    public static final ModConfigSpec.IntValue VIEWING_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue LITTER_CHECK_INTERVAL_TICKS;
```

and before `SPEC = b.build();` add:

```java
        b.push("simulation");
        VISITOR_STEP_TICKS = b.comment("How often, in ticks, each visitor makes its decisions. Higher is cheaper but less responsive.")
                .defineInRange("visitorStepTicks", 10, 1, 40);
        VIEWING_INTERVAL_TICKS = b.comment("How often, in ticks, each visitor looks for nearby animals.")
                .defineInRange("viewingIntervalTicks", 20, 5, 200);
        LITTER_CHECK_INTERVAL_TICKS = b.comment("How often, in ticks, each visitor notices litter around it.")
                .defineInRange("litterCheckIntervalTicks", 40, 10, 400);
        b.pop();
```

- [ ] **Step 5: Use the configured step**

In `VisitorController.java`:

- Delete `static final int STEP = 10;` and `INTEREST_DECAY_PER_STEP`. Change the class Javadoc's `{@link #STEP}` to "every {@code simulation.visitorStepTicks} ticks".
- Add:

```java
    /** The configured visitor step, in ticks. Read every tick, so a config change applies straight away. */
    static int step() {
        return ZooConfig.VISITOR_STEP_TICKS.get();
    }
```

- Delete the `stepOffset` field and its `stepOffset < 0` block in `tick()`, and replace the step check with:

```java
        int step = step();
        if ((now + Math.floorMod(visitor.getId(), step)) % step != 0 || visitor.zooId == null) {
```

- In `stepExploring`, replace `visitor.changeInterest(-INTEREST_DECAY_PER_STEP);` with `visitor.changeInterest(-StepRates.interestDecay(step()));`.
- Import `com.zoocraft.core.visitor.StepRates`.

In `VisitorErrands.java`:

- Delete `URGENT_LOSS_PER_STEP`.
- In `tickNeeds()`, start with `int step = VisitorController.step();`, replace `needs.tick(VisitorController.STEP / 20.0);` with `needs.tick(StepRates.seconds(step));`, and replace both `-URGENT_LOSS_PER_STEP` with `-StepRates.urgentLoss(step)`.
- Import `com.zoocraft.core.visitor.StepRates`.

In `Visitor.java`, add these runtime fields next to `lastShownComment`:

```java
    /** Game time of the last look for animals and for litter (runtime only). */
    long lastViewingCheck = Long.MIN_VALUE / 2;
    long lastLitterCheck = Long.MIN_VALUE / 2;
```

In `VisitorExploration.java`:

- In `observe`, replace `(now / VisitorController.STEP) % 2 != 0` with `!StepRates.due(now, visitor.lastViewingCheck, ZooConfig.VIEWING_INTERVAL_TICKS.get())`, and set `visitor.lastViewingCheck = now;` straight after the early-return check.
- In `noticeLitter`, replace `(now / VisitorController.STEP) % 4 != 0` with `!StepRates.due(now, visitor.lastLitterCheck, ZooConfig.LITTER_CHECK_INTERVAL_TICKS.get())`, and set `visitor.lastLitterCheck = now;` straight after the early return, before the timer from Task 2 starts.
- Import `com.zoocraft.core.visitor.StepRates`.

Run `grep -rn "STEP\b\|URGENT_LOSS_PER_STEP\|INTEREST_DECAY_PER_STEP" src/main/java`. It should print nothing.

- [ ] **Step 6: Write the GameTest for a step change mid-visit**

Add to `PerfGameTests`:

```java
    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 400)
    public static void changingTheStepKeepsNeedsRisingAtTheSameRate(GameTestHelper helper) {
        Setup setup = openZoo(helper, "Stepped");
        Visitor visitor = exploringVisitor(helper, setup, 30, 24);
        visitor.testSetNeeds(0, 0);
        int original = ZooConfig.VISITOR_STEP_TICKS.get();
        helper.runAfterDelay(100, () -> {
            double before = visitor.needs().hunger();
            ZooConfig.VISITOR_STEP_TICKS.set(3);
            helper.runAfterDelay(100, () -> {
                double after = visitor.needs().hunger();
                ZooConfig.VISITOR_STEP_TICKS.set(original);
                // 5 seconds at either step: the rise must be about the same (needs rise 0.14 to 0.25 per second).
                double first = before;
                double second = after - before;
                helper.assertTrue(first > 0.4 && second > 0.4, "needs didn't rise: " + first + ", " + second);
                helper.assertTrue(Math.abs(first - second) < 0.35, "rate changed with the step: " + first + " then " + second);
                visitor.discard();
                TestZoos.cleanUp(helper, setup.owner(), setup.zoo());
                helper.succeed();
            });
        });
    }
```

Add the import `com.zoocraft.config.ZooConfig`. `ModConfigSpec.IntValue.set` works on a loaded server config. If GameTests run before the server config loads, it throws `IllegalStateException`. In that case, have the test call `helper.succeed()` with a log line, and rely on the JUnit equivalence test.

- [ ] **Step 7: Build and run everything**

Run: `./gradlew build` then `tools/gametest.sh`
Expected: all JUnit and GameTests pass, including the existing needs, litter and visitor tests, which check the default-step behaviour.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/zoocraft/core/visitor/StepRates.java src/test/java/com/zoocraft/core/visitor src/main/java/com/zoocraft/config/ZooConfig.java src/main/java/com/zoocraft/visitor src/main/java/com/zoocraft/gametest/PerfGameTests.java
git commit -m "M9: simulation settings for the visitor step, viewing and litter intervals, with rates kept per second"
```

---

### Task 6: Harness, baseline rerun and documentation

**Files:**
- Create: `tools/perf/rcon_client.py`
- Create: `tools/perf/perf_world.py`
- Create: `tools/perf/build_park.py`
- Create: `tools/perf/ramp.py`
- Create: `tools/perf/jfr_summary.py`
- Create: `tools/perf/README.md`
- Create: `docs/performance.md`
- Create: `docs/playtests/m9-diagnostics.md`
- Modify: `CLAUDE.md` (build-and-test bullets)

**Interfaces:**
- Consumes: `/zoo debug perf log <seconds>` and its CSV columns (Task 3); `/zoo debug create`, `gate`, `shop`, `link`, `toilet` and `open` (existing).
- Produces: the scripts and docs listed above.

- [ ] **Step 1: RCON client that copes with long replies**

`tools/perf/rcon_client.py`:

```python
"""RCON client for the perf harness. Vanilla RCON reads one packet per socket read, so commands go one at a time and
replies that span several packets are read until the socket goes quiet."""
import socket
import struct

HOST, PORT, PASSWORD = "127.0.0.1", 25575, "zoocraft-dev"


class Rcon:
    def __init__(self, host=HOST, port=PORT, password=PASSWORD):
        self.sock = socket.create_connection((host, port), timeout=60)
        self.next_id = 1
        self._send(3, password)
        if self._read_packet()[0] == -1:
            raise RuntimeError("RCON login failed")

    def _send(self, kind, body):
        self.next_id += 1
        data = struct.pack("<ii", self.next_id, kind) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(data)) + data)

    def _exact(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise EOFError("RCON connection closed")
            buf += chunk
        return buf

    def _read_packet(self):
        length = struct.unpack("<i", self._exact(4))[0]
        data = self._exact(length)
        request_id = struct.unpack("<i", data[:4])[0]
        return request_id, data[8:-2].decode("utf-8", "replace")

    def command(self, body):
        """Runs one command and returns its full reply."""
        self._send(2, body)
        request_id, text = self._read_packet()
        parts = [text]
        if len(text.encode("utf-8")) >= 4000:
            # Possibly split: read further packets until none arrive for half a second.
            self.sock.settimeout(0.5)
            try:
                while True:
                    parts.append(self._read_packet()[1])
            except socket.timeout:
                pass
            finally:
                self.sock.settimeout(60)
        return "".join(parts)
```

- [ ] **Step 2: Prepare and restore the perf world**

`tools/perf/perf_world.py`:

```python
"""Switches the dev server to a separate perf world with raised limits, and back.

Usage: python tools/perf/perf_world.py prepare|restore
prepare backs up run-server/server.properties and run-server/config/zoocraft-server.toml into run-server/perf-backup/,
sets level-name=perf-world, and sets maxVisitorsPerZoo=0, maxVisitorsTotal=1000, spawnIntervalSeconds=1.
restore puts the backups back.
"""
import pathlib
import re
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2] / "run-server"
FILES = [ROOT / "server.properties", ROOT / "config" / "zoocraft-server.toml"]
BACKUP = ROOT / "perf-backup"


def prepare():
    if BACKUP.exists():
        sys.exit("run-server/perf-backup already exists: run 'restore' first")
    BACKUP.mkdir()
    for f in FILES:
        shutil.copy2(f, BACKUP / f.name)
    props = FILES[0].read_text()
    FILES[0].write_text(re.sub(r"(?m)^level-name=.*$", "level-name=perf-world", props))
    cfg = FILES[1].read_text()
    for key, value in [("maxVisitorsPerZoo", 0), ("maxVisitorsTotal", 1000), ("spawnIntervalSeconds", 1)]:
        cfg = re.sub(rf"{key} = \d+", f"{key} = {value}", cfg)
    FILES[1].write_text(cfg)
    print("Perf world prepared. Start runServer, then runClient -PjoinServer.")


def restore():
    if not BACKUP.exists():
        sys.exit("Nothing to restore")
    for f in FILES:
        shutil.copy2(BACKUP / f.name, f)
    shutil.rmtree(BACKUP)
    print("Dev server settings restored.")


if __name__ == "__main__":
    {"prepare": prepare, "restore": restore}[sys.argv[1]]()
```

`run-server/perf-backup/` needs no `.gitignore` entry: all of `run-server/` is already ignored (`.gitignore` line 12).

- [ ] **Step 3: The park builder**

`tools/perf/build_park.py`:

```python
"""Builds a test park on the dev server (superflat world, feet at y=-60), Phase 3 spec section 3.1.

Usage: python tools/perf/build_park.py "<name>" <x0> <z0> <cells> [animals_per_pen]
A park is cells x cells cells of 16 blocks: 2-wide gravel paths on the cell edges, an 8x8 fenced pen in each cell,
a gate in the middle of each side, a shop every 4th cell, a toilet every 5th and a bin every 3rd.
The reference "Big Park" is: "Big Park" 0 0 15 2. The small parks are 6 cells.
Needs a player called Dev online and opped (it runs the zoo commands as Dev), and the area force-loaded.
"""
import sys

from rcon_client import Rcon

Y, FLOOR, CELL = -60, -61, 16
SPECIES = ["cow", "sheep", "pig", "chicken", "horse", "donkey", "llama", "rabbit", "fox", "panda",
           "polar_bear", "mooshroom", "camel", "armadillo", "turtle"]


def main():
    name, x0, z0, cells = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
    per_pen = int(sys.argv[5]) if len(sys.argv) > 5 else 2
    size = cells * CELL
    x1, z1 = x0 + size - 1, z0 + size - 1
    r = Rcon()
    replies = []

    def run(cmd):
        replies.append((cmd, r.command(cmd)))

    for i in range(cells + 1):
        a = min(x0 + i * CELL, x1 - 1)
        run(f"fill {a} {FLOOR} {z0} {a + 1} {FLOOR} {z1} gravel")
        b = min(z0 + i * CELL, z1 - 1)
        run(f"fill {x0} {FLOOR} {b} {x1} {FLOOR} {b + 1} gravel")

    for cx in range(cells):
        for cz in range(cells):
            bx, bz = x0 + cx * CELL, z0 + cz * CELL
            fx0, fz0, fx1, fz1 = bx + 4, bz + 4, bx + 11, bz + 11
            run(f"fill {fx0} {Y} {fz0} {fx1} {Y} {fz0} oak_fence")
            run(f"fill {fx0} {Y} {fz1} {fx1} {Y} {fz1} oak_fence")
            run(f"fill {fx0} {Y} {fz0} {fx0} {Y} {fz1} oak_fence")
            run(f"fill {fx1} {Y} {fz0} {fx1} {Y} {fz1} oak_fence")
            species = SPECIES[(cx * cells + cz) % len(SPECIES)]
            for k in range(per_pen):
                run(f"summon {species} {bx + 6 + 2 * k + 0.5} {Y} {bz + 7.5} {{PersistenceRequired:1b}}")

    run(f'execute as Dev run zoo debug create "{name}" {x0} {Y} {z0} {x1} {Y} {z1}')
    mid_x, mid_z = x0 + (cells // 2) * CELL + 8, z0 + (cells // 2) * CELL + 8
    for gx, gz in [(mid_x, z0), (mid_x, z1), (x0, mid_z), (x1, mid_z)]:
        run(f"execute as Dev run zoo debug gate {gx} {Y} {gz}")

    index = 0
    for cx in range(1, cells):
        for cz in range(cells):
            index += 1
            px, pz = x0 + cx * CELL, z0 + cz * CELL + 8
            if index % 4 == 0:
                run(f'setblock {px + 2} {Y} {pz} barrel{{Items:[{{Slot:0b,id:"minecraft:bread",count:64}},'
                    f'{{Slot:1b,id:"minecraft:bread",count:64}},{{Slot:2b,id:"minecraft:cookie",count:64}},'
                    f'{{Slot:3b,id:"minecraft:honey_bottle",count:16}},{{Slot:4b,id:"minecraft:poppy",count:64}}]}}')
                run(f"execute as Dev run zoo debug shop {px} {Y} {pz}")
                run(f"execute as Dev run zoo debug link {px + 2} {Y} {pz}")
            elif index % 5 == 0:
                run(f"execute as Dev run zoo debug toilet {px} {Y} {pz + 3}")
            if index % 3 == 0:
                run(f"setblock {px + 2} {Y} {pz + 4} zoocraft:rubbish_bin")

    run(f'execute as Dev run zoo open "{name}"')
    problems = [(c, o) for c, o in replies if "Unknown" in o or "Incorrect" in o or "rror" in o]
    print(f"{len(replies)} commands sent for {name}; {cells * cells} pens, {cells * cells * per_pen} animals")
    for c, o in replies:
        if " create " in c or " gate " in c or " open " in c:
            print(">", c, "\n ", o)
    for c, o in problems[:20]:
        print("PROBLEM:", c, "\n ", o)


if __name__ == "__main__":
    main()
```

- [ ] **Step 4: The ramp script**

`tools/perf/ramp.py`:

```python
"""Steps the per-zoo visitor limit and records one minute per step (Phase 3 spec section 6.4).

Usage: python tools/perf/ramp.py <tag> "<zoo1,zoo2,...>" <levels e.g. 0,12,25,50,100,150,200> [--jfr <server pid>]
For each level: set maxVisitorsPerZoo, wait until 90% of the visitors have arrived (at most 7 minutes), settle for
30 s, then log a minute with /zoo debug perf log 60 while sampling /tick query, and append a results line to
tools/perf/results/<tag>.md. With --jfr, a JFR recording of the same minute goes to tools/perf/results/<tag>-<level>.jfr.
"""
import csv
import pathlib
import re
import subprocess
import sys
import time

from rcon_client import Rcon

REPO = pathlib.Path(__file__).resolve().parents[2]
CONFIG = REPO / "run-server" / "config" / "zoocraft-server.toml"
PERF_CSV = REPO / "run-server" / "logs" / "zoocraft-perf.csv"
RESULTS = pathlib.Path(__file__).resolve().parent / "results"


def set_limit(n):
    text = CONFIG.read_text()
    CONFIG.write_text(re.sub(r"maxVisitorsPerZoo = \d+", f"maxVisitorsPerZoo = {n}", text))


def visitor_count(r):
    m = re.search(r"count: (\d+)", r.command("execute if entity @e[type=zoocraft:visitor]"))
    return int(m.group(1)) if m else 0


def tick_query(r):
    out = r.command("tick query")
    avg = float(re.search(r"Average time per tick: ([\d.]+)ms", out).group(1))
    p = re.search(r"P50: ([\d.]+)ms P95: ([\d.]+)ms P99: ([\d.]+)ms", out)
    return avg, float(p.group(2)), float(p.group(3))


def perf_averages():
    with PERF_CSV.open() as f:
        rows = list(csv.DictReader(f))
    if not rows:
        return {}
    return {k: sum(float(row[k]) for row in rows) / len(rows) for k in rows[0] if k != "seconds"}


def main():
    tag, zoos, levels = sys.argv[1], sys.argv[2].split(","), [int(x) for x in sys.argv[3].split(",")]
    pid = sys.argv[sys.argv.index("--jfr") + 1] if "--jfr" in sys.argv else None
    RESULTS.mkdir(exist_ok=True)
    out = RESULTS / f"{tag}.md"
    if not out.exists():
        out.write_text("| Limit per zoo | Visitors | Avg tick ms | Worst P95 | Worst P99 | ZooCraft ms | Path following ms |\n"
                       "|---|---|---|---|---|---|---|\n")
    r = Rcon()
    for n in levels:
        set_limit(n)
        target = int(n * len(zoos) * 0.9)
        start = time.time()
        while time.time() - start < 420 and visitor_count(r) < target:
            time.sleep(10)
        time.sleep(30)
        if pid:
            subprocess.run(["jcmd", pid, "JFR.start", f"name={tag}{n}", "settings=profile", "duration=60s",
                            f"filename={RESULTS / f'{tag}-{n}.jfr'}"], capture_output=True)
        r.command("zoo debug perf log 60")
        samples, counts = [], []
        for _ in range(12):
            time.sleep(5)
            samples.append(tick_query(r))
            counts.append(visitor_count(r))
        time.sleep(3)
        perf = perf_averages()
        line = (f"| {n} | {sum(counts) / len(counts):.0f} | {sum(s[0] for s in samples) / len(samples):.1f} "
                f"| {max(s[1] for s in samples):.1f} | {max(s[2] for s in samples):.1f} "
                f"| {perf.get('zoocraft_total_ms', 0):.2f} | {perf.get('visitor_navigation_ms', 0):.2f} |")
        print(line, flush=True)
        with out.open("a") as f:
            f.write(line + "\n")


if __name__ == "__main__":
    main()
```

Add `tools/perf/results/` to `.gitignore`, since results are machine-specific and the docs record the ones that matter.

- [ ] **Step 5: The JFR summariser**

`tools/perf/jfr_summary.py`:

```python
"""Inclusive share of server-thread CPU samples per bucket and per method in a JFR recording (Phase 3 spec 3.2).

Usage: python tools/perf/jfr_summary.py <file.jfr> [top_methods]
Needs the JDK's jfr tool on PATH.
"""
import collections
import json
import subprocess
import sys

BUCKETS = [
    ("visitor brain and controller", "com.zoocraft.visitor.Visitor.customServerAiStep"),
    ("SmartBrainLib navigation (visitors)", "net.tslat.smartbrainlib.api.core.navigation."),
    ("  path smoothing (attemptShortcut)", "net.tslat.smartbrainlib.api.core.navigation.ExtendedNavigator.attemptShortcut"),
    ("pathfinding, all mobs", "net.minecraft.world.level.pathfinder.PathFinder.findPath"),
    ("entity pushing", "net.minecraft.world.entity.LivingEntity.pushEntities"),
    ("entity move and collide", "net.minecraft.world.entity.Entity.move"),
    ("all living entity ticks", "net.minecraft.world.entity.LivingEntity.tick"),
    ("animal census", "com.zoocraft.animal.AnimalCensus.refresh"),
    ("facility census", "com.zoocraft.shop.FacilityCensus.refresh"),
    ("entity tracking (ChunkMap.tick)", "net.minecraft.server.level.ChunkMap.tick"),
    ("any ZooCraft frame", "com.zoocraft."),
]


def frames(event):
    for f in event["values"]["stackTrace"]["frames"]:
        m = f["method"]
        yield m["type"]["name"].replace("/", ".") + "." + m["name"]


def main():
    path = sys.argv[1]
    top = int(sys.argv[2]) if len(sys.argv) > 2 else 25
    raw = subprocess.run(["jfr", "print", "--json", "--stack-depth", "256", "--events", "jdk.ExecutionSample", path],
                         capture_output=True, text=True, encoding="utf-8").stdout
    events = json.loads(raw)["recording"]["events"]
    server = [e for e in events if (e["values"].get("sampledThread") or {}).get("javaName") == "Server thread"]
    n = max(len(server), 1)
    inclusive, buckets = collections.Counter(), collections.Counter()
    for e in server:
        names = list(frames(e))
        for name in set(names):
            inclusive[name] += 1
        for label, prefix in BUCKETS:
            if any(x.startswith(prefix) for x in names):
                buckets[label] += 1
    print(f"{path}: {len(server)} server-thread samples")
    for label, _ in BUCKETS:
        print(f"  {label:38s} {100.0 * buckets[label] / n:5.1f}%")
    print("  top ZooCraft, pathfinder and SmartBrainLib methods (inclusive):")
    shown = 0
    for name, c in inclusive.most_common():
        if name.startswith(("com.zoocraft", "net.minecraft.world.level.pathfinder", "net.tslat")):
            print(f"    {100.0 * c / n:5.1f}%  {name}")
            shown += 1
            if shown >= top:
                break


if __name__ == "__main__":
    main()
```

- [ ] **Step 6: README**

`tools/perf/README.md`:

````markdown
# ZooCraft performance harness

Reproduces the measurements in the Phase 3 spec (section 3) and records new ones. Run everything from the repository
root with Git Bash.

## One-off setup

```bash
python tools/perf/perf_world.py prepare     # separate perf world, raised limits, settings backed up
./gradlew runServer                         # in one terminal (creates the superflat perf-world)
./gradlew runClient -PjoinServer            # in another; joins as Dev
python tools/rcon.py "op Dev" "gamerule doMobSpawning false" "gamerule doDaylightCycle false" \
  "gamerule doWeatherCycle false" "time set noon" "gamemode spectator Dev" \
  "forceload add -32 -32 271 112" "forceload add -32 113 271 271" "tp Dev 120 -40 120"
cd tools/perf
python build_park.py "Big Park" 0 0 15 2
```

The four-zoo layout: close Big Park (`execute as Dev run zoo close "Big Park"`), force-load `368 -32 688 120` and
`368 121 688 288`, then build `"North West" 400 0 6`, `"North East" 560 0 6`, `"South West" 400 160 6` and
`"South East" 560 160 6`.

## Measuring

```bash
python ramp.py big "Big Park" 0,12,25,50,100,150,200
python ramp.py four "North West,North East,South West,South East" 12,25,50
```

Add `--jfr <pid>` for a profile per level (`jcmd -l` lists the server's process as `serverRunProgramArgs`), then
`python jfr_summary.py results/big-200.jfr`. Results tables go to `tools/perf/results/` (not committed).

## Afterwards

Stop the server (`python tools/rcon.py stop`) and run `python tools/perf/perf_world.py restore`.

## Reading the numbers

- Compare averages with the no-visitor level of the same layout. The difference divided by the visitor count is the
  cost per visitor (the number the limit rule in the spec uses).
- `ZooCraft ms` is the `ZOOCRAFT_TOTAL` timer: time inside ZooCraft-timed code, counted once even when timers nest.
  It includes SmartBrainLib's path following, because visitors cause it.
- The tick percentiles come from vanilla `/tick query` (100-tick samples); the table keeps the worst of twelve samples.
````

- [ ] **Step 7: Rerun the Big Park ramp and check it against the spec**

Follow the README with the Task 1–5 build. Run `python ramp.py big "Big Park" 0,50,100,200 --jfr <pid>`.
Expected: each average is within 15% of the spec's section 3.2 value at the same level (9.1, 12.1, 17.5 and 29.8 ms). `/zoo debug perf` lists "visitor path following" as the top slot at 100 and 200. `jfr_summary.py results/big-200.jfr` puts path smoothing within 10 points of 46.9%. Timers are always on, so a ZooCraft total under 0.5 ms above the navigation figure shows the timers themselves cost little.

If an average is more than 15% off, look for a cause before going further: other programs running, a different chunk count, or a timer accidentally wrapping something expensive. Write down what was found.

- [ ] **Step 8: `docs/performance.md`**

```markdown
# ZooCraft performance

What to expect, how to measure, and how to tune. Numbers come from the harness in `tools/perf/`. Phase 3 adds the
post-optimisation tables (M10, M11) and the final defaults (M12).

## Reference machine and layouts

- AMD Ryzen 5 5600X, 32 GB, Windows 11, dev server (`runServer`), one spectator client connected.
- **Big Park:** 240 × 240 blocks, 225 fenced 8 × 8 pens with 2 animals each (450 animals, 15 vanilla species),
  16 shops, 32 toilets, 70 bins, 4 gates, 361 force-loaded chunks.
- **Four zoos:** four 96 × 96 parks of the same design (72 animals each) open at once.

## Before Phase 3 optimisation (2026-10-15)

| Visitors | Big Park avg tick | Worst P95 | Worst P99 | Four zoos avg tick |
|---|---|---|---|---|
| 0 | 9.1 ms | 12.9 ms | | 15.4 ms |
| 50 | 12.1 ms | 18.6 ms | 31.5 ms | 16.6 ms (48) |
| 100 | 17.5 ms | 26.4 ms | 37.1 ms | 20.7 ms |
| 200 | 29.8 ms | 60.3 ms | 113.6 ms | 28.0 ms |

Each visitor costs about 0.05 to 0.10 ms per tick. About two-thirds of that is SmartBrainLib's path smoothing.
ZooCraft's censuses, viewing, litter, spawner and dashboard together stay under 1%.

M9 rerun with the committed harness: (filled in from Task 7's results table, with the date)

## Reading `/zoo debug perf`

- **Server tick**: the whole tick, average and worst over the last 30 seconds. Vanilla's budget is 50 ms.
- **ZooCraft**: time spent in ZooCraft-timed code, counted once. It includes visitors' path following, because
  visitors cause it. The percentage is ZooCraft's share of the tick.
- **Slots**: `visitor path following` (moving along a path, every tick), `visitor path planning` (finding a new
  path), `visitor brain` (SmartBrainLib behaviours, including path planning it starts), `visitor decisions` (the
  visit itself), `animal census`, `facility census`, `visitor spawner`, `dashboard`, `litter`, `chunk loading`.
  Slots can include each other (planning happens inside the brain), so they don't add up to the total.
- **Stuck visitors**: admitted visitors that haven't got closer to their target for 10 seconds. A few for a moment is
  normal in crowds; the same ones for a minute is a problem worth reporting.
- **Exhibits**: views per exhibit since each exhibit was first counted. Many never-viewed exhibits mean visitors
  can't reach them or don't choose them.

`/zoo debug perf log <seconds>` writes the same numbers once a second to `logs/zoocraft-perf.csv`.

## Tuning a weaker server

1. Lower `visitors.maxVisitorsTotal` first. Visitor count is the main cost.
2. Raise `simulation.visitorStepTicks` (for example to 20) and `simulation.viewingIntervalTicks` (to 40). Visitors
   decide and look around less often; their needs and interest still change at the same rate per second.
3. Keep zoos loaded only where needed: every force-loaded chunk ticks its animals, whether or not ZooCraft is busy.
4. Large animal collections cost vanilla mob ticking time regardless of ZooCraft (450 animals cost about 8 ms per
   tick on the reference machine with no visitors at all).
```

Replace the "M9 rerun" line with the actual table rows and date from Step 7 before committing.

- [ ] **Step 9: Play-test checklist**

`docs/playtests/m9-diagnostics.md`:

```markdown
# M9 play-test: diagnostics and harness

Dev server and client running (`runServer`, `runClient -PjoinServer`), Dev opped, a small zoo open with visitors.

- [ ] `/zoo debug perf` shows a server tick line, ZooCraft's share, the busiest slots first, and one line per open
      zoo with its visitors by phase.
- [ ] `/zoo debug perf "<zoo>"` adds the zoo's own slots, stuck visitors (0 in a working zoo), the most visitors
      heading for one spot, and exhibit view counts.
- [ ] Wall a visitor in with blocks: within about 10 seconds it shows as stuck, with its position.
- [ ] `/zoo debug perf log 5` writes `run-server/logs/zoocraft-perf.csv` with a header and 5 lines; running it
      again replaces the file.
- [ ] `/zoo debug recognise minecraft` summarises the vanilla entity types and lists the first 40 with reasons;
      `logs/zoocraft-recognition.csv` has every one. Cows are "recognised (extends Animal)", zombies "not
      recognised (monster category)".
- [ ] Set `simulation.visitorStepTicks = 20` in `run-server/config/zoocraft-server.toml` while visitors walk: they
      keep visiting normally, just a little slower to react. Set it back to 10.
- [ ] The harness README steps reproduce the Big Park table (Task 7 of the M9 plan).
```

- [ ] **Step 10: CLAUDE.md**

In the "Build, run and test" list, after the `tools/gametest.sh` bullet, add:

```markdown
- Performance: `/zoo debug perf [zoo]` shows ZooCraft's own cost per tick (and `/zoo debug perf log <seconds>` writes `logs/zoocraft-perf.csv`); `/zoo debug recognise [namespace]` lists recognition verdicts with reasons. The harness in `tools/perf/` (see its README) builds the reference parks over RCON and records ramps; results and expectations are in `docs/performance.md`.
```

- [ ] **Step 11: Restore the dev server and commit**

Run: `python tools/perf/perf_world.py restore`, then check `git status`: only the files from this task have changed.

```bash
git add tools/perf docs/performance.md docs/playtests/m9-diagnostics.md CLAUDE.md .gitignore
git commit -m "M9: perf harness, baseline rerun, performance doc and play-test checklist"
```

---

## Self-review notes

- Spec 6.1 timers: Tasks 1–2. 6.2 perf command and log: Task 3. 6.3 recognition report: Task 4. 6.4 harness: Task 6. 6.5 simulation settings: Task 5. The spec's `simulation` keys `pathShortcutMode`, `pathReplanGap` and the far-visitor settings arrive with the behaviour they control (M10 and M11). Adding them now would expose settings that do nothing. 6.6 tests: JUnit in Tasks 1 and 4, GameTests in Tasks 2 to 5, manual rerun in Task 6.
- Deviation from the spec, recorded here: exhibit coverage reports views since each exhibit was first counted, not "today". The harness measures runs as before and after, so lifetime counts give the same coverage figures for a fresh park. M11 adds a daily count when route selection needs it. Crowding uses "visitors heading for the same block" until M10's occupancy counts replace it.
- Type consistency: `SimulationTimers.start()/stop(PerfSlot, UUID, long)`, `TimerSlots.averageNanos(int[, int])`, `PerfReport.ms(double)` and `RecognitionReport.Entry(id, recognised, reason, checked)` are used with the same signatures in every task.
