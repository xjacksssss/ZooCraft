# ZooCraft Phase 1 Prototype: Design Specification

**Date:** 2026-10-14
**Status:** Draft for review
**Source of truth:** [zoocraft_game_design_document_v1.0.md](../../../zoocraft_game_design_document_v1.0.md) (the GDD). This spec implements GDD section 14 (first milestone) and is the Phase 1 feature specification requested in GDD section 15.

## 1. Purpose and success criteria

Build a tiny but fully playable zoo prototype that answers one question (GDD section 14):

> Is it enjoyable to watch a zoo you built come alive, with villagers reacting to the things you've placed in it?

The prototype is complete when:

1. A player can draw a custom-shaped zoo boundary and place at least two gates.
2. The zoo can be opened and closed, with an emerald admission price.
3. Villager visitors arrive, pay, walk to animals and leave.
4. Animals are recognised automatically and count as viewed when visitors get close.
5. A small dashboard shows attendance, income and visitor feedback.
6. Two players can manage the same zoo and see the same state.

Every acceptance test in sections 6 to 10 passes, and the mod loads both standalone and in a copy of ATM10.

### Out of scope for this prototype

These wait for later phases (GDD section 11): shops, stock, toilets, rubbish bins, litter, staff and wages, breeding and conservation, per-species welfare (cramped, natural surroundings), rides and tours, unlock trees, simulated offline income, the Visitors and Finances dashboard tabs.

## 2. Working arrangement

- Claude writes the code and makes the architectural decisions. The user reviews, play-tests and makes gameplay decisions.
- Prefer existing libraries and built-in NeoForge/vanilla systems over custom code.
- Work is split into milestones M0 to M4. Each milestone gets its own implementation plan and ends with a manual play-test the user can run.

## 3. Platform and environment

| Item | Decision |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.251 (the version used by ATM10 8.2) |
| Java | JDK 21 (must be installed; the machine currently has JDK 17) |
| Build | ModDevGradle with Parchment mappings |
| Project location | `C:\dev\ZooCraft`, a git repository, outside OneDrive |
| Mod ID | `zoocraft` |
| Base package | `com.zoocraft` |

## 4. Libraries

| Need | Choice | Notes |
|---|---|---|
| Visitor AI | **SmartBrainLib** (NeoForge 1.21.1) | Required dependency. Already in ATM10, so no added conflict risk there. |
| Boundary geometry | **JTS (Java Topology Suite)** | Bundled inside the mod jar with NeoForge jar-in-jar. Used for validity, overlap and point-in-polygon. |
| Persistence | Vanilla `SavedData` with Codecs | Built in. |
| Networking | NeoForge payload registration (`CustomPacketPayload`, `StreamCodec`) | Built in. |
| Config | NeoForge `ModConfigSpec` and its built-in config screen | Built in. |
| Chunk loading | NeoForge `TicketController` | Built in. |
| Species grouping | NeoForge data maps | Built in. |
| Pathfinding | Vanilla `PathNavigation` with a custom node evaluator | Built in. |
| Commands | Brigadier | Built in. |
| UI | Vanilla `Screen` widgets | No UI framework. owo-lib's NeoForge port is beta. |
| Rendering | Vanilla villager model and textures | GeckoLib is available in ATM10 but not needed yet. |
| Tests | JUnit 5 and NeoForge GameTests | |

Optional integrations later, never required: Jade (visitor tooltip).

## 5. Architecture overview

The zoo is a server-side data object, separate from the blocks inside it. All state changes happen on the server thread. Clients only display snapshots and send requests.

### 5.1 Code layout

Pure Java packages (no Minecraft classes, unit-tested with JUnit):

- `com.zoocraft.core.geometry`: boundary polygon, validation, overlap, containment (wraps JTS).
- `com.zoocraft.core.ledger`: treasury and transaction ledger with exactly-once rules.
- `com.zoocraft.core.rating`: satisfaction and reputation maths.
- `com.zoocraft.core.exhibit`: grouping animal positions into exhibits.
- `com.zoocraft.core.choice`: visitor exhibit choice weighting.

Minecraft packages:

