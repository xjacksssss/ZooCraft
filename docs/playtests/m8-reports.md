# M8 play-test: reports, advice and two players

## Advice and the Overview
- [ ] After visitors have been around for a while, the Overview's right column shows "Needs attention" (things wrong right now, such as full bins and litter hotspots) and "Advice" (the top 5 problems from today, worded as what to do).
- [ ] Empty a stocked café while visitors are hungry: within a minute the advice says "Shop 1 ran out of Bread. N visitors left without buying." Restock it, and once the problem stops happening the line drops down the list on the next in-game day.
- [ ] Price bread at 9: the advice says visitors thought it was too expensive, with the price.
- [ ] Remove every toilet while visitors need one: "Visitors couldn't find a toilet" appears.
- [ ] The left column says "Would recommend: N in 10" once visitors have left, or "Nobody has left yet".

## Visitors tab
- [ ] Shows arrivals and refusals today, how many left and how long they stayed, average spending, the profile mix, meals bought against hungry visitors who found no food, and toilet visits against visitors who found no toilet.
- [ ] Lists everything visitors said today, with counts, and every advice line.

## Finances tab
- [ ] Shows the treasury, today's admission and shop income, deposits and withdrawals.
- [ ] The income chart shows one bar per in-game day (admission in green, shops in gold), up to 14 days.
- [ ] Revenue by shop shows today, the last 7 days and all time for each shop.
- [ ] Recent transactions list admissions, sales, deposits and withdrawals.
- [ ] Sleep through a night or two: yesterday's bar stays, today's starts again from nothing.

## Two players
Start `./gradlew runServer`, then `./gradlew runClient -PjoinServer` and `./gradlew runClient2 -PjoinServer` (the second player is Manager).
- [ ] Both open the Shops tab. One presses + on a price: the other sees the new price within a second.
- [ ] Manager restocks a stock chest: the owner's stock count updates.
- [ ] The owner removes Manager: Manager's dashboard closes, Manager can no longer open the stock chest, and Manager's shop commands are refused.
- [ ] Stop and restart the server while visitors are buying: the treasury, shop takings, history, toilets, bins and litter are all as they were, and nobody is charged twice.

## Upgrading a Phase 1 world
- [ ] Open a world saved before Phase 2: its zoos, treasury and statistics are intact, and shops, toilets and reports can be added and survive a restart.
