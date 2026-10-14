# M2 play-test: visitors

Set up a zoo as in the M1 play-test, with two gates on different sides and a few paths (dirt path or gravel) inside.

- [ ] `/zoo price 2`, `/zoo open`. Within a minute or two, villagers appear 16 to 32 blocks outside a gate, alone or in small groups, and walk to it.
- [ ] They wait at the gate, then walk in. `/zoo info` shows attendance and admission income going up by the price per visitor.
- [ ] Right-click a visitor: chat shows its profile, emeralds left, mood and what it is doing.
- [ ] Visitors prefer your paths but cut across grass when needed. They open doors and fence gates and close them behind them.
- [ ] Raise the price to 30: arriving groups say "The ticket was expensive for what we got." and walk away; nobody pays.
- [ ] `/zoo close`: visitors say they're leaving, walk out through the nearest gate and fade away a little way outside.
- [ ] Wall off one gate completely while visitors are inside, then close the zoo: they use the other gate. Wall off both: within about a minute they give up, say "I couldn't find the way out." and fade out.
- [ ] Shrink the boundary so some visitors are left outside it: they head off instead of staying.
- [ ] `/zoo keeploaded true`, fly far away for a few minutes, come back: attendance kept rising.
- [ ] Save and quit with visitors inside, reopen: they carry on; nobody pays twice.
