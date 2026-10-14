# M3 play-test: animals and viewing

Use the zoo from M2 and add two or three pens with different animals (pigs, sheep, a pond with fish or axolotls), with paths running past them. Some pens fenced, one walled in stone, one behind glass.

- [ ] `/zoo open`. Visitors walk to the pens rather than wandering randomly, linger, look at the animals, then move on.
- [ ] Seeing a new species: "That was an amazing exhibit!" above their heads. With five or more species they sometimes say "This place has so many different animals!"
- [ ] Animals behind the stone wall don't count (no reaction from just walking past); animals behind glass do.
- [ ] A pen in the middle of the zoo with no way to get close: visitors who try say "We couldn't get close enough to see the animals." and stop choosing it for a while.
- [ ] A zoo with no animals: visitors say "There aren't any animals to see." and leave sooner.
- [ ] Baby animals and colour variants don't add extra species (check `/zoo info` once the dashboard exists, or the visitor's reaction count).
- [ ] Villagers, iron golems and hostile mobs inside the zoo are never treated as animals.
- [ ] Over several visits, `/zoo info` satisfaction drifts up for a zoo with varied, visible animals and down for an empty or unreachable one, never jumping.
