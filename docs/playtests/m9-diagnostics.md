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