- `com.zoocraft.zoo`: `Zoo` record, `ZooSavedData`, per-dimension chunk index, permissions, `ZooManager` service.
- `com.zoocraft.item`: Zoo Boundary Tool, Zoo Designation Tool.
- `com.zoocraft.visitor`: `Visitor` entity, SmartBrainLib behaviours, spawner, node evaluator, watchdog.
- `com.zoocraft.animal`: recognition rules, species resolution, per-zoo animal census, viewing.
- `com.zoocraft.feedback`: comment types and tallies.
- `com.zoocraft.network`: payloads and subscriptions.
- `com.zoocraft.client`: screens, boundary renderer, comment text renderer, keybind.
- `com.zoocraft.config`: server and client config.
- `com.zoocraft.command`: `/zoo` commands.
- `com.zoocraft.chunk`: optional zoo chunk loading.

### 5.2 Data flow

1. Player actions (tool clicks, dashboard buttons, commands) reach the server as item use or C2S payloads.
2. The server checks permissions, calls `ZooManager`, which updates the `Zoo` and marks `ZooSavedData` dirty.
3. Changes are pushed as snapshots to subscribed clients.
4. Each open zoo's spawner, census and visitor brains tick on the server and report events (admission, view, comment, departure) back to `ZooManager`.

## 6. Zoos, boundaries and gates (M1)

### 6.1 Zoo record

