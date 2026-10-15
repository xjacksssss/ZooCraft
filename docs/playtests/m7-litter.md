# M7 play-test: bins, litter and cleanliness

Start with a zoo that has a stocked café. `/zoo debug needs 75 0` makes every visitor hungry, so rubbish appears quickly.

## Bins
- [ ] Craft a Rubbish Bin (iron ingots in a U with green dye in the middle) and place it near the café.
- [ ] Visitors who finish eating carry a wrapper, cup or scrap of paper to the bin. The bin's model fills up in stages.
- [ ] A full bin (32 pieces) is ignored, and the Overview says "The bin at (x, z) is full. Empty it."
- [ ] Use the bin with an empty hand: it empties and the message says how much was thrown away. Visitors use it again.

## Litter
- [ ] Remove the bins. After eating and carrying rubbish for about 30 seconds, most visitors drop it on the ground; some take it home.
- [ ] Hold a zoo tool: litter glows through walls. Put the tool away and the glow stops (client config `highlightLitter`).
- [ ] Hit or use a piece of litter: it disappears with a sound, and "picked up" on the Overview goes up.
- [ ] Litter never appears outside the boundary or in water, and is never picked up by hoppers or magnets.
- [ ] With lots of visitors and no bins, the litter count stops at the cap (48 per zoo by default) and nearby pieces pile up instead (up to 4 in a pile).

## Cleanliness and complaints
- [ ] With litter about, the Cleanliness bar on the Overview falls and its label changes from Clean to A little litter to Dirty.
- [ ] Visitors walking through litter lose satisfaction, and after a few sightings say "Why is there rubbish everywhere?" once each.
- [ ] The Overview shows "Litter is piling up near (x, z). Clear it up or add a bin nearby." for the worst spots.
- [ ] Clear the litter and add bins: cleanliness climbs back over the next few minutes.

## Saving
- [ ] Save and reload: litter stays where it was with the same pile sizes, bins keep their fill, and cleanliness is unchanged.
