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

Each visitor costs about 0.05 to 0.10 ms per tick. ZooCraft's censuses, viewing, litter, spawner and dashboard
together stay under 1%.

### M9 rerun with the committed harness (2026-10-15)

Big Park, visitors added with `/zoo debug spawn`, one minute per level:

| Visitors | Avg tick | Worst P95 | Worst P99 | ZooCraft total | Path following | Path planning |
|---|---|---|---|---|---|---|
| 0 | 9.0 ms | 35.0 ms | 45.2 ms | 0.09 ms | 0 | 0 |
| 50 | 11.9 ms | 17.3 ms | 91.7 ms | 3.67 ms | 2.27 ms | |
| 100 | 16.2 ms | 22.4 ms | 27.4 ms | 7.50 ms | 3.71 ms | |
| 200 | 25.8 ms | 47.9 ms | 55.7 ms | 14.82 ms | 7.60 ms | 6.10 ms |

Averages are within 15% of the table above (200 visitors: 13% lower). The worst P99 at 50 visitors came right after
the spawning burst. At 200 visitors, `/zoo debug perf "Big Park"` showed:

- **Path following** 7.9 ms per tick. JFR puts SmartBrainLib's path smoothing (`attemptShortcut`) at 41% of
  server-thread samples, all of it under `VisitorNavigation.tick`.
- **Path planning** 7.0 ms per tick from about 54 new paths a second (1,624 in 30 seconds), about 2.6 ms each. JFR
  attributes only about 2% of samples to `PathFinder.findPath`, so most of that time is spent outside the A* search
  (building the search region) or where JFR doesn't sample. The timers measure elapsed time; JFR samples CPU in Java
  code. Both are worth fixing; M10 measures a single plan in isolation before choosing a fix.
- **16 of 200 visitors stuck** (no progress towards their target for over 10 seconds).
- Everything else ZooCraft does (decisions, censuses, litter, spawner, chunk loading) under 0.4 ms per tick together.

## M10a: path cost (2026-10-15)

Big Park, one minute per level. The no-visitor baseline in these runs was 7.4 to 7.5 ms.

| Build and settings | 100 visitors | 200 visitors | Worst P99 at 200 | ZooCraft at 200 | Path following at 200 |
|---|---|---|---|---|---|
| Shortcuts every tick, follow range 48, off-path cost 1.0 (behaviour before M10a) | 15.7 ms | 23.5 ms | 40.1 ms | 13.9 ms | 8.1 ms |
| Shortcuts every node, range 48, off-path 0.75 | | 18.4 ms | 36.7 ms | 9.1 ms | 0.44 ms |
| Same, range 96 | | 27.1 ms | 75.6 ms | 17.7 ms | 1.1 ms |
| Same, range 128 | | 26.3 ms | 69.8 ms | 17.0 ms | 1.3 ms |
| **Every node, range 48, off-path 0.75, give up on unreachable targets (M10a)** | **10.2 ms** | **13.3 ms** | 34 ms steady (97 ms once, during the spawning burst) | **3.6 ms** | 0.46 ms |

`/zoo debug perf "Big Park"` at 200 visitors:

| | Path plans per second | Fall short | Each | Stuck visitors |
|---|---|---|---|---|
| Before M10a | 45.7 | 87% | 2.3 ms | 17 |
| Every node, range 48 | 63.0 | 90% | 2.4 ms | 18 |
| Range 96 | 49.8 | 82% | 5.8 ms | 8 |
| Range 128 | 41.0 | 73% | 7.0 ms | 10 |
| M10a | 20.3 | 73% | 2.2 ms | 2 |

What made the difference:

1. **Shortcut checks only when a visitor reaches a new path node** (`simulation.pathShortcutMode = EVERY_NODE`). Path following fell from 8.1 to 0.4 ms per tick at 200 visitors, and visitors walk as fast as before.
2. **Giving up on unreachable targets quickly.** The most-missed targets were exhibit viewing spots inside the pens. Visitors re-planned to them for 30 seconds per attempt, each plan using the whole search budget. Two plans in a row that can't reach a target within reach of one search now count as a failed route straight away. Plans fell from about 60 to 20 a second, and stuck visitors from 17 to 2.
3. **Off-path cost 0.75** (`simulation.offPathCost`, was 1.0). Vanilla's path search weighs the remaining distance at 1.5 per block, so a grass step costing 2.0 made searches flood sideways: a 42-block walk across grass couldn't be planned at all. At 0.5 visitors stopped seeking out paths beside them. 0.75 does both.
4. **Follow range stays 48** (`simulation.visitorFollowRange`). 96 and 128 make every failing search dearer and the tick worse, with fewer stuck visitors only because each search covers more ground.

Result: 200 visitors add 5.9 ms per tick (0.03 ms each), inside the spec target of 8 ms, and the steady P99 stays under 35 ms. Exhibit coverage at 200 visitors: 9 of 212 exhibits never viewed (4%; M9: 7%). The remaining most-missed targets are busy shop counters, which M10b's counter occupancy addresses.

## Reading `/zoo debug perf`

- **Server tick**: the whole tick, average and worst over the last 30 seconds. Vanilla's budget is 50 ms.
- **ZooCraft**: time spent in ZooCraft-timed code, counted once. It includes visitors' path following, because
  visitors cause it. The percentage is ZooCraft's share of the tick.
- **Slots**: `visitor path following` (moving along a path, every tick), `visitor path planning` (finding a new
  path), `visitor brain` (SmartBrainLib behaviours, including path planning it starts), `visitor decisions` (the
  visit itself), `animal census`, `facility census`, `visitor spawner`, `dashboard`, `litter`, `chunk loading`.
  Slots can include each other (planning happens inside the brain), so they don't add up to the total.
- **Timers measure elapsed time** on the server thread, so they include anything that stalls it (allocation, GC).
  A profiler such as JFR or spark samples CPU instead; expect the two to differ, and use the timers to compare
  before and after.
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
