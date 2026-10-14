# ZooCraft Phase 2 Management Loop: Design Specification

**Date:** 2026-10-14
**Status:** Draft for review
**Source of truth:** [zoocraft_game_design_document_v1.0.md](../../../zoocraft_game_design_document_v1.0.md) (the GDD). This spec implements GDD section 11, Phase 2 ("Complete management loop"), and builds on the Phase 1 spec, [2026-10-14-phase1-prototype-design.md](2026-10-14-phase1-prototype-design.md). Section numbers like "P1 7.4" refer to the Phase 1 spec.

## 1. Purpose and success criteria

Phase 2 turns the prototype into a working zoo business. The GDD exit condition:

> The player can operate a functioning zoo business, make meaningful management decisions and improve performance in response to visitor behaviour.

Phase 2 is complete when:

1. A player can designate a shop with the Zoo Designation Tool, link ordinary containers to it as physical stock, and set prices.
2. Visitors buy food, drinks and souvenirs from their own budgets. Each sale removes exactly one item and pays the treasury exactly once. A failed sale changes nothing.
3. Visitors get hungry and need the toilet. Designated toilets that visitors can reach meet that need. Unmet needs produce complaints and early departures.
4. Rubbish bins exist as a dedicated block. Visitors put rubbish in nearby bins or drop physical litter, which players clean up by hand. Litter accumulation is capped.
5. The dashboard has Visitors, Shops and Finances tabs. Complaints are summarised as advice that names the cause and the place ("Riverside Café ran out of bread. 6 visitors left without buying.").
6. Owner and managers share shops, stock, prices and reports consistently. Non-managers cannot take shop stock.
7. Every acceptance test in sections 6 to 10 passes, Phase 1 worlds load without data loss, and the mod runs standalone and in ATM10.

These cover the "Business and facilities" items of the MVP acceptance criteria (GDD section 10).

### Out of scope for Phase 2

Staff, cleaners and wages (Phase 4); breeding and conservation; per-species welfare; rides and tours; unlock trees; a wholesale supplier or automatic running costs (section 4.6); crowd and congestion work and larger visitor limits (Phase 3); individual toilet stalls; shop categories beyond food, drink and souvenirs.

## 2. Working arrangement

Unchanged from Phase 1: Claude writes the code and makes architecture decisions, prefers existing libraries and built-in NeoForge systems, and splits the work into milestones M5 to M8, each with its own plan in `docs/superpowers/plans/` and a manual play-test checklist in `docs/playtests/`. Gameplay calls made in this spec that the user may want to change are listed in section 15.

## 3. Starting point

Phase 1 already provides what Phase 2 builds on:

- `Ledger` with keyed, exactly-once transactions (`DEPOSIT`, `WITHDRAW`, `ADMISSION`).
- Visitors with a profile, an individual budget (already separate from the treasury), satisfaction and interest, saved in entity NBT.
- `VisitorController` with an `EXPLORING` phase that chooses targets, and a never-trapped safety net.
- A periodic per-zoo animal census whose cached results are the only thing visitors read, and reachability learned from real path attempts (P1 8.3).
- The Zoo Designation Tool (gates only), tool outlines, a server-built dashboard snapshot, and comment tallies.

Phase 2 extends these rather than replacing them.

## 4. Key decisions

### 4.1 Shop stock lives in ordinary containers linked to the shop

A shop is a designated **counter** (where customers stand) plus up to 4 **linked containers** nearby. Any block that exposes NeoForge's item handler capability (`Capabilities.ItemHandler.BLOCK`) can be linked: chests, barrels, and modded storage such as Sophisticated Storage or Functional Storage drawers.

Alternatives considered:

- *A dedicated shop block with its own inventory.* Rejected: the GDD says rubbish bins are the only dedicated zoo block.
- *A virtual inventory stored in the zoo record and filled from the dashboard.* Rejected: the GDD requires physical stock, and a virtual inventory would cut out hoppers and modded logistics.

Using the capability means players can restock by hand, with hoppers, or from AE2 and Refined Storage, and ZooCraft needs no knowledge of any storage mod. This resolves the GDD section 13 "Physical shop storage" question.

### 4.2 A sale is one server-thread step: check, take the item, then pay

All checks happen first. The item is then taken from the container. Payment follows, and by that point it cannot fail (section 7.5). Nothing about a sale happens over more than one tick, so nothing can interleave with it.

