# ZooCraft Phase 3 Scale and Compatibility: Design Specification

**Date:** 2026-10-15
**Status:** Draft for review
**Source of truth:** [zoocraft_game_design_document_v1.0.md](../../../zoocraft_game_design_document_v1.0.md) (the GDD). This spec implements GDD section 11, Phase 3 ("Scale and compatibility"), and builds on the Phase 1 spec, [2026-10-14-phase1-prototype-design.md](2026-10-14-phase1-prototype-design.md), and the Phase 2 spec, [2026-10-14-phase2-management-loop-design.md](2026-10-14-phase2-management-loop-design.md). "P1 8.3" means Phase 1 spec section 8.3; "P2 8.4" means Phase 2 spec section 8.4.

## 1. Purpose and success criteria

The GDD exit condition:

> Representative large-park tests demonstrate stable gameplay, with documented performance expectations and known compatibility limitations.

Phase 3 is complete when:

1. On the reference machine, 200 visitors add at most 8 ms to the average tick over the same world with no visitors (0.04 ms per visitor, down from about 0.1), and the 99th-percentile tick stays under 50 ms. This holds for the reference large park (section 3.1) and for the four-zoo layout with 200 visitors split between the zoos.
2. Visitors in a crowded zoo spread out across viewing spots, gate queues and shop counters instead of piling onto one block, and no visitor is stuck for more than 60 seconds in the reference tests.
3. In the reference park, at least 95% of exhibits are viewed during a 20-minute run with 100 visitors, and no exhibit gets more than 4 times the median number of views.
4. Exhibits in pens of any size up to 32 by 32 blocks get reachable viewing spots, and exhibits receive a plain-language rating covering viewing access, space and surroundings.
5. Animals are found at any height inside the boundary.
6. Default visitor limits are set from measured numbers by the rule in section 9.2, and `docs/performance.md` documents what to expect on the reference machine and how to tune for weaker ones.
7. Recognition has been checked creature by creature in ATM10 for Twilight Forest, the Aether, Aquaculture, the Undergarden and Productive Bees (recognised), and for MineColonies citizens, Ice and Fire dragons and Cataclysm bosses (not recognised), with the results in `docs/compatibility.md`.
8. The standalone production jar runs on a dedicated NeoForge server, and every acceptance test in sections 6 to 9 passes.

### Out of scope for Phase 3

Staff, cleaners, keepers and wages; breeding and conservation; per-species welfare requirements (section 8.5 adds generic space and surroundings only); rides, tours and attractions; new visitor personalities; unlock trees. All of these belong to Phase 4. Phase 3 does not promise "hundreds of visible visitors" (GDD section 9): it raises limits only as far as the measurements allow.

## 2. Working arrangement

Unchanged: Claude writes the code and makes architecture decisions, prefers existing libraries and built-in NeoForge systems, and splits the work into milestones M9 to M12, each with its own plan in `docs/superpowers/plans/` and a play-test checklist in `docs/playtests/`. Gameplay calls the user may want to change are listed in section 12.

Performance work follows one rule: **measure, change, measure again with the same harness.** Every optimisation in this spec names the measurement that justifies it and the number it must reach.

## 3. Measured starting point

### 3.1 Method

Measured on 2026-10-15 on the user's machine (AMD Ryzen 5 5600X, 32 GB), dev server (`runServer`) on a superflat world, with one client connected in spectator mode. Throwaway scripts (to be committed as the harness in M9) built the parks over RCON, raised the visitor limits in steps, sampled `/tick query` every 5 seconds for a minute at each step, and recorded a Java Flight Recorder (JFR) profile of the same minute. Mob spawning, the day cycle and weather were off. The parks were force-loaded.

- **Reference large park ("Big Park"):** 240 by 240 blocks (57,600 square blocks), a 16-block grid of 2-wide gravel paths, 225 fenced 8 by 8 pens with 2 animals each (450 animals, 15 vanilla species), 4 gates (one per side), 16 shops stocked with bread, cookies, honey and flowers, 32 toilets and 70 bins. 361 force-loaded chunks.
- **Four-zoo layout:** four 96 by 96 parks of the same design (36 pens, 72 animals each), open together, with Big Park closed but still loaded.

### 3.2 Results

Average tick time, with the worst 95th and 99th percentiles seen across the twelve samples:

| Visitors | Big Park | Four zoos (total visitors) |
|---|---|---|
| 0 | 9.1 ms (P95 12.9) | 15.4 ms (all five zoos' animals loaded) |
| 12 | 8.8 ms (P95 13.7) | |
| 25 | 10.7 ms (P95 17.5) | |
| 50 | 12.1 ms (P95 18.6, P99 31.5) | 16.6 ms (48) |
| 100 | 17.5 ms (P95 26.4, P99 37.1) | 20.7 ms (100; P95 30.8) |
| 150 | 22.0 ms (P95 33.5, P99 43.2) | |
| 200 | 29.8 ms (P95 60.3, P99 113.6) | 28.0 ms (200; P95 39.7, P99 62.8) |

Share of server-thread CPU time (JFR, inclusive):

| | 25 | 50 | 100 | 150 | 200 | Four zoos, 200 |
|---|---|---|---|---|---|---|
| SmartBrainLib path smoothing (`attemptShortcut`) | 11.7% | 22.0% | 36.5% | 42.1% | 46.9% | 26.4% |
| Pathfinding, all mobs (`PathFinder.findPath`) | 6.4% | 4.9% | 3.3% | 3.2% | 2.9% | 5.1% |
| Visitor brain and controller | 1.3% | 2.5% | 3.3% | 3.4% | 4.2% | 3.6% |
| Animal census, facility census, viewing, litter, spawner, dashboard (together) | under 1% | under 1% | under 1% | under 1% | under 1% | under 1% |
| Entity pushing | 2.0% | 1.5% | 1.1% | 0.7% | 1.1% | 1.4% |

### 3.3 What the numbers say

- **Each visitor costs about 0.05 to 0.10 ms per tick,** whether visitors are in one zoo or spread over four. Running several zoos adds no measurable cost of its own.
- **Path following dominates.** SmartBrainLib's `SmoothGroundNavigation` tries a straight-line shortcut along the path every tick for every moving visitor, testing the path type of each block the visitor's box would cross (`collidesWhileTraversing` → `VisitorNodeEvaluator.getPathType` → `WalkNodeEvaluator.checkNeighbourBlocks`). This uncached neighbour check is about two-thirds of the cost each visitor adds.
- **ZooCraft's own simulation is cheap.** The census of 450 animals, the facility census, viewing checks, litter checks, the spawner and the dashboard add up to under 1%. Phase 1 and 2 caching works and is not the bottleneck.
- **Ticks start going over the 50 ms budget at about 200 visitors in one park** (P99 114 ms). Garbage-collection pauses stayed under 10 ms, so the spikes come from something else, most likely world autosave or bursts of path planning. The current tooling can't tell which. That is why M9 adds per-tick timing.
- **Route choice clusters.** After about 20 minutes with up to 200 visitors, the median exhibit had 16 views, the busiest had 89, and 15 of 212 exhibits (7%) had none. Choice weight falls with distance as `1 / (1 + distance / 16)` (P1 8.5), so in a 240-block park a far exhibit is several times less likely to be chosen than a near one, whatever has been seen.
- **Large pens can't be viewed.** Viewing spots are sampled 2.5 to 6 blocks from an exhibit's centre (P1 8.3). In any pen wider than about 12 blocks, every sample lands inside the fence. The test park used 8 by 8 pens to avoid this.
- **The animal search is height-limited:** 48 blocks below and 64 above the boundary corners and gates. The census cost of 0.2% shows there is room to search the full height.

### 3.4 M9 findings (added after M9)

The M9 timers reproduced section 3.2 within 15% and added two things JFR had hidden:

- **Path planning costs about as much as path following.** At 200 visitors in Big Park, visitors start about 54 new paths a second at about 2.6 ms each: 7 ms per tick, against 7.9 ms for path following. JFR put `PathFinder.findPath` at only about 2%, so the time goes on work around the A* search (building its region) or somewhere JFR doesn't sample. M10 measures a single plan in isolation and adds a plan-rate fix (the re-plan gap from section 8.4, brought forward) if planning is still above 3 ms per tick at 200 visitors after section 7.1.
- **About 8% of visitors are stuck at any moment** at 200 visitors (16 of 200 with no progress for 10 seconds). M10's crowd recovery (section 7.4) is measured against this number.

## 4. Key decisions

### 4.1 Fix path following inside ZooCraft, not by forking SmartBrainLib

`attemptShortcut` is a default method on SmartBrainLib's `ExtendedNavigator` interface, and `followThePath` is a protected method of `SmoothGroundNavigation`. `VisitorNavigation` overrides them (section 7.1). Visitors keep SmartBrainLib's smooth movement, which looks better than vanilla node-to-node walking.

Alternatives considered:

- *Switch visitors to vanilla `GroundPathNavigation`.* Simplest and cheapest, but visitors zigzag across open ground from node to node, which looks robotic in a zoo where watching them is the point.
- *Tick distant visitors less often (level of detail).* More complex and visible to players. Kept as a fallback in section 7.5, used only if section 7.1 misses its target.

### 4.2 ZooCraft measures itself

A `SimulationTimers` class records how long each ZooCraft system takes, per tick and per zoo, using `System.nanoTime` around the existing entry points. It has a fixed set of named slots and no allocation per call. It is always on, because it costs about as much as the counters it updates. `/zoo debug perf` reads it. This answers "is ZooCraft the problem?" on any server without a profiler, and it gives the harness numbers that don't need JFR. JFR stays the tool for finding hotspots inside Minecraft and libraries.

Spark is not bundled or required. Server owners who have it (ATM10 does) can use it alongside `/zoo debug perf`.

### 4.3 Limits are set by a written rule from measurements

Defaults are not guessed. Section 9.2 defines the rule, and M12 applies it to the post-optimisation numbers. The config ceilings are raised so that powerful servers can go further on their own responsibility.

### 4.4 Enclosures are found by a bounded flood fill, shared by viewing and rating

One search finds an exhibit's enclosure: the floor area its animals can walk on, bounded by fences, walls and other obstacles, capped in size. Viewing spots are taken from just outside its edge (section 8.1), and the exhibit rating uses its area and floor blocks (section 8.5). The search runs only when an exhibit is new or its animals have moved outside the last enclosure, never for every visitor.

## 5. Architecture

### 5.1 New and changed code

Pure Java, unit-tested with JUnit:

- `core.perf`: `TimerSlots` (fixed-slot accumulators with rolling averages and a per-tick maximum), `LimitRule` (the section 9.2 calculation, so it is tested and documented in code).
- `core.exhibit`: `EnclosureFill` (bounded flood fill over an abstract `FloorGrid`), `ViewingSpots` (pick spots along an enclosure edge, spread out, path blocks first), `ExhibitRating` (variety, access, space, surroundings to a score and warnings).
- `core.choice`: `ExhibitChoice` gains popularity, crowding and coverage terms (section 8.3). `SpotChoice` picks the least occupied spot.
- `core.crowd`: `Occupancy` (generic claim and release counts per spot with capacity, used by viewing spots, gate queue slots and shop counters).

Minecraft side:

- `perf` (new): `SimulationTimers` (server-side singleton over `TimerSlots`), `PerfCommand` (`/zoo debug perf`), `PerfLog` (CSV output for the harness).
- `visitor`: `VisitorNavigation` overrides path following (section 7.1). Gate queue slots in `VisitorController`. Spot claims in `VisitorExploration`.
- `animal`: `AnimalCensus` searches the full height and runs `EnclosureFill` through a `LevelFloorGrid`. `Exhibit` gains its enclosure, rating and spot occupancy. `RecognitionReport` (new) runs `/zoo debug recognise`.
- `shop`: counter occupancy for shops (section 7.3), reusing `core.crowd.Occupancy` the way toilets use `ToiletOccupancy`.
- `config`: a new `simulation` section (section 10).
- `dashboard`, `client`: exhibit ratings and new warnings on the Animals tab.
- `tools/perf/`: the park builder, ramp script and JFR summariser (section 6.4).

### 5.2 Data flow

Unchanged from Phases 1 and 2: periodic censuses fill caches, visitors read only the caches, and transactions recheck the real world. Phase 3 adds occupancy claims, made and released by visitors on the server thread as they choose and leave targets, and timer slots written by each system and read by the perf command.

## 6. Diagnostics, harness and simulation settings (M9)

### 6.1 Timers

`SimulationTimers` has one slot per system: visitor controller, visitor brain, visitor navigation (time inside `VisitorNavigation.tick`), pathfinding started by visitors, animal census, facility census, enclosure fills, spawner, dashboard snapshots, litter, chunk loader. The visitor slots are also kept per zoo.

For each slot it keeps the total over the current tick, a rolling 30-second average per tick, the worst single tick in the last 30 seconds, and a call count. It also records the server's total tick time (`ServerTickEvent.Pre` to `Post`), so ZooCraft's share is visible.

### 6.2 `/zoo debug perf`

`/zoo debug perf [zoo]` (operators) prints:

- server tick average and worst over 30 seconds, and ZooCraft's share of it;
- each slot's average and worst per tick, worst first;
- visitors in each phase, per zoo and in total, against the configured limits;
- **stuck visitors**: admitted visitors that have made no progress towards their target for more than 10 seconds, with positions (the first three);
- **crowding**: the most visitors on any one viewing spot, gate queue or counter;
- **exhibit coverage**: views per exhibit today (minimum, median, maximum) and the number never viewed.

`/zoo debug perf log <seconds>` writes one CSV line per second to `logs/zoocraft-perf.csv`: time, tick time, every slot, and visitor counts. The harness reads this instead of scraping `/tick query`.

### 6.3 Recognition report

`/zoo debug recognise [namespace]` (operators) goes through every registered entity type, optionally limited to one mod's namespace. It creates an instance in the current level without adding it to the world, inside a `try/catch`, and applies the recognition rules. For each type it reports the verdict and the rule that decided it ("tag zoocraft:exhibit_animals", "boss tag", "extends Animal", "creature spawn category", "monster category", "not a mob"). Types that fail to construct are reported as "could not be checked" with the category-only verdict. The full list goes to `logs/zoocraft-recognition.csv` and a summary to chat. This is the tool for the ATM10 check in section 9.1 and for modpack authors.

### 6.4 Harness

`tools/perf/` holds:

- `build_park.py`: builds the reference park layout (or the small one) over RCON. The grid size, animals per pen, and shop, toilet and bin spacing are arguments.
- `ramp.py`: steps the visitor limit, waits for visitors to arrive, then records one minute per step from `/zoo debug perf log` plus an optional JFR recording, and writes a results table.
- `jfr_summary.py`: inclusive server-thread CPU share per method and per bucket, as in section 3.2.
- `README.md`: how to reproduce section 3 exactly, including the world settings and the restore step for the dev server's config.

The harness works against a dev server started with `runServer` and joined by `runClient -PjoinServer`, as in the earlier play-tests. It switches `level-name` to a separate `perf-world` and puts the original `server.properties` and server config back when it finishes.

### 6.5 Simulation settings

A new `simulation` config section (section 10) exposes the intervals that were constants: the visitor step, the viewing check, the litter check, the path shortcut mode, the gap between path re-plans, and the far-visitor settings from section 7.5. Every value is read live, like the existing config.

### 6.6 Acceptance tests (M9)

- JUnit: `TimerSlots` rolling average, worst-tick tracking and the 30-second window; `LimitRule` examples.
- GameTest: after a visitor has walked for a few seconds, the visitor slots have non-zero counts, and the per-zoo split adds up to the total.
- GameTest: `/zoo debug recognise minecraft` classes a cow as recognised ("extends Animal"), a zombie and a villager as not recognised, each with the rule that decided it.
- Manual: rerun section 3's Big Park ramp with the committed harness. The results match section 3.2 within 15%, and `/zoo debug perf` puts visitor navigation at the top.

## 7. Crowd movement and congestion (M10)

### 7.1 Cheaper path following

`VisitorNavigation` changes two things:

1. **Shortcuts only when there is something new to skip.** A shortcut is attempted when the visitor reaches a new path node or when its path changes, not on every tick. Between attempts, the visitor follows the path it already has. With `pathShortcutMode = EVERY_TICK` it behaves exactly as SmartBrainLib does today, so the change can be compared and reverted from config. `OFF` turns shortcuts off entirely.
2. **Cached block checks.** `VisitorNodeEvaluator.getPathType`, which the shortcut test calls for every block it crosses, goes through Minecraft's shared `PathTypeCache` (the cache the A* search already uses through `getCachedPathType`) instead of recomputing neighbour danger every time. The fence-gate rule (P1 7.5) stays.

**Target:** in Big Park at 200 visitors, visitor navigation's share falls from 47% to under 15% of server-thread time, visitors add at most 8 ms to the no-visitor average (about 17 ms in total, down from 29.8 ms), and the P99 stays below 50 ms. Movement must look the same in a side-by-side check: visitors still walk straight across open ground and round corners on paths.

### 7.2 Spreading out at exhibits

- Each exhibit has up to `maxViewingSpots` (default 12, up from 6) spots (section 8.1). Each spot holds `viewingSpotCapacity` (default 2) visitors.
- A visitor choosing an exhibit claims its least-occupied reachable spot (`SpotChoice`), preferring path blocks among spots that tie. The claim is released when the visitor leaves the spot, chooses again, leaves the zoo or is removed.
- When every spot of an exhibit is full, its choice weight is multiplied by 0.1 (section 8.3). A visitor that arrives at a full spot anyway (because it claimed before the spot filled, or after a reload) waits up to 5 seconds for any spot of that exhibit, then moves on and says "It's too crowded to see anything." once per visit. That records a `CROWDED` complaint with the exhibit as subject.
- Advice: "The {species} exhibit is too crowded. Give visitors more room to see it." Visitors already spread out, so this line means real demand rather than a pathing artefact.

### 7.3 Spreading out at gates and counters

- **Gate queue:** instead of one block outside the gate, arriving visitors queue on up to 8 slots in an arc 2 to 4 blocks outside it (the standable blocks found when the gate is designated, and refreshed when the boundary changes). A group shares neighbouring slots. With every slot full, a visitor waits 3 blocks further out. Admission is unchanged (P1 7.4).
- **Shop counters:** a counter serves `counterCapacity` (default 2) visitors at once, the same model as toilets (P2 8.4). The others wait nearby for up to 20 seconds, then try another shop that sells what they want, or record "The queue at {shop} was too long." with the shop as subject. Advice: "{shop} is too busy. Add another counter or shop nearby." A sale still happens in one step at the counter (P2 7.5). Occupancy only decides who stands there.
- **Exits:** visitors leaving choose the nearest gate weighted by how many others are already heading to it (a gate's weight halves for every 8 visitors heading to it), so one gate doesn't take the whole crowd when the zoo closes.

### 7.4 Recovering from crowding

- The existing no-progress rule (30 seconds, 3 failures, P1 7.5) stays. Before counting a failure, a visitor blocked mainly by other visitors (more than 2 within 1 block for 5 seconds) steps aside to a random standable block within 3 blocks and re-plans once. Crowding alone then doesn't mark exhibits or facilities unreachable.
- The never-trapped safety net (P1 7.7) is unchanged.

### 7.5 Fallback: distant visitors tick less often

Only built if section 7.1 misses its target. Visitors with no player within `farVisitorDistance` (default 64 blocks) run their controller and their brain's idle tasks every `farVisitorStepMultiplier` (default 2) steps. Movement keeps ticking every tick, so walking stays smooth. Viewing, needs and timers use elapsed game time rather than step counts, so results don't change with the multiplier. If built, it is off by default for zoos with fewer than 50 visitors.

### 7.6 Where visitors look

Visitors currently stare at players. Their idle tasks try SmartBrainLib's `SetPlayerLookTarget` first, so any visitor near a player watches that player for as long as the player stays nearby. In a zoo, visitors should be looking at the animals.

A new `ChooseLookTarget` behaviour replaces the `FirstApplicableBehaviour(SetPlayerLookTarget, SetRandomLookTarget)` pair. Each time the visitor needs a new look target (when the last one expires), it picks, in this order:

1. **While lingering at an exhibit or viewing animals:** a living animal of that exhibit within viewing distance, from the census cache. A new one is chosen every 3 to 6 seconds, so the visitor's gaze moves around the pen.
2. **While walking:** a point a few blocks ahead along its path, or a cached animal it passes within viewing distance, with a 30% chance per choice.
3. **A glance at a player:** only when a player is within 6 blocks, with a 10% chance per choice, for 1 to 2 seconds. After a glance, that visitor won't look at a player again for 20 to 40 seconds. A player who right-clicks a visitor to read its info gets a 3-second look, with no cooldown.
4. **Otherwise:** a random nearby direction, as `SetRandomLookTarget` does now.

A group leader's group members may look at the leader instead of a random direction (20% chance), so groups read as groups. Animals come from the cached census only, never from an entity search, so the behaviour costs nothing measurable. It runs inside the existing brain timer slot (section 6.1).

### 7.7 Acceptance tests (M10)

- JUnit: `Occupancy` claim, release, capacity and leak-free release on removal; `SpotChoice` prefers empty and path spots.
- GameTest: a visitor walking a 30-block straight path with `pathShortcutMode = EVERY_NODE` arrives in the same time (within 10%) as with `EVERY_TICK`.
- GameTest: 8 visitors sent to one exhibit with 4 spots of capacity 2 occupy all 4 spots, two each. A ninth waits, then moves on with the crowded comment.
- GameTest: a counter with capacity 1 and two hungry visitors serves both in turn; with a long wait, the second tries another shop.
- GameTest: a group of 4 queues on separate gate slots.
- GameTest: closing a zoo with 30 visitors and 2 gates sends visitors to both gates, and everyone has left within the existing time limit.
- JUnit: the look-target choice order, the player-glance chance and its cooldown (pure logic in `core.visitor.LookChoice`).
- GameTest: a visitor lingering at an exhibit with a player standing 3 blocks away spends less than 15% of 60 seconds looking at the player, and most of that time looking at the exhibit's animals.
- Manual: stand among visitors at an exhibit. They look at the animals and only glance at you now and then.
- Manual (harness): rerun the Big Park and four-zoo ramps. Section 7.1's targets are met and the results go into `docs/performance.md`.

## 8. Exhibit evaluation and route selection (M11)

### 8.1 Enclosures and viewing spots

- When an exhibit is new, or one of its animals is outside its last enclosure, the census runs `EnclosureFill`. It starts from the animals' floor blocks and spreads across standable floor blocks the animals could walk to, at step heights of up to 1 block, without crossing blocks that block movement (fences, walls, glass, closed gates). It stops after `maxEnclosureArea` (default 1,024) floor blocks.
- **Bounded enclosure:** the fill stopped by itself. Viewing spots are standable blocks inside the boundary, outside the enclosure, within 2 blocks of its edge and within `viewingRadius` of at least one floor block of the enclosure. Up to `maxViewingSpots` are picked, spread round the edge (one per edge segment of roughly equal length), path blocks first.
- **Open exhibit:** the fill hit the cap, as with animals roaming open land or a pen bigger than 32 by 32. Spots are sampled around the animals themselves, as in Phase 1, but around each animal's position rather than the exhibit's centre.
- Fills are spread over ticks: at most `enclosureFillsPerTick` (default 2) per server tick across all zoos. Until its fill is done, an exhibit keeps its old spots. The fill's time goes into the enclosure timer slot.
- A pen bigger than the viewing distance whose animals keep to its middle can have spots that see nothing. Viewing (P1 8.4) is unchanged, so those spots simply produce no views, and the rating in 8.5 reports it.

### 8.2 Full-height animal search

The census entity query covers the zoo's bounding box from the level's minimum to maximum build height. M11 measures the cost in Big Park and a 64-block-deep test pit; the target is that the census stays under 1% of tick time. The boundary rule is unchanged: an animal counts if its column is inside the boundary.

### 8.3 Route selection

`ExhibitChoice.weight` becomes:

`base × unseenBonus × recentPenalty × distanceFactor × popularityFactor × crowdFactor`

- `base` and `unseenBonus`: unchanged (1, plus 3 per unseen species, 6 for enthusiasts).
- `recentPenalty`: unchanged (0.2 for the last 3 exhibits visited).
- `distanceFactor`: `1 / (1 + distance / routeScale)`, where `routeScale` is a quarter of the zoo's larger dimension and at least 16. A far exhibit in a big park is then penalised about as much as a far exhibit in a small one.
- `popularityFactor`: `1 / (1 + viewsToday / (medianViewsToday + 1)) ^ 0.5`. Over-viewed exhibits get a little less weight; never-viewed ones get the most.
- `crowdFactor`: 0.1 when every spot is full (section 7.2), otherwise `1 - 0.5 × occupiedShare`.

`ExhibitChoice.pick` keeps the same interface. Inputs come from the census cache and the occupancy counts, so choosing stays cheap.

**Target:** at least 95% of exhibits viewed in 20 minutes with 100 visitors in Big Park, and no exhibit above 4 times the median views (section 1).

### 8.4 Long routes

Visitors' follow range is 48 blocks, so a target 150 blocks away is reached by walking partial paths. The Big Park runs showed this works, but each partial path costs a re-plan. `pathReplanGap` (default 20 ticks) stops a visitor re-planning again within that gap of its last re-plan, unless the path failed. M11 measures re-plans per visitor-minute in `/zoo debug perf`. If re-planning appears in the top three slots, the follow range is raised to 64 and measured again.

### 8.5 Exhibit rating

Every exhibit gets a rating from 0 to 100, recomputed with its enclosure and shown on the Animals tab as Excellent, Good, Fair or Poor, with any warnings. Generic rules only (GDD 5.3: don't invent strict requirements):

| Part | Weight | Measured as |
|---|---|---|
| Viewing access | 40% | Share of the exhibit's spots that visitors have reached in the last day, and whether any visitor saw an animal from them |
| Variety | 20% | Species in the exhibit (1 species = 60%, 2 = 85%, 3 or more = 100%) |
| Space | 25% | Enclosure floor blocks per animal, full marks at 12 or more and half marks at 6. Open exhibits get full marks. Very large animals (bounding box wider than 1.5 blocks) count double. |
| Surroundings | 15% | Share of the enclosure floor that is natural (grass, dirt, sand, moss, snow and other blocks in the `zoocraft:natural_floor` tag), plus a bonus for water or leaves inside or next to the enclosure, capped at 100% |

Warnings use the existing style and name the exhibit's species:

- "The {species} exhibit looks cramped." when space per animal is below 6.
- "The {species} exhibit has bare ground. Natural surroundings would help." when the natural share is below 25%.
- "Visitors can't get close enough to see the {species}." (the Phase 1 warning) when viewing access is below 25% after at least 10 visit attempts.

The rating nudges satisfaction only: on first viewing an exhibit, a visitor gains +1 for Excellent and loses 1 for Poor, in addition to the existing new-species effect. Ratings never harm animals (GDD 5.3).

### 8.6 Acceptance tests (M11)

- JUnit: `EnclosureFill` on fixtures: a closed 8 by 8 pen, a 20 by 20 pen, an L-shaped pen, a pen with an open gate (fill escapes and hits the cap, so it is open), and a 2-block step.
- JUnit: `ViewingSpots` spreads spots round a pen's edge and prefers path blocks.
- JUnit: `ExhibitChoice` with the new factors: far exhibits in a big zoo aren't starved; an over-viewed exhibit loses weight; a full exhibit drops to 0.1.
- JUnit: `ExhibitRating` parts and warning thresholds.
- GameTest: a 20 by 20 fenced pen gets viewing spots outside the fence, and a visitor sent there views the animals.
- GameTest: a cow 60 blocks below the boundary's height, inside the boundary, is counted.
- GameTest: a cramped pen (4 cows in 3 by 3) raises the cramped warning.
- Manual (harness): Big Park at 100 visitors for 20 minutes meets the coverage target in 8.3.

## 9. Compatibility, limits and sign-off (M12)

### 9.1 ATM10 recognition check

1. In ATM10 (`runAtm10Client -Patm10`), run `/zoo debug recognise` for `twilightforest`, `aether`, `aquaculture`, `undergarden`, `productivebees`, `minecolonies`, `iceandfire` and `cataclysm`.
2. Expected: the first five mods' creatures are recognised, except their bosses and hostile mobs. MineColonies citizens and visitors, the three Ice and Fire dragons and every Cataclysm boss are not.
3. Each surprise is fixed in data, not code: entries in `zoocraft:exhibit_animals` or `zoocraft:not_exhibit_animals` (optional entries, so a missing mod does no harm), or `zoocraft:species` merges for variants that inflate species counts (GDD 13, "Species identity").
4. In-world spot check: in an ATM10 zoo, spawn two creatures per mod, recognised and not recognised, run `/zoo debug census` and check the counts and species names.
5. `docs/compatibility.md` gets a per-mod table: recognised, excluded, merged, and anything that couldn't be checked.

Correct the MineColonies citizen id if the report shows it differs from `minecolonies:citizen` (compatibility.md currently assumes it).

### 9.2 Limit rule and new defaults

`LimitRule` sets the defaults from the M10 and M11 harness results on the reference machine:

1. `visitorCost` = (average tick with N visitors − average tick with none) / N, measured in Big Park at N = 100 and N = 200; the larger value is used.
2. **Server default:** the largest multiple of 10 visitors whose estimated cost is at most 10 ms per tick, **halved** for weaker servers and shared hosts. Section 3's numbers (about 0.10 ms per visitor at 200) give 90, halved to 45. The section 7.1 target (0.04 ms) would give 250, halved to 125.
3. **Per-zoo default:** half the server default, rounded down to a multiple of 4, and at least 12.
4. **Config ceilings:** `maxVisitorsPerZoo` up to 500 and `maxVisitorsTotal` up to 2,000. The comments say that raising them is at the server's own risk, and point to `docs/performance.md`.

### 9.3 `docs/performance.md`

- What to expect on the reference machine: the section 3.2 table, plus the post-M10 and post-M11 tables.
- How to read `/zoo debug perf` and what each slot means.
- Tuning advice in plain language: lower `maxVisitorsTotal` first, then raise `simulation.visitorStepTicks` and `simulation.viewingIntervalTicks`. What keeping zoos loaded costs (each force-loaded chunk ticks its animals). Large animal counts cost vanilla mob ticking time regardless of ZooCraft.
- How to rerun the harness.
- Known limits: crowds beyond the measured range, untested hardware, and zoo areas larger than the reference park.

### 9.4 Large-park and multi-zoo sign-off

With the new defaults and then at 200 visitors, run the harness on both layouts and keep each run going for 20 minutes:

- the section 1 targets hold;
- `/zoo debug perf` reports no visitor stuck for more than 60 seconds;
- closing every zoo at once empties them within the existing time limit;
- a save and restart in the middle of the run loads every zoo intact, with no duplicated payment or stock change. The existing GameTests cover the transactions, and this run checks them under load.

### 9.5 Standalone production jar

Repeat the Phase 1 M4 check with the M12 jar: install a NeoForge 21.1.251 dedicated server from the official installer, with only the ZooCraft and SmartBrainLib jars in `mods`. Create a zoo, open it, watch visitors pay, buy and leave, run `/zoo debug perf`, then restart and confirm the zoo is intact. Record the result in `docs/compatibility.md`.

### 9.6 Acceptance tests (M12)

- JUnit: `LimitRule` produces the documented defaults from the recorded numbers.
- The section 9.1 report and spot check, the section 9.4 runs and the section 9.5 check, recorded in `docs/compatibility.md` and `docs/performance.md`.
- The full GameTest suite passes, and a Phase 2 world loads without data loss.
- CLAUDE.md's code map and build notes are updated (perf package, harness, new debug commands).

## 10. Configuration additions

| Key | Side | Default |
|---|---|---|
| `maxVisitorsPerZoo` / `maxVisitorsTotal` | server | Set by section 9.2 (currently 12 / 40); ceilings 500 / 2,000 |
| `simulation.visitorStepTicks` | server | 10 (current constant) |
| `simulation.viewingIntervalTicks` | server | 20 (current behaviour) |
| `simulation.litterCheckIntervalTicks` | server | 40 (current behaviour) |
| `simulation.pathShortcutMode` | server | `EVERY_NODE` (`EVERY_TICK` = SmartBrainLib's behaviour, `OFF`) |
| `simulation.pathReplanGap` | server | 20 ticks |
| `simulation.farVisitorDistance` / `farVisitorStepMultiplier` | server | 64 / 2 (only if section 7.5 is built) |
| `animals.maxViewingSpots` | server | 12 |
| `animals.viewingSpotCapacity` | server | 2 |
| `animals.maxEnclosureArea` | server | 1,024 |
| `animals.enclosureFillsPerTick` | server | 2 |
| `shops.counterCapacity` | server | 2 |

`zoocraft:natural_floor` is a new block tag (grass, dirt, coarse dirt, podzol, rooted dirt, mud, moss, sand, red sand, gravel, snow, clay, mycelium, `#minecraft:leaves`, optional `#c:` entries).

## 11. Milestones

| Milestone | Scope | Done when |
|---|---|---|
| **M9 Diagnostics and harness** | Section 6: timers, `/zoo debug perf` and its log, the recognition report, `tools/perf/`, the `simulation` config section | M9 acceptance tests pass and the harness reproduces section 3.2 |
| **M10 Crowd movement** | Section 7: cheaper path following, spot, gate and counter occupancy, crowd recovery, where visitors look; section 7.5 only if needed | M10 acceptance tests pass and the section 7.1 target is met |
| **M11 Exhibits and routes** | Section 8: enclosures, viewing spots, full-height search, route selection, exhibit ratings | M11 acceptance tests pass and the coverage target is met |
| **M12 Compatibility and sign-off** | Section 9: ATM10 recognition, limit rule and defaults, performance doc, large-park and multi-zoo runs, standalone jar, CLAUDE.md | Every section 1 criterion is checked and recorded |

Each milestone gets its plan just before it starts and ends with a play-test checklist in `docs/playtests/`.

## 12. Gameplay calls to confirm in play-testing

1. **Crowding is a complaint.** Full exhibits and long counter queues produce comments and advice (7.2, 7.3).
2. **Shops serve two at a time** by default (7.3), like toilets.
3. **Exhibit ratings nudge satisfaction by ±1** only (8.5). They mainly exist as feedback for the player.
4. **Space thresholds are generic:** 12 floor blocks per animal for full marks, with large animals counting double (8.5). No per-species rules until Phase 4.
5. **Route choice favours unseen and less-visited exhibits** more than Phase 1 did (8.3), so visitors walk further in big zoos.
6. **Default limits change** to whatever section 9.2 gives, probably higher than 12 and 40.
7. **Visitors mostly ignore players:** a 10% chance of a 1 to 2 second glance at a player within 6 blocks, then 20 to 40 seconds before the next (7.6).

## 13. Known risks

- **The path-following fix may change how movement looks.** Mitigation: the `EVERY_TICK` mode keeps the old behaviour one config change away, and M10 includes a side-by-side check.
- **Constructing modded entities for the recognition report** may run mod code with side effects (sounds, log spam, or rarely a crash). Mitigation: instances are never added to the world, each is wrapped in `try/catch`, failures are reported, and the command is operator-only and run on demand.
- **Flood fills in odd builds** (no fence, open terrain, huge pens). Mitigation: the area cap, the open-exhibit fallback, the per-tick budget, and the enclosure timer slot to prove the cost.
- **The reference machine is fast.** A 5600X is quicker than many shared hosts. Mitigation: the limit rule halves the measured capacity, and `docs/performance.md` explains how to tune down.
- **Unexplained P99 spikes at 200 visitors.** M9's per-tick timers will show whether ZooCraft causes them. If autosave or vanilla causes them, they are documented rather than fixed.
- **Dev server versus production.** Measurements use the dev runtime. The section 9.5 production-jar check includes a short `/zoo debug perf` run so the two can be compared.
