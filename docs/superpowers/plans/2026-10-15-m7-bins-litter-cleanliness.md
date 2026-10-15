# M7 Bins, Litter and Cleanliness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A dedicated rubbish bin block, rubbish that visitors carry after eating, physical litter they drop when no bin is handy, manual cleanup, litter caps, a cleanliness rating fed by what visitors actually see, and litter hotspot and full-bin alerts (Phase 2 spec section 9).

**Architecture:** Pure rules in `core.litter` (`LitterRules` for caps and piles, `LitterHotspots` for grid clustering, `Cleanliness` for samples and blending) are JUnit-tested, and the planner gains a BIN errand. On the Minecraft side, a `litter` package holds the `RubbishBinBlock` and its block entity, the `LitterEntity` with its client renderer, `LitterTracker` (all loaded litter, kept from entity join and leave events so nothing scans the world) and `Littering` (the one place litter is dropped, with the caps applied). `FacilityCensus` adds bins to the snapshot by reading block entities in the zoo's loaded chunks. `VisitorErrands` runs the rubbish errand, and `VisitorController` samples nearby litter every 2 seconds.

**Tech Stack:** NeoForge 21.1.251 block entities, a non-living `Entity` with synced data, `EntityRenderer` with `ItemRenderer.renderStatic`, `EntityJoinLevelEvent` and `EntityLeaveLevelEvent`, generated 16×16 PNG textures (`tools/make_item_textures.py`), data-driven recipe and loot table.

**Spec:** `docs/superpowers/specs/2026-10-14-phase2-management-loop-design.md` (section 9)

**Execution note:** Autonomous build (user memory). Native execution on branch `phase2`.

## Global Constraints

- Bin capacity `binCapacity` (32); visitors look for a bin within `binSearchRadius` (16) blocks; they carry rubbish up to 30 seconds; without a bin they drop it with chance `litterChanceWithoutBin` (0.75), otherwise they pocket it.
- Caps: `maxLitterPerZoo` (48) entities per zoo, `maxLitterTotal` (300) per server. At a cap, the nearest piece within 8 blocks grows (pile size at most 4); if there is none, nothing is dropped.
- Litter is only dropped inside a zoo's boundary and never in fluids.
- Anyone can empty a bin (empty-hand use) or collect litter (hit it). Emptying gives nothing back.
- Cleanliness sample = max(0, 100 − 20 × litter units within 8 blocks); zoo cleanliness += 0.05 × (sample − cleanliness). A visitor seeing 3 or more units loses 2 satisfaction; after 3 such sightings it says "Why is there rubbish everywhere?" once.
- Hotspots: 16 × 16 block cells with 3 or more units, at most 3 shown.
- Bins are the only dedicated zoo block.

## Review Focus

- Litter left outside the boundary by an edit is no longer counted for that zoo (Task 3: the tracker filters by `zoo.contains`; GameTest).
- A bin broken while a visitor walks to it: the visitor drops the rubbish where it stands or carries on looking; nothing crashes (Task 4: the arrival check re-reads the block entity; GameTest).
- Hundreds of drops from a busy zoo stay within the caps (Task 3: GameTest with 100 forced drops).
- A visitor reloaded while holding rubbish: the hand is already cleared on load (M6). It simply never drops that piece (accepted).
- A visitor whose zoo closes while it carries rubbish leaves without littering (Task 4: littering only happens while exploring).

---

### Task 1: Litter rules, hotspots, cleanliness and the BIN errand

**Files:**
- Create: `core/litter/LitterRules.java`, `core/litter/LitterHotspots.java`, `core/litter/Cleanliness.java`
- Modify: `core/needs/Errand.java` (add `BIN`), `core/needs/PlannerInput.java` (add `hasRubbish`, `binAvailable` at the end), `core/needs/VisitPlanner.java` (BIN after FOOD, before the ending checks)
- Test: `src/test/java/com/zoocraft/core/litter/LitterRulesTest.java`, `LitterHotspotsTest.java`, `CleanlinessTest.java`; update `core/needs/VisitPlannerTest.java`

**Produces:**

