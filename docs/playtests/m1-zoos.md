# M1 play-test: zoos, boundaries and gates

Run `./gradlew runClient`, create a creative world, and take both tools from the ZooCraft creative tab.

## Drawing a zoo
- [ ] Use the Zoo Boundary Tool on the ground to place corners. Yellow lines join them. You can aim at blocks up to 64 blocks away.
- [ ] Sneak and use the tool to undo the last corner.
- [ ] Use the first corner again: a naming screen opens. Cancel keeps your corners; naming it creates the zoo and the outline turns green.
- [ ] Draw a second, irregular zoo nearby. Try to draw a third that overlaps one of them: you get "This zoo would overlap ...".
- [ ] Try a tiny zoo and a bow-tie shape: each gets its own message.

## Gates
- [ ] Use the Zoo Designation Tool on the ground at the boundary edge: a gate box appears (green). Far from the edge you get a message instead.
- [ ] Sneak and use on the gate to remove it.
- [ ] `/zoo open` fails until the zoo has a gate, then works.

## Editing
- [ ] Use the Boundary Tool inside your zoo: chat shows [Save] and [Cancel]. Move a corner, insert one on an edge (sneak), delete one (sneak on a corner), then click [Save].
- [ ] Move the edge away from a gate and save: you are told the gate is no longer on the boundary, and its box turns grey. Nothing you built is removed.

## Money and people
- [ ] `/zoo deposit 10` with emeralds (and emerald blocks) in your inventory, `/zoo withdraw 5`, `/zoo info`.
- [ ] `/zoo delete` refuses while the treasury has emeralds.
- [ ] `/zoo price 3`, `/zoo list`, `/zoo rename "New Name"`.
- [ ] Save and quit, reopen the world: zoos, gates, treasury and settings are all still there.
- [ ] Two players (`runClient` and `runClient2` against `runServer`): add the second as a manager. They can change the price but not add managers or delete the zoo.