### 4.3 Litter is a custom lightweight entity

Litter is a `zoocraft:litter` entity, not an item entity and not a block.

- Item entities despawn after five minutes, merge, get vacuumed by hoppers and ATM10 magnets, and end up in player inventories.
- Blocks would mean visitors placing blocks in player-built zoos, with rules about what can be replaced.

A small non-living entity persists, never alters the build, has its own cap and is removed with one hit.

### 4.4 Facilities are cached per zoo, like the animal census

Every census interval, each open zoo builds a **facility census**: shops with a stock summary, toilets, bins with their fill level, and litter. Visitors choose errands from this cache only. The real world is checked once, at the moment of the transaction (taking stock, using a bin). A stale cache can make a visitor walk to a shop that has just sold out, which is realistic and produces a "sold out" record for that shop.

### 4.5 The dashboard sends one tab at a time

The Phase 1 snapshot sends everything. Phase 2 adds four kinds of data (shops with item lists, finance history, visitor statistics, advice), so the client now tells the server which tab it is viewing. The server sends the overview header plus that tab only. This keeps snapshots small and means stock is only read from containers while someone is looking at the Shops tab.

### 4.6 No automatic expenses in Phase 2

GDD 6.4 lists "expenditure on restocking and other supported business costs". Restocking is physical: players stock shops with items they farm, craft or buy from villagers with emeralds they withdraw. Phase 2 does not add a wholesale supplier or running costs. The Finances tab shows withdrawals as money taken out of the business. This is a gameplay call to confirm in play-testing (section 15).

## 5. Architecture

### 5.1 New and changed code

Pure Java, unit-tested with JUnit:

- `core.shop`: `ProductCategory`, `ProductRules` (classify an item from facts: tags, food component, container contents), `PriceRules` (visitor willingness to pay), `Sale` (the exactly-once purchase algorithm over a `StockSource` interface).
- `core.needs`: `Needs` (hunger and toilet growth, thresholds), `VisitPlanner` (picks the next errand: exhibit, shop, toilet, bin, wander, or leave).
- `core.litter`: `LitterHotspots` (grid clustering), `Cleanliness` (rolling score).
- `core.ledger`: new `SHOP_SALE` type, per-type totals, `FinanceHistory` (daily records).
- `core.advice`: `Advice` (turns complaint tallies with subjects into ranked, actionable lines).

Minecraft side:

- `shop` (new): `Shop` and `Toilet` records, `ShopStock` (capability-backed `StockSource`), `FacilityCensus`, `FacilityReach`, `StockProtection` (container access events).
- `litter` (new): `LitterEntity`, `LitterTracker`, `RubbishBinBlock` and its block entity.
- `item`: designation tool modes, `RubbishItem` (what visitors hold after eating).
- `visitor`: needs, errands and purchases added to `VisitorController` through a new `VisitorErrands` helper, so the controller stays a phase machine.
- `zoo`: `Zoo` gains shops, toilets and finance history. `ZooManager` gains shop, toilet and price operations.
- `dashboard`, `network`, `client`: tab-scoped snapshots, new tabs, outlines for shops and toilets, litter highlight.

### 5.2 Data flow

1. Tool clicks and dashboard actions reach `ZooManager` as before. Permissions are checked on the server.
2. The facility census runs on the census timer and stores per-zoo facility data.
3. Visitors pick errands from `VisitPlanner` using that cache, walk there, and at arrival call `ZooManager.sell(...)`, `useToilet(...)` or `useBin(...)`, which recheck the real world and apply the change in one step.
4. Each event updates zoo statistics, finance history and complaint tallies (with a subject, such as a shop ID or a position).
5. Subscribed dashboards receive the tab they are viewing about once a second.

## 6. Zoo Designation Tool modes (M5)

- The tool has three modes: **Gate**, **Shop** and **Toilet**, stored on the item stack as a data component.
- **Sneak and scroll** while holding the tool cycles the mode (client scroll event, sent to the server as a payload). **Sneak and right-click the air** also cycles it, for players whose scroll wheel is taken by another mod. The new mode shows in the action bar and in the item name ("Zoo Designation Tool (Shop)").
- Gate mode behaves exactly as in Phase 1.
- Shop mode:
  - Right-click a walkable block inside a managed zoo to place a **shop counter** there. The shop is named "Shop 1", "Shop 2" and so on, and can be renamed from the dashboard.
  - Right-click a container within `shopLinkRadius` (default 6) blocks of a counter to **link** it to the nearest counter in the same zoo. Linking either half of a double chest links the whole chest once.
  - Sneak and right-click a linked container to unlink it. Sneak and right-click a counter to remove the shop. Stock stays in the containers. Past revenue stays in the finance history.
