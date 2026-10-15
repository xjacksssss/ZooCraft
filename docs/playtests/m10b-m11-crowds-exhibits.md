# M10b and M11 play-test: crowds, exhibits and routes

Dev server and client running (`runServer`, `runClient -PjoinServer`), Dev opped. A zoo with a few fenced pens, a shop,
a toilet and two gates, open, with `maxVisitorsPerZoo` raised to about 40. `/zoo debug spawn "<zoo>"` adds visitors.

## Crowds (spec 7.2 to 7.4)

- [ ] Stand by a pen while many visitors arrive: they spread along its fence, about two per spot, instead of piling onto
      one block. `/zoo debug perf "<zoo>"` shows "most on one viewing spot" at 2 or less.
- [ ] Lower `animals.maxViewingSpots` to 2 with 20 visitors and a single pen: some say "It's too crowded to see
      anything." and move on, and the Overview advice reads "The {species} exhibit is too crowded. Give visitors more
      room to see it."
- [ ] Watch the gate as a group of 3 or 4 arrives: they stand side by side on separate blocks outside it, not on one.
- [ ] Set `shops.counterCapacity` to 1 and make several visitors hungry: one stands at the counter, the others wait a
      couple of blocks away and step up in turn. With the shop kept busy for over 20 seconds, a waiting visitor goes to
      another shop, or says "The queue here was too long." and the advice names the shop.
- [ ] Close the zoo with 30 or more visitors inside and two gates: both gates are used, and everyone has gone within
      about a minute.
- [ ] Push a visitor into a tight group of others with a few more around: it steps aside and carries on, rather than
      standing still for half a minute.

## Where visitors look (spec 7.6)

- [ ] Stand among visitors at an exhibit: they look at the animals, moving their gaze between them, and only glance at
      you now and then (about one short glance per half-minute each).
- [ ] Walking visitors look ahead along their way, or at animals they pass. Group members sometimes look at their leader.
- [ ] Right-click a visitor: it looks at you for about 3 seconds while its info appears.

## Movement

- [ ] Side-by-side movement check: watch a visitor cross a large open area and walk along a winding path with
      `simulation.pathShortcutMode = EVERY_NODE` (the default), then set it to `EVERY_TICK` and watch again. Both cut
      straight across open ground and round corners on paths, at the same speed. Set it back to `EVERY_NODE`.
- [ ] Long walks: send visitors across a big zoo (exhibits 100+ blocks apart). They walk there steadily via the paths,
      without stopping against a pen fence or bending far off the direct line.
- [ ] Rubbish bins on a path: visitors walk round them without snagging.
- [ ] Keeping to paths at `simulation.offPathCost = 0.75`: build a path that runs a few blocks to the side of the direct
      line between two exhibits. Most visitors walk along the path rather than across the grass beside it; with no path
      nearby they cross grass directly. Note how often they leave the path to cut a corner.

## Exhibits (spec 8)

- [ ] A 20 by 20 fenced pen: its viewing spots (use `/zoo debug census` and watch visitors) are just outside the fence,
      round all four sides, and visitors there see animals near that side.
- [ ] The Animals tab shows each exhibit's rating ("Rating: Good (72)") with its warnings.
- [ ] Four cows in a 3 by 3 pen: "The Cow exhibit looks cramped."
- [ ] A pen floored with stone: "The {species} exhibit has bare ground. Natural surroundings would help." Adding a pond
      or some leaves inside lifts the warning at the next census (within about 10 seconds).
- [ ] An animal on a platform 70 blocks up inside the boundary is counted by `/zoo debug census`.
- [ ] Animals roaming open land (no fence): they still get viewing spots round them, and visitors see them.
- [ ] Over a long session in a big zoo, far exhibits get visitors too: `/zoo debug perf "<zoo>"` shows few exhibits
      "not viewed today", and none with many times the median.