```java
public final class LitterRules {
    public static final int MAX_PILE = 4;
    public static final double MERGE_DISTANCE = 8;
    public enum Outcome { SPAWN, GROW, NONE }
    /** @param nearestPile pile size of the nearest litter within MERGE_DISTANCE, or 0 when there is none */
    public static Outcome decide(int zooCount, int zooCap, int serverCount, int serverCap, int nearestPile);
}
public final class LitterHotspots {
    public record Point(int x, int z, int units) {}
    public record Hotspot(int centreX, int centreZ, int units) {}
    public static List<Hotspot> find(List<Point> litter, int cellSize, int minUnits, int limit);
}
public final class Cleanliness {
    public static final double WEIGHT = 0.05;
    public static double sample(int unitsNearby);              // max(0, 100 - 20 × units)
    public static double blend(double current, double sample); // current + WEIGHT × (sample - current), clamped 0-100
    public static String label(double cleanliness);            // "clean" ≥ 80, "some_litter" ≥ 50, else "dirty"
}
```

Rules for `decide`: below both caps, SPAWN. At or over a cap, GROW when there's a pile under `MAX_PILE` nearby, otherwise NONE.

- [ ] JUnit: `decide` covers each branch, including a full pile counting as "none nearby" (NONE).
- [ ] JUnit: hotspots group by cell, drop cells under the minimum, sort by units, respect the limit, and handle negative coordinates (floor division).
- [ ] JUnit: the sample for 0, 2 and 6 units is 100, 60 and 0; blending moves 5% of the way; labels at 80 and 50.
- [ ] JUnit: the planner sends a visitor with rubbish and a bin available to BIN; food and toilet still come first; no bin means no BIN errand.
- [ ] Run `./gradlew test`, commit.

### Task 2: Rubbish bin block

**Files:**
- Create: `litter/RubbishBinBlock.java` (shape 12×14×12 pixels; `fill` property 0 to 3 for the model; empty-hand use empties it), `litter/RubbishBinBlockEntity.java` (`int fill`, saved; `boolean deposit()` adds one unless full; `void empty()`; `boolean full()`), `registry/ZooBlocks.java` (block, block item, block entity type)
- Create: assets (`blockstates/rubbish_bin.json`, block models for 4 fill stages, item model, textures from `tools/make_item_textures.py`), `data/zoocraft/recipe/rubbish_bin.json` (shaped: iron ingots "I I", "IGI", "III" with G = green dye), `data/zoocraft/loot_table/blocks/rubbish_bin.json`
- Modify: `config/ZooConfig.java` (a `litter` section with the five server keys), `client/ZooClientConfig.java` (`highlightLitter`), `ZooCraft.java`, lang
- Test: `gametest/LitterGameTests.java`

- [ ] GameTest: depositing 32 times fills the bin (the 33rd deposit is refused); `full()` is true; the block state's `fill` is 3; using it with an empty hand empties it to 0.
- [ ] GameTest: the fill level survives saving the block entity and loading it again.
- [ ] Implement, run `tools/gametest.sh`, commit.

### Task 3: Litter entity, tracker, dropping and collecting