Each zoo is stored in one world-level `SavedData` (kept in the overworld's data storage) and identified by a UUID. Fields:

- `id`, `name`, `dimension`
- `owner` (player UUID), `managers` (set of player UUIDs)
- `boundary` (ordered list of integer X/Z vertices)
- `gates` (list of gates, see 6.5)
- `open` (boolean), `admissionPrice` (non-negative whole emeralds)
- `keepLoaded` (boolean)
- `treasury` (balance and a capped list of recent transactions)
- `satisfaction`, `reputation` (0 to 100)
- statistics: total attendance, attendance today, admission revenue, refused admissions, comment tallies

A per-dimension index from chunk position to zoo IDs answers "which zoo contains this block?" without scanning every zoo. Blocks are never stored in the zoo record, so no zoo operation can delete a structure.

### 6.2 Boundary shape rules

- The boundary is a polygon on the X/Z plane and covers the full build height.
- At least 3 vertices. At most `maxBoundaryVertices` (default 64).
- Edges must not cross each other (JTS validity check).
- Area at least `minZooArea` (default 64 blocks²) and at most `maxZooArea` (default 65,536 blocks²).
- No overlap with any other zoo in the same dimension. Touching edges are allowed. Overlap is rejected.

Each failure produces its own message, for example:

- "A zoo boundary needs at least 3 corners."
- "The boundary crosses itself near (120, 64)."
- "This zoo would overlap Riverside Zoo."
- "The zoo is too small. It needs at least 64 square blocks."

### 6.3 Zoo Boundary Tool

Creating a zoo:

- Right-click a block to add a corner at that block's X/Z. Lines between corners are drawn for the holding player.
- Sneak and right-click to undo the last corner.
- Right-click the first corner to close the shape. A naming screen opens, then the server validates the shape.
- On success, the player becomes the owner. On failure, the draft is kept so the player can fix it.
- The draft is held on the server per player and is discarded on logout.

Editing a zoo (owner or manager):

- Using the tool inside a managed zoo enters edit mode and shows its corners.
- Click a corner to pick it up, click a block to put it down.
- Sneak-click an edge to insert a corner. Use a corner while sneaking to delete it.
- Confirm saves the new shape only if it passes 6.2. Cancel restores the old shape.
- After a successful edit:
  - Gates no longer within 3 blocks of the edge become "detached". They are kept, not deleted, and listed in a report.
  - Visitors now outside the boundary are released (section 7.7).
  - Treasury, statistics, managers and settings are unchanged.

### 6.4 Ownership and permissions

| Action | Owner | Manager | Server operator |
|---|---|---|---|
| Edit boundary and gates | Yes | Yes | Yes |
| Open/close, set price, keep-loaded | Yes | Yes | Yes |
| Deposit and withdraw | Yes | Yes | Yes |
| View dashboard | Yes | Yes | Yes |
| Add or remove managers | Yes | No | Yes |
| Transfer ownership | Yes | No | Yes |
| Delete zoo | Yes | No | Yes |

Rules:

- Every permission is checked on the server.
- The zoo keeps running while the owner is offline.
- Removing a manager removes their access only.
- Transferring ownership to a current manager removes them from the manager list. The old owner does not become a manager unless re-added.
- **A zoo cannot be deleted while its treasury holds any emeralds.** The deleting player must withdraw first. This stops funds from being lost or duplicated.
- Deleting a zoo releases all its visitors.
- Players can own several zoos, each with independent finances.

### 6.5 Gates

- Placed with the Zoo Designation Tool by right-clicking a walkable block within 3 blocks of the boundary edge.
- The server works out the gate's outside point and inside point, perpendicular to the nearest edge.
- If either side isn't walkable, the gate is still placed, with a warning.
- Status is one of: `open`, `blocked` (visitors recently failed to path through it), `detached` (no longer near the edge).
- Detached gates are not used by visitors. Moving the boundary back re-attaches them.
- Any number of gates. No required building style.
- Right-click an existing gate with the tool while sneaking to remove it.

### 6.6 Treasury and ledger

- The balance is a whole number of emeralds stored as a `long`.
- All changes go through `Ledger.apply(transaction)`. Transaction types: `DEPOSIT`, `WITHDRAW`, `ADMISSION`.
- Each transaction has a unique key. For admission the key is the visit ID. A second transaction with an existing key is rejected and changes nothing.
- A transaction that would make the balance negative is rejected and changes nothing.
- Deposits take real emerald items (and emerald blocks, counted as 9) from the player's inventory. Withdrawals give emerald items. Anything that does not fit drops at the player's feet.
- Up to 200 recent transactions are kept for display. The balance and totals are kept permanently.

### 6.7 Acceptance tests (M1)

- JUnit: valid polygons pass; fewer than 3 corners, self-crossing, too small, too large, too many corners each fail with the right reason.
- JUnit: overlapping zoos rejected; touching zoos allowed; different dimensions never conflict.
- JUnit: ledger rejects a repeated key, rejects overdraw, and leaves the balance unchanged on rejection.
- JUnit: every saved record round-trips through its Codec unchanged.
- Manual: draw two zoos of irregular shape; try to draw a third overlapping one and see the message; save, quit, reload, and both zoos are intact with their treasury.
- Manual: edit a boundary so a gate falls away from the edge; the gate is reported as detached and nothing built is removed.
- Manual: a manager cannot add managers or delete the zoo.

## 7. Visitors (M2)

### 7.1 Entity

- `zoocraft:visitor`, a `PathfinderMob`, rendered with the vanilla villager model and a random biome villager skin.
- No profession, no trading, no breeding, no zombie conversion, no natural despawn.
- Saved data: visit ID, zoo ID, group ID, profile, budget, admitted flag, species seen, satisfaction, interest, visit start time, last time saved.
- Behaviour is built with SmartBrainLib behaviours for each stage: arrive, queue, decide, explore, leave.

### 7.2 Profiles and budgets

| Profile | Budget range (emeralds, default) | Willingness to pay | Weights |
|---|---|---|---|
| Budget-conscious | 3 to 10 | Low | Strong price sensitivity |
| Animal enthusiast | 5 to 16 | Medium, rising with species variety | Strong preference for unseen species |
| General | 4 to 12 | Medium | Balanced |

The budget belongs to the visitor and is never mixed with the treasury. Exact values are config defaults and will be tuned in play-testing.

### 7.3 Arrivals

- Each open zoo has a spawner that runs every `spawnIntervalSeconds` (default 10).
- It spawns a group when the zoo is under `maxVisitorsPerZoo` (default 12) and the server is under `maxVisitorsTotal` (default 40).
- The chance of spawning rises with reputation.
- Group size is 1 to 4, weighted towards 1 and 2.
- The spawn point is solid ground 16 to 32 blocks outside a random open gate, outside every zoo, in a loaded chunk.
- A path from the spawn point to the gate's outside point is checked once before spawning.
- If no gate can be reached from any candidate spot after several tries, the zoo shows the alert "Visitors can't find a way to your gates."

### 7.4 Admission

1. The group walks to the gate's outside point.
2. Each member checks that the zoo is open, that it can afford the price, and that the price is within its willingness to pay (based on profile and reputation).
3. If every member accepts, each one pays. For each member, the treasury and that visitor's budget change in the same server step, using the visit ID as the ledger key. The member is marked admitted.
4. If any member refuses, nobody pays. The group records a refusal and comment ("The ticket's too expensive") and walks away.
5. Walking through any other gate during the same visit never charges again.
6. Attendance and admission revenue are recorded once per admitted visitor.

### 7.5 Movement

- A custom node evaluator gives blocks in the tag `zoocraft:visitor_paths` a lower cost. Defaults: dirt path, gravel, all stone brick and brick variants, all planks. Modpacks can extend the tag.
- Positions outside the zoo boundary are treated as impassable for admitted visitors, except while passing through a gate to leave.
- Visitors open wooden doors and fence gates and close them behind them.
- In M2, admitted visitors wander between random reachable points inside the boundary. M3 replaces this with exhibit choice.

### 7.6 Leaving

A visitor leaves when any of these happens: interest reaches zero, budget can't cover anything more, satisfaction drops below a threshold, maximum visit length (`maxVisitMinutes`, default 15) is reached, or the zoo closes.

To leave, the visitor:

1. Picks the nearest open gate it can reach.
2. Walks through it and about 16 blocks outward.
3. Fades out with particles, and its final satisfaction is reported to the zoo.

Closing the zoo stops admissions, sends every admitted visitor to an exit, and makes queueing groups walk away.

### 7.7 Never-trapped safety net

- No progress towards the current target for 30 seconds: plan a new route.
- 3 failed routes in a row: try any open gate. Gates that fail are marked `blocked` for a while.
- Still unable to leave 60 seconds after starting to exit: the visitor fades out where it stands and records "I couldn't find the way out." This shows on the dashboard.
- The same release happens immediately when a boundary edit leaves a visitor outside the zoo, or when its zoo is deleted.
- On loading a saved visitor: if its zoo no longer exists, or the time since it was last saved is longer than `maxVisitMinutes`, the visit ends at once.
- A periodic check removes visitors whose zoo ID is unknown.

### 7.8 Keeping a zoo loaded

- Each zoo has a "Keep zoo loaded" setting, switchable by the owner or a manager. Off by default.
- Server config `allowZooChunkLoading` (default true) controls whether the setting can be used, and `maxLoadedChunksPerZoo` (default 64) caps it.
- When on, NeoForge chunk tickets keep the chunks covering the boundary plus the spawn margin loaded.
- Without it, a zoo only runs while a player is nearby. This is documented.

### 7.9 Acceptance tests (M2)

- GameTest: an admitted visitor's payment is recorded once, even if admission is triggered again or the visitor passes a second gate.
- GameTest: a group with one member who can't afford the price refuses together, and neither the treasury nor any budget changes.
- GameTest: closing the zoo makes every visitor leave within the time limit.
- GameTest: with the only gate walled off, an admitted visitor is released within the safety-net time and the complaint is recorded.
- GameTest: a boundary edit that leaves a visitor outside releases it.
- Manual: open a zoo with two gates and watch groups arrive, pay, wander and leave through either gate.
- Manual: raise the price far above budgets and see refusals and the price comment.
- Manual: with keep-loaded on, fly far away; on return, attendance has increased.

## 8. Animals, viewing and feedback (M3)

### 8.1 Recognition

An entity is an exhibit animal when all of these are true:

1. It is in the tag `zoocraft:exhibit_animals`, **or** it extends vanilla `Animal` or `WaterAnimal` (as most modded animals do), or its entity type's spawn category is `CREATURE`, `WATER_CREATURE`, `WATER_AMBIENT`, `UNDERGROUND_WATER_CREATURE` or `AXOLOTLS`.
2. It is not in the tag `zoocraft:not_exhibit_animals`. This ships containing vanilla villagers, wandering traders, iron golems, snow golems, `zoocraft:visitor`, and optional entries for MineColonies citizens.
3. It is not in the common tag `c:bosses`.
4. Its spawn category is not `MONSTER`, unless it is in `zoocraft:exhibit_animals`.
5. It is inside the zoo boundary.

When nothing is known about an animal's needs, no requirements are invented. It still counts for recognition and variety.

### 8.2 Species identity

- By default, one entity type is one species. Babies and colour or pattern variants are the same entity type, so they never inflate variety.
- A NeoForge data map `zoocraft:species` on entity types lets a datapack merge several entity types into one species ID. Example: two separate male and female entity types from a mod map to one species.
- The species display name is the entity type's name, or the name given in the data map.

### 8.3 Animal census and exhibits

- Every `censusIntervalSeconds` (default 10), each open zoo runs one entity query over its bounding box, filters with 8.1, and stores the result.
- Animals within 8 blocks of each other (chained) form one exhibit. Exhibits are matched to the previous census by overlap so their statistics carry over.
- Each exhibit gets up to 6 candidate viewing spots: walkable blocks inside the boundary, within the viewing distance of an animal, sampled around the exhibit.
- Whether a spot can be reached is learned from real visitor path attempts and cached for 60 seconds.
- Visitors and viewing use only this cached data. Nothing scans the world per visitor.

### 8.4 Viewing

- Once per second, each admitted visitor checks the cached animals near it.
- An animal counts as viewed when it is within 6 blocks horizontally and 3 blocks vertically, and one ray from the visitor's eyes to the animal is not stopped by a block whose shape blocks vision (`canOcclude`). Glass, panes, fences, iron bars and leaves do not block it. Solid walls do.
- Each species counts once per visitor for variety. Repeat views are tracked for the "same animals" comment.

### 8.5 Choosing exhibits

- On arrival, after lingering, or after a failed path, a visitor picks its next exhibit with a weighted random choice.
  - Higher weight for exhibits with species the visitor has not seen.
  - Lower weight for exhibits visited in the last few choices.
  - Lower weight with distance.
  - Animal enthusiasts give unseen species extra weight.
- The visitor walks to the exhibit's best reachable viewing spot, stays 5 to 15 seconds, then chooses again.
- An exhibit whose spots all fail is marked unreachable until the cache expires.

### 8.6 Comments

Comments come only from real events. Each comment type has a translation key and British English text.

| Event | Example |
|---|---|
| First view of a new species | "That was an amazing exhibit!" |
| Many species seen | "This place has so many different animals!" |
| Same species seen repeatedly | "I've seen the same animals three times." |
| No exhibits at all | "There aren't any animals to see." |
| Exhibit can't be reached | "We couldn't get close enough to see the animals." |
| Refused admission | "The ticket was expensive for what we got." |
| Couldn't leave | "I couldn't find the way out." |

- Comments appear briefly as text above the visitor's head, for nearby players.
- The zoo keeps a rolling tally of each comment type over the last in-game day. The dashboard shows the most frequent ones.

### 8.7 Satisfaction and reputation

- Each visitor starts at 50 satisfaction. Events change it (for example, new species up, unreachable exhibit down, price refusal down).
- On leaving, the zoo's satisfaction moves a small step towards the visitor's final score: `zooSat += 0.05 × (visitorSat − zooSat)`.
- Reputation follows satisfaction more slowly: `rep += 0.01 × (zooSat − rep)` per departure.
- Reputation raises arrival chance and willingness to pay. It can always recover.
- Weights are config defaults and will be tuned in play-testing.

### 8.8 Dashboard warnings in Phase 1

- "Visitors can't get close enough to see the {species}."
- "No animals have been recognised in this zoo yet."
- "Visitors can't find a way to your gates."
- "Gate at (x, z) is blocked." / "Gate at (x, z) is no longer on the boundary."

Welfare warnings such as cramped exhibits wait for a later phase.

### 8.9 Acceptance tests (M3)

- JUnit: recognition rules include and exclude correctly for each case in 8.1, using test doubles for entity facts.
- JUnit: species data map merges types; babies don't add variety.
- JUnit: exhibit grouping and matching between censuses.
- JUnit: satisfaction from 100 visitors at 50 and one at 0 stays close to 50.
- JUnit: choice weighting favours unseen species and avoids recent exhibits.
- GameTest: an animal behind a stone wall is not viewed; an animal behind glass is.
- GameTest: a villager and an iron golem inside the zoo are not recognised; a pig and a cod are.
- Manual: build two exhibits with different species and watch visitors move between them and comment.
- Manual (ATM10 copy): Twilight Forest, Aether, Aquaculture, Undergarden and Productive Bees creatures are recognised; MineColonies citizens, Ice and Fire dragons and Cataclysm bosses are not. Gaps are recorded in `docs/compatibility.md`.

## 9. Dashboard and multiplayer (M4)

### 9.1 Opening

- `/zoo` opens the dashboard for the zoo the player is standing in, if they manage it; otherwise a list of zoos they manage.
- A keybind does the same. Its default key is chosen in M4 after checking for clashes with ATM10.

### 9.2 Tabs

- **Overview:** visitors now, attendance today and total, treasury, admission revenue, satisfaction, reputation, alerts, top comments.
- **Animals:** recognised species with counts; exhibits with view counts and warnings.
- **Settings:** open/close, admission price, keep-loaded, gates and their status, deposit and withdraw, managers (owner only), transfer ownership (owner only).

### 9.3 Sync

- Opening the dashboard subscribes the player to that zoo. The server sends a full snapshot, then refreshes subscribers about once a second. Closing the screen unsubscribes.
- Every button sends a C2S request. The server validates the permission, applies the change through `ZooManager`, and pushes a new snapshot to all subscribers.
- Clients never change zoo data locally.
- Payload handlers run on the main server thread, so simultaneous requests are applied one at a time.
- Players holding a zoo tool receive outlines of zoos within render distance.

### 9.4 Commands

`/zoo`, `/zoo list`, `/zoo open|close <zoo>`, `/zoo price <zoo> <emeralds>`, `/zoo manager add|remove <zoo> <player>`, `/zoo transfer <zoo> <player>`, `/zoo delete <zoo>`, and operator-only `/zoo admin ...` versions for abandoned zoos. Debug commands: `/zoo debug census|visitors|spawn`.

### 9.5 Acceptance tests (M4)

- Manual (dev server and two clients): the owner adds a manager; both see the same treasury and visitor count update within about a second.
- Manual: both withdraw at the same moment with only enough for one; exactly one succeeds and the balance never goes negative.
- Manual: the owner logs out; the manager keeps the zoo running and admissions continue.
- Manual: the owner removes the manager; the manager's dashboard closes and requests are refused.
- Manual: save, quit and reload with visitors inside; no payments are duplicated and no zoo data is lost.
- Manual (ATM10 copy): the mod loads with no errors, the keybind doesn't clash, and the full loop works.

## 10. Configuration defaults

| Key | Side | Default |
|---|---|---|
| `maxVisitorsPerZoo` | server | 12 |
| `maxVisitorsTotal` | server | 40 |
| `spawnIntervalSeconds` | server | 10 |
| `maxVisitMinutes` | server | 15 |
| `censusIntervalSeconds` | server | 10 |
| `viewingRadius` | server | 6 |
| `minZooArea` | server | 64 |
| `maxZooArea` | server | 65536 |
| `maxBoundaryVertices` | server | 64 |
| `allowZooChunkLoading` | server | true |
| `maxLoadedChunksPerZoo` | server | 64 |
| `showVisitorComments` | client | true |

## 11. Milestones

Each milestone has its own implementation plan in `docs/superpowers/plans/`, written just before that milestone starts.

| Milestone | Scope | Done when |
|---|---|---|
| **M0 Foundations** | JDK 21, ModDevGradle project on NeoForge 21.1.251, SmartBrainLib and JTS dependencies, JUnit and GameTest set up, empty mod loads, CLAUDE.md updated with build/test commands | `gradlew build` passes, a sample JUnit and GameTest pass, the mod appears in the mods list standalone and in an ATM10 copy |
| **M1 Zoos** | Section 6 | M1 acceptance tests pass |
| **M2 Visitors** | Section 7 | M2 acceptance tests pass |
| **M3 Animals** | Section 8 | M3 acceptance tests pass |
| **M4 Dashboard and multiplayer** | Section 9 | M4 acceptance tests pass, then the play-test question in section 1 is answered |

## 12. GDD section 13 questions resolved for Phase 1

| Question | Resolution |
|---|---|
| Boundary editing and recovery | Edit mode with move, insert and delete corners; validated on confirm; detached gates kept; visitors outside are released (6.3, 7.7). |
| Physical shop storage | Not in Phase 1. |
| Species identity | Entity type by default, merged with the `zoocraft:species` data map (8.2). |
| Visitor capacity | 12 per zoo and 40 per server by default, configurable (7.3). |
| Ownership and treasury protection | Owner-only transfer and deletion; no deletion with funds; operators can handle abandoned zoos (6.4). |

## 13. Known risks

- **Pathfinding cost:** vanilla pathfinding on large zoos may be slow. Mitigation: modest limits, cached reachability, path length caps; measure in M2.
- **Boundary as an AI rule:** visitors that are pushed out of the boundary (by players or water) must recover. Covered by the safety net.
- **Recognition breadth:** some modded creatures may use unusual classes. Mitigation: the include and exclude tags; gaps documented.
- **ATM10 interactions:** mods that affect all mobs (for example spawn controllers or claim protection) could affect visitors. Tested in M0 and M4 and documented.
