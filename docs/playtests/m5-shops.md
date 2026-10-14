# M5 play-test: shops and stock

## Designation tool modes
- [ ] Hold the Zoo Designation Tool, sneak and scroll: the action bar shows "Designation Tool: Gate / Shop / Toilet" and the item name changes. Sneak and use on the sky does the same.
- [ ] Gate mode still places and removes gates exactly as before.

## Shops
- [ ] In Shop mode, use the tool on the ground inside your zoo: "Shop 1 created". A small gold box marks the counter.
- [ ] Use the tool on a barrel and a chest within 6 blocks: each is linked, with gold boxes and lines back to the counter. The chest does not open.
- [ ] Linking a chest 10 blocks away says there's no shop counter within 6 blocks. Linking a chest outside the zoo, or one already linked to another shop, is refused with its own message.
- [ ] A double chest links once; using the tool on its other half says it is already stock.
- [ ] Sneak and use on a linked container unlinks it; sneak and use on the counter removes the shop and leaves the items in the containers.

## Shops tab
- [ ] Put bread, apples, honey bottles, poppies and a filled shulker box in the shop's containers. The Shops tab lists each with its category (Food, Drink, Souvenir, Not for sale), stock and price (bread 2, honey 1, poppies 3).
- [ ] The + and − buttons change a price by 1 (by 10 with Shift); the change shows within a second and survives a restart.
- [ ] Rename the shop and close it; the outline turns red while it is closed.
- [ ] `/zoo debug sell "Shop 1" minecraft:bread` sells one bread: the stock drops by 1 and the treasury and "Shop income" rise by the price. Selling something not in stock records a missed sale, shown in red on the tab.
- [ ] Break a linked chest: within the census interval the alert "A stock container at (x, y, z) was removed" appears and the shop shows one container fewer.

## Toilets
- [ ] In Toilet mode, use the tool on the ground: a blue box marks the toilet, and the Shops tab counts it. Sneak and use removes it.

## Two players
Start `./gradlew runServer`, then `./gradlew runClient -PjoinServer` and `./gradlew runClient2 -PjoinServer`.
- [ ] A player who isn't the owner or a manager can't open or break a shop's stock chest ("This is stock for ...").
- [ ] A manager can open it, restock it, and the owner's Shops tab updates within a second.
- [ ] Both change the same price at the same moment: both dashboards end up showing the same price.
- [ ] A hopper under a stock chest still pulls items out (automation is allowed).

## Quick scripted scene
With a dev server and client running, `python tools/rcon.py` can set one up: `setblock` a barrel with items, then `execute as Dev run zoo debug shop <x y z>`, `zoo debug link <x y z>`, `zoo debug toilet <x y z>` and `zoo debug sell "<shop>" <item>`. `tools/click.ps1 <x> <y>` clicks in the client window at screenshot coordinates.
