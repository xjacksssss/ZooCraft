# M12 play-test: compatibility, limits and sign-off

## Zoo animals are kept (no despawning, no culling)

Dev server and client, a zoo with a pond and a pen.

- [ ] Put cod, salmon and tropical fish (spawn eggs, not buckets, so they would normally despawn) in a fenced pond
      inside the zoo, and a few squid. Fly more than 128 blocks away for a minute, come back: all are still there.
- [ ] Do the same outside any zoo: fish there despawn as usual.
- [ ] Set the difficulty to peaceful with a tagged-in hostile creature inside the zoo (`zoocraft:exhibit_animals`):
      it stays.
- [ ] Walk far enough for the zoo's chunks to unload, then come back: the animals are where they were, and the
      dashboard's animal counts match.
- [ ] Pack 30 sheep into a 2 by 2 pen inside the zoo with `maxEntityCramming` at its default: none take cramming
      damage. The same pen outside the zoo loses sheep.

## ATM10 recognition (automated)

- [ ] `tools/atm10/recognition-audit.sh` runs without anyone at the keyboard, closes the game itself and prints
      PASSED. (Don't open the pause menu while it runs: a paused single-player world stops ticking.)
- [ ] Spot check in an ATM10 zoo: a Twilight Forest deer, an Aquaculture fish in a pond, a Productive Bees bee and a
      Living Things elephant appear on the dashboard's animal list by name; a MineColonies citizen, an Ice and Fire
      dragon and an Occultism familiar in the zoo don't.

## Visitor limits

- [ ] A new server's `zoocraft-server.toml` has `maxVisitorsPerZoo = 68` and `maxVisitorsTotal = 140`, and the
      comments point to `docs/performance.md`.

## Hill Park (terraced reference park)

Perf world, `tools/perf/build_complex_park.py` (see `tools/perf/README.md`), 100 visitors.

- [ ] Visitors climb the ramps onto the terraces and come down again, and cross the boardwalk over the pond.
- [ ] A visitor on a terrace heading for a far exhibit walks along the terrace path to the ramp, not to the cliff edge
      above its target. `/zoo debug perf "Hill Park"` shows few paths falling short of a nearby target (under 10%).
- [ ] Visitors stand on the pond's bank, round its fence, and the pond exhibit gets views.
- [ ] Watch a gate on the far side of the park from where you stand (outside simulation distance): visitors leaving
      by it are gone within about 5 seconds of their chunk no longer ticking, and the zoo's visitor count drops.
