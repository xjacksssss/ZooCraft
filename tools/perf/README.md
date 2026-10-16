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

The terraced park: close the other zoos, force-load `768 -32 991 191` (the park plus two chunks round it, like the other layouts), then `python build_complex_park.py "Hill Park" 800 0`.
It is 160 by 160 blocks with terraces at +3, +4 and +6, a sunken pond with fish under a railed boardwalk, one winding
loop path with ramps and three gates, and 27 pens of 7 to 15 blocks spaced along the path on every level.

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

## Exhibit coverage

```bash
python coverage.py cov "Big Park" 100 20 --sprint
```

Spawns 100 visitors, then runs 20 minutes and compares each exhibit's view count before and after (from
`/zoo debug perf exhibits`, which writes `logs/zoocraft-exhibits.csv`). It reports the share of exhibits viewed during the
run, the median and maximum views, their ratio (the spec's target is at most 4) and the longest time any visitor was stuck.
Kill leftover visitors first (`kill @e[type=zoocraft:visitor]`): lowering the limit doesn't remove them.

`--sprint` runs the 20 minutes of game time with `/tick sprint`, a game minute at a time, so it finishes in a few real
minutes. Views and stuck times count game time, so the results mean the same; don't read tick timings from such a run.