- Toilet mode: right-click a walkable block to place a **toilet point** (where a visitor stands to use the toilet). Sneak and right-click to remove it. The building around it is up to the player.
- Each failure has its own message, for example "That block can't hold stock.", "There's no shop counter within 6 blocks.", "That container is already stock for Riverside Café.", "This zoo already has 16 shops."
- While a zoo tool is held, outlines show shop counters (gold), lines from counters to their containers, and toilet points (blue). Unreachable or detached ones are drawn red.

## 7. Shops, stock and sales (M5)

### 7.1 Shop record

Stored in the zoo record:

- `id`, `name`, `counter` (block position), `containers` (list of positions, at most `maxContainersPerShop`, default 4), `open` (default true)
- `prices`: item ID to whole emeralds (1 to 999)
- statistics: revenue and units sold, today and in total; units sold today per item; missed sales today (out of stock, too expensive, closed)

A zoo can have at most `maxShopsPerZoo` (default 16) shops. After a boundary edit, a shop whose counter is outside the boundary becomes **detached**, the same as a gate (P1 6.5). It is kept, not deleted, is not used by visitors, and is reported. Toilets follow the same rule.

### 7.2 Products and categories

Every item type in a shop's linked containers is a product. Products are identified by item ID. Data components (names, enchantments) are ignored for pricing, and a sale takes one unit of that item.

`ProductRules` sorts each item into a category, checking in this order:

1. **Not for sale**: items in the tag `zoocraft:unsellable`, spawn eggs, and items whose container or bundle contents are not empty (so their contents can't be lost). The tag ships with command blocks, structure blocks, jigsaw blocks, barriers, light blocks, the debug stick, the knowledge book, bedrock, emeralds, emerald blocks, and ZooCraft's own tools. Not-for-sale items stay in the container and show as "Not for sale" on the dashboard.
2. **Drink**: items in `zoocraft:shop_drinks` that are not in `zoocraft:not_visitor_food`. The drinks tag ships with `#c:drinks` (NeoForge's common tag for honey bottles, milk and modded juices), honey bottles and milk buckets.
3. **Food**: items with the food component (`DataComponents.FOOD`) or in `zoocraft:shop_food`, that are not in `zoocraft:not_visitor_food`. Modded foods, such as Farmer's Delight meals, are recognised automatically.
4. **Souvenir**: anything else.

`zoocraft:not_visitor_food` ships with rotten flesh, spider eyes, poisonous potatoes, pufferfish, chorus fruit, suspicious stew, potions and ominous bottles. These can still be sold, but only as souvenirs.

Food and drink satisfy hunger (section 8.1). Souvenirs satisfy the souvenir wish (section 8.3). An unusual modded item is still sellable as a souvenir even when it means nothing to a visitor's needs (GDD 6.3).

### 7.3 Prices

- When an item first appears in a shop's stock, its price is the category default: food `defaultFoodPrice` (2), drink `defaultDrinkPrice` (1), souvenir `defaultSouvenirPrice` (3).
- Owners and managers change prices on the Shops tab or with `/zoo shop price`. A price applies to the next sale.
- A visitor's highest acceptable price for an item:

  `categoryDefault × profileShopFactor × personalFactor × (0.5 + reputation / 100)`

  `personalFactor` is the visitor's existing random 0.7 to 1.3 factor. `profileShopFactor` is 0.8 for budget-conscious, 1.0 for animal enthusiasts and 1.2 for general visitors. At reputation 50 a general visitor accepts up to about 1.2 times the default price.

### 7.4 Stock reading

- `ShopStock` reads containers through a NeoForge `BlockCapabilityCache` per linked container, so lookups are cheap and follow block changes.
- Containers in unloaded chunks count as empty. The dashboard says "Stock unknown (area not loaded)".
- A container that no longer exposes the capability (broken or replaced) is unlinked automatically the next time the facility census sees it. The shop records the note "A stock container at (x, y, z) was removed."

### 7.5 The sale algorithm

`Sale.attempt(ledger, stock, buyer, key, item, price)` runs on the server thread in a single call:

1. Shop closed, detached or not found: fails with `SHOP_CLOSED`.
2. `ledger.hasKey(key)`: fails with `DUPLICATE`, changing nothing. The sale already happened.
3. Price above the buyer's remaining budget: fails with `CANT_AFFORD`.
4. Simulated extraction of one unit fails: fails with `OUT_OF_STOCK`.
5. Real extraction of one unit. If it returns nothing, fails with `OUT_OF_STOCK`. Nothing has changed.
6. `ledger.apply(SHOP_SALE, key, price)`. It cannot fail at this point: the key is new, the amount is at least 1, and a sale only adds to the balance. If it fails anyway (a programming error), the item is put back into the container, or dropped at the counter if it doesn't fit, and the error is logged.
7. The buyer's budget is reduced by the price, its sale counter goes up, and the shop's statistics are updated.

The key is `sale:<visitId>:<saleIndex>`. `saleIndex` is saved with the visitor, so a sale can never be applied twice for the same visit, even if a visitor reloads from an older save.

Rejected purchases record a **missed sale** on the shop, with the reason (out of stock, too expensive, closed). Shortages and pricing advice come from these records.

### 7.6 Protecting stock

- Only the owner, managers and operators can open, break or extract from a linked container (`PlayerInteractEvent.RightClickBlock` and `BlockEvent.BreakEvent`). Other players see "This is stock for Riverside Café."
- Hoppers, pipes and modded logistics keep working. They are how players automate restocking. This is documented.
- A removed manager loses access immediately.

### 7.7 Acceptance tests (M5)

- JUnit: `ProductRules` sorts each case in 7.2 correctly, including a food with a full container component (not for sale) and a modded food (food).
- JUnit: `Sale` success removes exactly one unit, applies one ledger entry and lowers the budget by the price; a repeated key, out of stock, can't afford and closed each change nothing at all.
- JUnit: price bounds, and willingness to pay per profile and reputation.
- JUnit: the zoo, shop and ledger codecs round-trip, and a **Phase 1 zoo record** decodes with empty shops and toilets.
- GameTest: designate a counter, link a chest, and a test sale removes one item and pays once; running it again with the same key does nothing.
- GameTest: emptying the chest between the census and the sale makes the sale fail with no change to the treasury or the budget.
- GameTest: linking both halves of a double chest links it once.
- GameTest: a non-manager player can't open a linked chest; a manager can.
- GameTest: breaking a linked chest unlinks it at the next census and records the note.
- Manual: stock a barrel with bread and a chest with flowers, set prices on the Shops tab, and watch the stock counts change as test sales happen (`/zoo debug sell`).

## 8. Visitor needs, spending and toilets (M6)

### 8.1 Needs

Each visitor has two needs from 0 to 100, saved with the visitor:

| Need | Starts at | Rises per second (rolled per visitor) | Satisfied by |
|---|---|---|---|
| Hunger | 0 to 30 | 0.14 to 0.25 (hungry after about 4 to 7 minutes) | Food lowers it by 60; a drink lowers it by 35 |
| Toilet | 0 to 20 | 0.11 to 0.2 (needs a toilet after about 5 to 9 minutes) | Using a toilet resets it to 0 |

- At `needThreshold` (default 60) the need becomes an errand (section 8.2).
- At 85 the need is **urgent**. The visitor says so once ("I really need a toilet!") and loses 1 satisfaction every 10 seconds until the need is met.
- At 100 for 60 seconds, the visitor leaves early and records the complaint for that need ("There's nowhere to get something to eat." or "I couldn't find a toilet.").
- Needs only rise while the visitor is admitted. They never affect a visitor that is arriving, queueing or walking away.

### 8.2 Choosing errands

`VisitPlanner` replaces the plain exhibit choice whenever the visitor needs a new target. Priorities:

1. Toilet, when the need is over the threshold and a reachable toilet exists.
2. Food or drink, when hungry and a reachable, open shop has food or drink in stock that the visitor would accept and can afford.
3. Rubbish in hand: go to a bin (section 9.2).
4. Exhibits, as in P1 8.5.
5. When the visit is ending of the visitor's own accord (interest, time or budget), a **last stop** at a souvenir shop if the visitor has a souvenir wish (section 8.3). Then leave.

If a need is over the threshold but no suitable facility exists in the cache, the visitor remembers that and says the complaint once ("There's nowhere to get something to eat.", with the subject "no shop with food"). A visitor that is hungry but can afford nothing records "I can't afford anything here." This is counted separately: it means prices or admission are too high, not that food is missing.

Closing the zoo, the safety net and boundary releases always override errands. A visitor on an errand still leaves when told to.

### 8.3 Spending

- Budgets move from the profile enum into config, and rise to make room for shopping: budget-conscious 4 to 12, enthusiast 6 to 18, general 5 to 15.
- At admission, each visitor rolls a **souvenir wish**: budget-conscious 10%, general 35%, animal enthusiast 25% (35% once it has seen 5 or more species).
- Choosing a product: among in-stock items of the wanted category that the visitor accepts and can afford, it picks at random, weighted towards cheaper items for budget-conscious visitors.
- If the cached shop offered something but the real attempt fails, the visitor reacts to the reason: out of stock ("They've sold out!"), too expensive ("That's far too expensive."), closed. Each records a missed sale on the shop and the complaint with the shop as its subject. The visitor may try one other shop before giving up on that errand.
- A visitor whose remaining budget can't cover the cheapest product in any category it might want ends its visit after its current activity (P1 7.6 "budget can't cover anything more", now meaningful).
- Bought items are consumed or taken home and never dropped. A visitor holds its purchase in its crossed arms (already rendered by `CrossedArmsItemLayer`): it eats food or drinks for 8 seconds with sounds and particles, then holds **rubbish** (section 9.2). Souvenirs are carried for 10 seconds and then stowed.
- Satisfaction effects: a satisfying meal +4 ("That was delicious!"), a souvenir +5, a sold-out shop -4, too expensive -3 (-5 for budget-conscious visitors).

### 8.4 Toilets

- A toilet point is usable when it is inside the boundary, not detached, and not marked unreachable.
- Each toilet point serves up to `toiletCapacity` (default 2) visitors at once. A visitor uses it for 6 seconds, standing at the point.
- If the toilet is full when the visitor arrives, it waits nearby for up to 20 seconds, then tries another toilet or records "The queue for the toilet is too long." with the toilet as subject.
- An isolated toilet that visitors can't reach becomes unreachable after 3 failed routes, using the same learned reachability as exhibits (P1 8.3), and records "We couldn't get to the toilets." Designation alone never meets a need.

### 8.5 Reachability for facilities

Shops, toilets and bins use `FacilityReach`, the same rule as exhibits: 3 failed routes mark a facility unreachable for 60 seconds, and the dashboard names it. A successful visit clears the mark.

### 8.6 Acceptance tests (M6)

- JUnit: needs growth, thresholds, urgent and leave-early timing.
- JUnit: `VisitPlanner` priorities, including "no facility", "can't afford anything", the souvenir last stop, and errands being ignored once leaving has begun.
- JUnit: product choice respects budget, acceptance and category.
- GameTest: a hungry visitor walks to a stocked food shop, buys once, the item leaves the chest, the treasury rises by the price, and hunger drops.
- GameTest: a hungry visitor in a zoo with no food shop records the complaint once and does not buy.
- GameTest: a toilet walled off from the paths is never used and is reported unreachable; an open one is used and the need resets.
- GameTest: with capacity 1, a second visitor waits and then complains or uses another toilet.
- GameTest: closing the zoo while a visitor walks to a shop makes it leave without buying; nothing is charged.
- GameTest: a visitor leaving of its own accord with a souvenir wish buys a souvenir on the way out.
- Manual: a zoo with a café and toilets, watched for 15 minutes: visitors eat, queue, complain when the café is emptied, and recover when it is restocked.

## 9. Bins, litter and cleanliness (M7)

### 9.1 Rubbish bin block

- `zoocraft:rubbish_bin` is a crafted block with a block entity holding its fill level (0 to `binCapacity`, default 32). The model shows four fill stages.
- Bins inside a zoo's boundary belong to that zoo. The facility census finds them by scanning the block entities of the zoo's loaded chunks, so no registration bookkeeping is needed.
- Anyone can empty a bin with an empty-hand right-click. Emptying gives nothing back.
- A full bin is not used. The dashboard says "The bin at (x, z) is full."
- Bins can be decorated around or built into structures freely. They are the only dedicated zoo block.

### 9.2 Rubbish and littering

- After eating or drinking, a visitor holds a `zoocraft:rubbish` item (a wrapper, cup or paper texture, picked at random).
- It looks for a cached, usable, non-full bin within `binSearchRadius` (default 16) blocks and walks to it. At arrival the bin's fill goes up by 1.
- If there is no usable bin, the visitor carries the rubbish for up to 30 seconds, using any bin that comes in range. Otherwise it drops litter where it stands with chance `litterChanceWithoutBin` (default 0.75), or pockets it.
- A visitor at a full bin drops the litter next to it.
- Litter is only dropped inside the boundary, never outside it or in fluids.

### 9.3 Litter entity

- `zoocraft:litter` is a small non-living entity with gravity, no AI and no despawn. It is rendered as its rubbish item lying flat. It has a **pile size** from 1 to 4.
- Any player removes a piece of litter by hitting it, with a sound. The zoo counts litter collected.
- For players holding a zoo tool, litter glows through walls on their client only (the client config `highlightLitter`, default true).
- Caps: at most `maxLitterPerZoo` (default 48) litter entities per zoo and `maxLitterTotal` (default 300) per server. At the cap, new litter makes the nearest piece within 8 blocks bigger (up to pile size 4). If no piece is in range, the drop doesn't happen.
- `LitterTracker` keeps a per-zoo list of loaded litter entities, the same way `VisitorTracker` tracks visitors, so nothing searches the world for litter.
- Litter left outside the boundary after an edit no longer belongs to the zoo. It stays in the world until someone picks it up.

### 9.4 Cleanliness

- Every 2 seconds, each admitted visitor counts litter units (pile sizes added together) within 8 blocks, using the tracker list.
- 3 or more units nearby: satisfaction -2 and the visitor's "litter seen" counter goes up. At 3, it says "Why is there rubbish everywhere?" once. The complaint's subject is the 16 by 16 block area it is standing in.
- The zoo's **cleanliness** (0 to 100) is a rolling average of what visitors see, `clean += 0.05 × (sample − clean)`, where a sample is 100 for no litter nearby, falling by 20 per unit. It changes gradually like satisfaction. It is shown on the Overview tab as Clean, A little litter or Dirty.
- `LitterHotspots` groups the zoo's litter into 16 by 16 areas. Areas with 3 or more units become advice: "Litter is piling up near (x, z). Clear it up or add a bin nearby."
- Cleanliness reaches reputation and future arrivals through visitor satisfaction, as GDD 7.4 asks.

### 9.5 Acceptance tests (M7)

- JUnit: hotspot clustering; cleanliness moves gradually and recovers; the pile and cap rules.
- GameTest: a visitor with rubbish and a bin in range uses the bin, and the fill goes up by 1.
- GameTest: with no bin, litter appears inside the boundary.
- GameTest: 100 forced drops never exceed the per-zoo cap.
- GameTest: a full bin is not used and raises the alert.
- GameTest: hitting litter removes it and the collected count goes up.
- GameTest: litter survives a save and reload, and the tracker counts it again afterwards.
- Manual: run a busy café with no bins, watch litter collect and the complaint and hotspot appear, add bins and clean up, and watch cleanliness recover.

## 10. Reports, complaints, dashboard and multiplayer (M8)

### 10.1 Finance history

- The ledger gains per-type totals, including `SHOP_SALE`.
- `FinanceHistory` keeps one record per in-game day for the last 28 days: admission income, shop income, deposits, withdrawals, closing balance, visitors, refusals, average departing satisfaction, and total visitor spending.
- Each shop keeps per-day revenue for the same 28 days.
- The day rolls over when the overworld day number changes, the same as the Phase 1 attendance counters.

### 10.2 Dashboard tabs

| Tab | Contents |
|---|---|
| **Overview** | Phase 1 overview plus shop income today, cleanliness, recommendation rate, and the top 5 advice lines |
| **Visitors** | Arrivals today, refusals, average visit length, average spending per visitor (admission and shops), profile mix, needs met and unmet (meals bought, hungry with nowhere to eat, toilet visits, couldn't find a toilet), complaints with counts |
| **Animals** | Unchanged from Phase 1 |
| **Shops** | One line per shop: name, status (open, closed, sold out, unreachable, detached), revenue today. Selecting a shop shows each product with its icon, category, stock count, price (with − and + buttons and a typed value), sold today, and missed sales. Rename and open/close buttons. Toilets and bins are listed underneath with their status and fill. |
| **Finances** | Treasury, today's income split into admission and shops, a bar chart of daily income for the last 14 days, revenue per shop, deposits and withdrawals, and recent transactions |
| **Settings** | Unchanged from Phase 1 |

- The dashboard screen is widened to fit six tabs. A tab that doesn't fit lists its items with scrolling.
- The **recommendation rate** is the share of the last 100 departing visitors that left with satisfaction 70 or more. It is shown as "7 in 10 visitors would recommend your zoo."

### 10.3 Actionable complaints

Every complaint is recorded with a type and an optional subject (a shop ID, toilet ID, bin position or 16 by 16 area). `Advice` turns today's tallies into ranked lines that name the cause and the fix:

| Complaint | Advice line |
|---|---|
| No food | "Hungry visitors couldn't find anything to eat. Stock a shop with food or drink." |
| Sold out (shop) | "{shop} ran out of {item}. {n} visitors left without buying." |
| Too expensive (shop) | "Visitors thought {shop}'s {item} was too expensive at {price}." |
| Can't afford anything | "{n} visitors ran out of emeralds before they could eat. Lower your prices or admission." |
| No toilet | "Visitors couldn't find a toilet. Designate one they can reach." |
| Toilet queue (toilet) | "The toilet at (x, z) is too busy. Add another one nearby." |
| Unreachable (shop or toilet) | "Visitors can't reach {shop / the toilet at (x, z)}." |
| Rubbish (area) | "Litter is piling up near (x, z). Clear it up or add a bin nearby." |
| Bin full | "The bin at (x, z) is full. Empty it." |
| Phase 1 complaints | Reworded as advice, for example "Visitors can't get close enough to see the {species}." |

Lines are ranked by count multiplied by a severity weight, and the top 5 are shown on the Overview tab. The Visitors tab shows them all. Advice is rebuilt from tallies, so it disappears once the problem stops happening.

### 10.4 Multiplayer and consistency

- Shop, toilet and price operations need the existing "manage shops and facilities" permission (owner and managers, GDD 3.4). Viewing reports needs dashboard access.
- All changes go through `ZooManager` on the server thread. Two managers changing the same price both see the final value within about a second.
- A manager restocking a container shows on the other manager's Shops tab at the next refresh.
- Visitor sales and player actions never run in parallel, because both happen on the server thread.

### 10.5 Commands

Each command arrives with the milestone that needs it (the shop commands and `/zoo debug sell` in M5, needs in M6, litter in M7).

`/zoo shop list <zoo>`, `/zoo shop price <zoo> <shop> <item> <emeralds>`, `/zoo shop open|close <zoo> <shop>`, `/zoo shop rename <zoo> <shop> <name>`. Debug commands: `/zoo debug facilities`, `/zoo debug sell <shop> <item>`, `/zoo debug needs <hunger> <toilet>` (sets needs for visitors in the zoo), `/zoo debug litter <count>`.

### 10.6 Acceptance tests (M8)

- JUnit: `FinanceHistory` day rollover, the 28-day cap, and totals that agree with the ledger.
- JUnit: `Advice` ranking and wording arguments for each complaint in 10.3.
- JUnit: the recommendation rate over a rolling window.
- GameTest: the snapshot for each tab contains that tab's data and no other tab's.
- Manual (dev server and two clients): the owner and a manager both change one price at the same moment, and both see the same final price. The manager restocks, and the owner's stock count updates. The owner removes the manager, who can no longer open a stock chest.
- Manual: save and quit with visitors mid-purchase; after reloading, no sale is charged twice and the stock matches.
- Manual: a world saved by Phase 1 loads with its zoos, treasury and statistics intact.
- Manual (ATM10 copy): Farmer's Delight foods are classed as food; a Sophisticated Storage chest and a Functional Storage drawer link as stock; an AE2 import bus can restock a linked chest; magnets don't pick up litter; the full loop works. Results go in `docs/compatibility.md`.

## 11. Persistence and compatibility

- New zoo fields (shops, toilets, finance history, per-type totals, advice tallies with subjects, cleanliness, recommendation window) use `optionalFieldOf` with empty defaults, so Phase 1 saves load unchanged. A JUnit fixture holds a Phase 1-encoded zoo.
- New visitor NBT fields (needs, need rates, sale index, souvenir wish, held item state) default sensibly when missing, so visitors saved by Phase 1 carry on.
- Bin fill is saved in its block entity. Litter is saved by Minecraft with its chunk.
- `zoocraft:unsellable`, `zoocraft:shop_drinks`, `zoocraft:not_visitor_food` and `zoocraft:shop_food` (forces an item to count as food) are item tags modpacks can extend. Optional entries cover known modded items, so a missing mod does no harm.

## 12. Configuration additions

| Key | Side | Default |
|---|---|---|
| `maxShopsPerZoo` | server | 16 |
| `maxContainersPerShop` | server | 4 |
| `shopLinkRadius` | server | 6 |
| `defaultFoodPrice` / `defaultDrinkPrice` / `defaultSouvenirPrice` | server | 2 / 1 / 3 |
| `budgetConsciousBudget` / `animalEnthusiastBudget` / `generalBudget` (min and max) | server | 4–12 / 6–18 / 5–15 |
| `needThreshold` | server | 60 |
| `toiletCapacity` | server | 2 |
| `binCapacity` | server | 32 |
| `binSearchRadius` | server | 16 |
| `litterChanceWithoutBin` | server | 0.75 |
| `maxLitterPerZoo` | server | 48 |
| `maxLitterTotal` | server | 300 |
| `highlightLitter` | client | true |

## 13. Milestones

Each milestone gets its own plan in `docs/superpowers/plans/`, written just before it starts, and ends with a play-test checklist.

| Milestone | Scope | Done when |
|---|---|---|
| **M5 Shops and stock** | Section 6 (tool modes), section 7, tab-scoped snapshots (4.5) and the Shops tab | M5 acceptance tests pass |
| **M6 Needs, spending and toilets** | Section 8 | M6 acceptance tests pass |
| **M7 Bins, litter and cleanliness** | Section 9 | M7 acceptance tests pass |
| **M8 Reports, complaints and multiplayer** | Section 10, section 11 migration checks, ATM10 run, compatibility notes, CLAUDE.md code map | M8 acceptance tests pass, then the Phase 2 exit condition in section 1 is checked in a play-test |

## 14. GDD section 13 questions resolved for Phase 2

| Question | Resolution |
|---|---|
| Physical shop storage | Ordinary containers linked to a designated counter through the item handler capability (4.1, 7.1). |
| Ownership and treasury protection | Shop stock is protected from non-managers (7.6). Removing a shop keeps its stock in the containers and its revenue in the history. |

## 15. Gameplay calls to confirm in play-testing

These are design choices this spec makes. They are easy to change later and are listed so the user can overrule them:

1. **No automatic expenses.** Restocking is physical, and there is no wholesale supplier or running cost (4.6).
2. **Two needs, hunger and toilet.** Food and drink both satisfy hunger; there is no separate thirst need.
3. **Bins fill up** (32 pieces) and need emptying by hand, so bins don't make litter go away for free (GDD 7.4).
4. **Anyone can pick up litter** by hitting it, but only owners and managers can take shop stock.
5. **Only food and drink create litter.** Souvenirs don't.
6. **Bought items disappear**: they are eaten or taken home, never dropped.
7. **Higher default budgets** to make room for shopping (8.3). Admission stays at 2 by default.

## 16. Known risks

- **Stale facility cache.** Visitors may walk to a shop that has just sold out. This is acceptable and produces useful shortage data. The real check at sale time keeps it correct.
- **Modded containers with unusual handlers.** Some storage blocks may report stock lazily or refuse simulated extraction. Mitigation: the sale treats a failed simulated extraction as out of stock, and the ATM10 check covers common storage mods. Gaps are documented.
- **Crash between saves.** A server crash between the zoo save and the visitor's entity save could roll a container back separately from the ledger. Sale keys stop a double charge. Exact item counts across a crash are best effort, the same as vanilla trading. This is documented.
- **Visitor errands adding path load.** Errands add walking targets, not extra path searches per tick. The step timers and caps stay as in Phase 1 and are measured in M6.
- **Dashboard size.** Six tabs and per-item price editing in vanilla widgets. Mitigation: tab-scoped snapshots and scrolling lists. Revisit a UI library only if vanilla widgets become unmanageable.
