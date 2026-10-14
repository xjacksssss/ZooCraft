# M4 play-test: dashboard and two players

## Dashboard
- [ ] Press `;` (or type `/zoo`) inside your zoo: the dashboard opens. Outside any zoo with one zoo: it opens that one. With several: a picker lists them.
- [ ] Overview shows visitors now, today and all time, people turned away, treasury, admission income, satisfaction and reputation bars, gates, alerts and the most common comments.
- [ ] Animals lists each species with a count and each exhibit with how often visitors have seen it. A pen nobody can get close to shows "Visitors can't get close enough to see these animals."
- [ ] Settings: open and close the zoo, set the price, deposit and withdraw emeralds, switch "Keep loaded". The numbers update within a second.

## Two players
Start `./gradlew runServer`, then `./gradlew runClient -PjoinServer` and `./gradlew runClient2 -PjoinServer` (the second player is called "Manager"). Make the first player op with the server console if you want to use debug commands.
- [ ] The owner adds "Manager" from the Settings tab. Both dashboards show the same treasury and visitor numbers, updating together.
- [ ] Manager can open and close the zoo, set the price and move emeralds, but sees no manager or transfer controls.
- [ ] Both try to withdraw the whole treasury at once: only one gets it.
- [ ] The owner logs out: the zoo keeps running for the manager, and admissions continue.
- [ ] The owner removes the manager: the manager's dashboard closes with a message.
- [ ] Stop and restart the server: everything is still there.

## Quick scripted scene
With a dev server and client running (`runServer` and `runClient -PjoinServer`), `python tools/rcon.py "op Dev" 'execute as Dev run zoo debug create "Test Zoo" 10 -60 -20 50 -60 20' "execute as Dev run zoo debug gate 10 -60 0" 'execute as Dev run zoo open "Test Zoo"'`, then summon some animals inside and use `/zoo debug spawn`, `/zoo debug visitors` and `/zoo debug census` to watch.