**Files:**
- Create: `item/RubbishItem.java` is not needed. Three plain items `rubbish_wrapper`, `rubbish_cup` and `rubbish_paper` in `registry/ZooItems.java` (`ZooItems.rubbish(int variant)`), with textures
- Create: `litter/LitterEntity.java` (synced `variant` and `pile`; gravity and ground friction; no despawn; `hurt` by a player collects it with a sound and calls `ZooManager.recordLitterCollected`; saved `variant` and `pile`; `isCurrentlyGlowing` on the client goes through a static hook set by the client), `litter/LitterTracker.java` (join and leave events; `litterIn(Zoo zoo)` filters by level and `zoo.contains`; `total()`), `litter/Littering.java` (`drop(ServerLevel, Zoo, Vec3 at, int variant) -> LitterRules.Outcome` applying caps, merge and the boundary and fluid rules), `client/LitterRenderer.java` (the variant's item lying flat; one copy per pile unit, slightly offset), `client/LitterHighlight.java` (sets the glow hook: the local player holds a zoo tool and `highlightLitter` is on)
- Modify: `registry/ZooEntities.java` (`LITTER`, MISC, 0.4 × 0.2), `zoo/ZooStats.java` (`litterCollected`, optional in the codec), `zoo/ZooManager.java` (`recordLitterCollected(Zoo)`), `client/ZooCraftClient.java` (renderer)
- Test: `gametest/LitterGameTests.java`

- [ ] GameTest: 100 drops spread over a 20 × 20 area of a zoo never leave more than 48 litter entities in that zoo, and some piles have grown above 1.
- [ ] GameTest: a drop outside the boundary or in water does nothing.
- [ ] GameTest: hitting litter with a player removes it and the zoo's `litterCollected` goes up by 1.
- [ ] GameTest: litter saved and loaded keeps its variant and pile; once added to the level, the tracker counts it.
- [ ] GameTest: after a boundary edit leaves a piece outside, `LitterTracker.litterIn(zoo)` no longer includes it.
- [ ] Implement, run `tools/gametest.sh`, commit.

### Task 4: Visitors with rubbish: bins and littering

**Files:**
- Modify: `shop/FacilitySnapshot.java` (`BinState(pos, fill, capacity)` with `usable()` = not full), `shop/FacilityCensus.java` (scans block entities of the zoo's loaded chunks within its bounding box; refreshes zoos that have shops, toilets or bins, so it now runs for every open zoo), `visitor/VisitorErrands.java` (after eating, hold a random rubbish item; BIN errand to the nearest usable, reachable bin within `binSearchRadius`; at the bin, deposit through the real block entity, and if it's full or gone, drop the litter beside it; with no bin, carry for up to 30 seconds, then drop it if `litterRoll < litterChanceWithoutBin`, otherwise pocket it), `visitor/Visitor.java` (saved `litterRoll`, test hook `testSetLitterRoll`, `testHoldRubbish()`)

- [ ] GameTest: a visitor holding rubbish with a bin 6 blocks away walks to it; the bin's fill goes up by 1 and the visitor's hand is empty.
- [ ] GameTest: no bin and litter roll 0: within 40 seconds a litter entity appears inside the zoo, and the hand is empty.
- [ ] GameTest: litter roll 0.99 and no bin: after 40 seconds no litter appears, and the hand is empty (pocketed).
- [ ] GameTest: a full bin is not chosen; the visitor drops litter after carrying it (roll 0).
- [ ] GameTest: the bin is broken after the visitor sets off: no crash; the visitor ends with an empty hand.
- [ ] Implement, run `tools/gametest.sh`, commit.

### Task 5: Cleanliness, complaints and alerts

**Files:**
- Modify: `zoo/Zoo.java` (`cleanliness`, optional in the codec, default 100), `zoo/ZooManager.java` (`recordCleanlinessSample(Zoo, double sample)` blends and marks dirty), `visitor/VisitorController.java` and `visitor/Visitor.java` (every 2 seconds while exploring: count units within 8 blocks from `LitterTracker.litterIn(zoo)`, record the sample, apply −2 satisfaction at 3 or more units, and say `RUBBISH_EVERYWHERE` once after 3 such sightings; `litterSeen` saved), `feedback/CommentType.java` (`RUBBISH_EVERYWHERE`, −5, complaint), `dashboard/DashboardService.java` (alerts for each full bin in the snapshot, "The bin at (x, z) is full. Empty it.", and the top 3 hotspots, "Litter is piling up near (x, z). Clear it up or add a bin nearby."; Overview fields for cleanliness), `network/DashboardSnapshot.java` (`float cleanliness`, `long litterCollected`), `client/DashboardScreen.java` (a cleanliness bar with its label), lang

- [ ] GameTest: a visitor standing among 5 litter units lowers the zoo's cleanliness below 100 within 3 seconds and, after enough sightings, says "Why is there rubbish everywhere?" exactly once.
- [ ] GameTest: an Overview snapshot of a zoo with a full bin and a cluster of 4 litter units includes both alerts.
- [ ] GameTest: a Phase 1 zoo record without `cleanliness` loads at 100.
- [ ] Implement, run `tools/gametest.sh`, commit.

### Task 6: Play-test checklist, manual run and wrap-up

**Files:**
- Create: `docs/playtests/m7-litter.md`
- Modify: `CLAUDE.md` code map, `docs/compatibility.md` (litter is an entity: magnet mods and item vacuums ignore it; entity-clearing commands such as `/kill @e` remove it)

- [ ] Manual (dev server and client): visitors eat, carry rubbish to a bin, and the bin model fills; with no bins, litter appears, glows while holding a zoo tool, the hotspot alert appears, cleanliness falls, and collecting the litter lets it recover.
- [ ] Run `./gradlew build` and `tools/gametest.sh`, commit, self-review.
