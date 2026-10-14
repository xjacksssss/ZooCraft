# ZooCraft — Game Design Document v1.0

**Platform:** Minecraft 1.21.1 · NeoForge  
**Target modpack:** All the Mods 10 — ATM10 (NeoForge)  
**Document status:** Baseline design agreed; ready for feature planning and development.

---

## 1. Product vision

ZooCraft is a zoo-building and management mod in which players design their own attractions, welcome living villager visitors, care for a diverse collection of animals, operate shops and improve their business based on visitor feedback.

> **Players decide what their zoo looks like. ZooCraft decides how well it works.**

There are no mandatory building blueprints, fixed exhibit layouts or progression locks. Players fund their zoo with their own emeralds, develop it at their own pace and earn more by creating an attraction visitors want to experience.

### Product goals

1. Make a zoo that is recognisably the player's own creation.
2. Make visitors react to what actually happens in the zoo.
3. Reward good management without punishing creative experimentation.
4. Support multiplayer ownership and shared management from the initial release.
5. Recognise animals from other mods automatically wherever practical.
6. Prioritise stable, smooth gameplay over large crowds before optimisation has been proven.

### Out of scope for the initial release

- Mandatory progression trees or research unlocks.
- Automatic animal deaths or severe penalties for poor welfare.
- Staff management and salaries.
- Detailed breeding and conservation systems.
- Guided tours, rides and large-scale attractions.
- A guarantee of hundreds of fully active visitors on all hardware.

These remain possible future additions rather than requirements for the first release.

## 2. Core gameplay loop

1. **Build:** Construct habitats, paths, shops and facilities.
2. **Define and open the zoo:** Mark the boundary, designate gates and set the admission price.
3. **Welcome visitors:** Villagers pay, explore, view animals and shop.
4. **Review performance:** Discover complaints, popular exhibits, spending and stock shortages.
5. **Reinvest and expand:** Improve the zoo using earned emeralds and repeat the cycle.

The player can open a modest zoo early rather than needing to complete a prescribed checklist. Better exhibits, useful facilities, attractive pricing and effective management should make the business more successful.

## 3. Zoo ownership, boundaries and gates

### 3.1 Creating a zoo

The player uses the Zoo Boundary Tool to mark a custom-shaped perimeter. The boundary may follow natural terrain or any other shape the player chooses.

A zoo has:

- One creator-owner.
- A custom boundary.
- One or more designated entrances and exits.
- Its own admission price, finances, exhibits, shops and facilities.
- A visitor population and reputation.
- A list of invited managers and their permissions.

Players fund their own zoo with their own emeralds. There is no automatic starter grant.

### 3.2 Boundary rules

- Buildings and designated facilities inside the boundary belong to the zoo.
- The boundary does not make enclosed areas automatically accessible.
- The boundary can be edited without deleting existing structures or progress.
- A player can create multiple separate zoos, each with independent finances.
- Overlapping zoo boundaries should be rejected by default to prevent ambiguous ownership.
- If a boundary is edited around existing visitors or facilities, the game must handle the change safely and report anything that is no longer included.
- Invalid or incomplete boundaries should produce clear feedback.

### 3.3 Entrances and exits

Players can designate multiple gates around the boundary.

Visitors choose an accessible entrance when arriving and a suitable exit when leaving. A closed or blocked gate must not leave visitors trapped.

For the initial release, gates should be functional designations rather than requiring a particular architectural style. Players remain free to build grand entrances, small side gates or elaborate ticket halls around them.

### 3.4 Ownership and permissions

The creator owns the zoo and can invite other players to manage it.

| Permission | Owner | Invited manager |
|---|---|---|
| Build and modify the zoo | Yes | Yes |
| Change zoo settings | Yes | Yes |
| Set admission prices | Yes | Yes |
| Manage shops and facilities | Yes | Yes |
| View financial reports | Yes | Yes |
| Invite or remove managers | Yes | No |
| Transfer ownership | Yes | No |

All players see the same finances, visitor activity and inventory. The owner remains the final authority over access and ownership.

## 4. Visitor simulation and behaviour

### 4.1 Arrival and admission

Visitors arrive individually and in groups. They evaluate the admission price against their personal budget and expectations.

Admission rules:

1. The zoo must be open and have an accessible entrance.
2. The visitor must be able to afford the admission price.
3. A successful admission transfers emeralds into the zoo's treasury.
4. A visitor who refuses the price does not pay and may produce a complaint.
5. The zoo records attendance and admission revenue.
6. Visitors do not need to visit every exhibit before leaving.

Admission is a one-off purchase per visit. A visitor should not be charged again simply for walking through another gate.

### 4.2 Visitor needs and personalities

The first release uses three visitor profiles.

| Profile | Main priorities |
|---|---|
| Budget-conscious | Affordable admission, reasonable shop prices and good value. |
| Animal enthusiast | Animal variety, visible exhibits and interesting creatures. |
| General visitor | A pleasant experience, convenient navigation and available facilities. |

These profiles affect decisions, not access to content. All visitors can explore the same zoo; they simply value different things.

Visitors can become hungry, need a toilet, want to shop, or lose interest. Needs should influence their choices without making every villager behave identically.

### 4.3 Visit duration

Visits vary in length based on the visitor's interests, zoo size, available attractions, spending opportunities and satisfaction.

A large zoo should naturally offer more opportunities for a longer visit. A small zoo can still succeed if its exhibits are appealing and its prices reflect its size.

Visitors can leave early because of:

- Excessive prices or poor value.
- Repeatedly encountering empty or uninteresting exhibits.
- Unavailable food or toilets.
- Excessive litter.
- Navigation difficulties.
- Running out of money or losing interest.

### 4.4 Navigation

Visitors prefer player-built paths but are not required to remain on paths at all times.

They should:

- Navigate toward accessible exhibits and facilities.
- Prefer sensible routes over random wandering.
- Avoid repeatedly visiting the same attraction when other interesting options exist.
- Choose suitable exits when leaving.
- Recover from blocked routes or inaccessible areas.
- Avoid becoming permanently stuck inside the zoo.

The game must distinguish between an area being inside the zoo and an area being reachable. A building on an isolated island inside the boundary does not automatically count as an accessible facility.

### 4.5 Animal viewing

For the initial release, animals count as viewed when a visitor gets sufficiently close to an eligible animal in an accessible area.

This is a deliberate simplification: full visibility checks through every block are not a launch requirement.

However, proximity should not make every animal in the zoo count as viewed. Animals behind distant walls, in inaccessible areas or far outside a visitor's route should not contribute automatically.

### 4.6 Visitor comments and reviews

Visitor reactions must be tied to actual experiences.

Examples:

- “That was an amazing exhibit!”
- “I've seen the same animals three times.”
- “The ticket was expensive for what we got.”
- “There's nowhere to get something to eat.”
- “Why is there rubbish everywhere?”
- “This place has so many different animals!”

When leaving, visitors contribute to the zoo's overall satisfaction and reputation. The management screen should summarise recurring complaints rather than expecting the player to read every individual comment.

## 5. Animal compatibility and exhibit management

### 5.1 Automatic recognition

The mod should automatically recognise eligible animals from vanilla Minecraft and installed animal mods wherever possible. The player should not need to configure every species manually.

Unusual creatures should still be usable even when the game cannot determine every detail about them.

The system distinguishes between:

- An individual animal.
- A recognised species.
- An exhibit containing one or more animals.
- A viewing opportunity that visitors can actually experience.

### 5.2 Exhibit quality

Exhibits receive ratings based on a combination of:

- Animal variety.
- Visitor access and viewing opportunities.
- Available space.
- Natural surroundings, such as terrain, vegetation and water where relevant.
- Basic welfare suitability.
- Optional presentation and landscaping bonuses.

These ratings should provide useful feedback without forcing every exhibit to follow a prescribed shape or use special zoo blocks.

### 5.3 Welfare policy

Poor welfare generates warnings and lower exhibit ratings. It does not automatically kill animals or impose severe survival penalties.

The system should explain its concerns in plain language, such as:

- The exhibit appears too cramped.
- Suitable natural surroundings are limited.
- Visitors cannot get close enough to appreciate the animals.

When an animal's requirements are unknown, the game should avoid inventing strict requirements. Recognition and species variety should continue to work where possible.

### 5.4 Compatibility principle

ZooCraft should work independently of All the Mods 10 (ATM10), with additional testing against the modpack.

Compatibility means more than simply recognising creatures. The mod should also avoid treating every living entity as an exhibit animal, and it must not depend on a particular animal mod being installed.

Known compatibility gaps should be documented rather than hidden.

## 6. Shops, physical stock and visitor spending

### 6.1 Creating a shop

Players build shops using ordinary Minecraft blocks, then use the designation tool to identify the shop's purpose.

A shop has:

- A designated shop area and customer access point.
- Physical storage for saleable products.
- A product selection based on items stocked.
- Configurable selling prices.
- Sales and inventory records.
- A connection to the zoo's shared treasury.

A player can build a wooden food stall, a modern café or a themed souvenir shop. Its appearance is entirely up to the player; its designation controls its business function.

### 6.2 Shop transactions

A purchase succeeds only if:

1. The shop is open and accessible.
2. The product is available in its physical stock.
3. The visitor can afford the price.
4. The stock can be removed and the payment processed successfully.

On a successful sale, the appropriate item leaves the stock and emeralds enter the zoo's treasury. The transaction is recorded once.

Visitors have individual budgets. They can afford admission but later discover they cannot afford food, or decide that a product is too expensive even when they have enough emeralds.

### 6.3 Support for modded products

The goal is broad support for ordinary Minecraft items and items added by other mods.

Not every item is necessarily a sensible shop product. The design should distinguish between:

- Items that can be stocked and sold normally.
- Items that need a shop category or visitor-facing description.
- Items that are unsuitable for sale or require special handling.

The initial system should support general item sales wherever practical, with curated food and drink categories for visitor needs. Unusual modded items may be sellable without automatically satisfying hunger or other needs.

### 6.4 Emerald economy

The economy has three core elements:

- Admission income.
- Shop revenue.
- Expenditure on restocking and other supported business costs.

The initial version does not require wages, loans, taxes or utility bills. The player's own emeralds fund the start of the business; subsequent earnings can be reinvested.

The treasury is shared by the zoo's owner and authorised managers. Visitors' individual budgets remain separate from the zoo's finances.

## 7. Toilets, rubbish and cleanliness

### 7.1 Toilets

Players designate a suitable area as a toilet using the facility tool. The building can be designed however they want.

Visitors should only consider the toilet available when it is accessible and usable. A designation alone should not make a toilet in an unreachable building satisfy every visitor.

The first release can use a straightforward availability model rather than simulating individual toilet stalls.

### 7.2 Dedicated rubbish bins

Rubbish bins are dedicated zoo blocks. Players can decorate or incorporate them into buildings, but their functional identity is clear.

Visitors sometimes dispose of litter in nearby bins. Food purchases and other visitor activities can generate litter, which may accumulate when bins are unavailable or inconvenient.

### 7.3 Manual cleanup

Until staff are introduced, players are responsible for cleaning up physical litter.

Litter should be identifiable and reasonably easy to collect. Cleaning it improves the zoo's cleanliness and prevents recurring complaints.

The system should place sensible limits on litter accumulation so a busy zoo does not become overwhelmed with rubbish objects.

### 7.4 Cleanliness consequences

Litter should affect:

- Visitor comments.
- Overall satisfaction.
- Reputation.
- The likelihood of visitors recommending the zoo.

Bins reduce littering but do not automatically solve all cleanliness problems. The player should be able to identify where litter is accumulating and respond by adding bins or cleaning up.

## 8. Finances, satisfaction and reputation

The management dashboard combines the most important information into one place.

### Dashboard sections

- **Overview:** attendance, satisfaction, reputation, treasury and alerts.
- **Visitors:** arrivals, refused admissions, visit duration, spending and complaints.
- **Animals and exhibits:** recognised species, exhibit ratings, viewing activity and welfare warnings.
- **Shops and stock:** inventory, pricing, sales and shortages.
- **Finances:** admission revenue, shop revenue, expenses and treasury history.
- **Settings and ownership:** boundaries, entrances, permissions, visitor limits and management options.

The dashboard should favour actionable information. “Exhibit rating is low” is less useful than “Visitors are not getting close enough to see the animals.” Likewise, “Revenue is falling” is less useful than “Your food shop is out of stock and visitors are leaving without making purchases.”

### 8.1 Satisfaction

Satisfaction represents how visitors feel about the zoo. It is influenced by animal viewing, variety, facilities, cleanliness, prices and navigation.

It should respond to improvements without allowing a single unhappy visitor to cause a dramatic collapse.

### 8.2 Reputation

Reputation represents the zoo's longer-term appeal. It changes based on repeated visitor experiences, rather than fluctuating wildly after every interaction.

A better reputation can attract more visitors and increase the price visitors are willing to consider reasonable. A poor reputation should be recoverable through improvements.

### 8.3 Open progression

There are no mandatory unlock trees. The player's money, building resources, available space and management decisions determine how quickly the zoo grows.

The game may offer optional milestones and achievements later, but these should not block core building features.

## 9. Multiplayer and performance requirements

### Multiplayer

ZooCraft must support shared management from its first release.

The owner and invited managers should see the same zoo boundary, visitor activity, animal records, stock, treasury and reputation.

The design must prevent duplicated payments, inconsistent inventory and conflicting ownership records. The zoo must continue to function when the owner is offline.

### Performance philosophy

Smooth gameplay takes priority over large visitor numbers in the first release.

The long-term goal is to support large parks with hundreds of visible visitors, but the first version should use modest configurable limits and prove that the simulation remains stable before increasing crowd sizes.

Performance principles:

- Avoid unnecessary continuous calculations.
- Reuse visitor decisions where they remain valid.
- Avoid repeatedly checking every animal and block for every visitor.
- Limit uncontrolled litter and visitor accumulation.
- Ensure visitors can exit safely when a zoo closes or becomes inaccessible.
- Test multiple zoos and multiple players, not only a single zoo in a local world.

A smaller crowd that moves convincingly is preferable to a huge crowd that freezes, becomes stuck or makes the server unpleasant to play on.

## 10. MVP acceptance criteria

The MVP is the first version that proves the core zoo experience works. It does not need every planned management feature, but it must meet clear, testable conditions.

A feature is complete when its expected behaviour has been demonstrated in a test world and, where relevant, in multiplayer.

### Zoo creation

- [ ] A player can mark and save a custom-shaped zoo boundary.
- [ ] The zoo can have multiple designated entrances and exits.
- [ ] Invalid or overlapping boundaries are handled with clear feedback.
- [ ] Editing a boundary does not delete zoo buildings or financial progress.

### Admission and visitors

- [ ] The player can open and close the zoo.
- [ ] Visitors arrive individually and in groups.
- [ ] Visitors can pay admission from their available budget or refuse entry.
- [ ] Admission revenue is recorded exactly once.
- [ ] Visitors can explore, receive feedback and find an exit.

### Animals and exhibits

- [ ] Eligible vanilla animals are recognised.
- [ ] Eligible animals from a tested modded-animal setup can be recognised without manual registration.
- [ ] Different species contribute to collection variety.
- [ ] Nearby eligible animals can count as viewed.
- [ ] Poor exhibit conditions generate understandable warnings without automatically killing animals.

### Business and facilities

- [ ] A player can designate a shop and stock it with ordinary Minecraft items.
- [ ] A successful purchase removes the correct stock and transfers payment once.
- [ ] An unaffordable or unavailable purchase fails without changing stock or finances.
- [ ] A player can designate a toilet and a dedicated rubbish bin.
- [ ] Physical litter can appear and be manually collected.

### Management and multiplayer

- [ ] The dashboard shows attendance, treasury, admission revenue and visitor satisfaction.
- [ ] The zoo creator can invite and remove managers.
- [ ] Owner and manager clients see consistent zoo state.
- [ ] Zoo data persists after the world is saved and reopened.
- [ ] Visitor limits can be configured and the simulation remains stable under the agreed test load.

### Compatibility and reliability

- [ ] The mod starts in a supported standalone NeoForge 1.21.1 instance.
- [ ] The mod loads in the selected test version of All the Mods 10 (ATM10).
- [ ] Unrecognised entities do not automatically count as valid zoo animals.
- [ ] Closing a zoo or blocking a route does not leave visitors permanently trapped.
- [ ] The test suite finds no duplicated payments, duplicated stock removal or lost zoo data.

### What does not block the MVP?

The following can be postponed without undermining the core concept:

- Hundreds of simultaneously active visitors.
- Hireable staff.
- Complex animal-specific welfare requirements.
- Breeding and conservation.
- Advanced shop categories and special products.
- Extensive visitor personality systems.
- Highly sophisticated crowd routing.
- Optional achievements and progression challenges.

The shop, toilet and litter systems belong in the complete initial management release, but they do not all need to be present in the very first internal prototype. The prototype should establish the boundary, admission, movement and viewing loop before expanding into full business management.

## 11. Phased development roadmap

### Phase 1 — Core simulation

**Goal:** Prove the central visitor experience.

- Zoo creation, boundaries and multiple gates.
- Opening, closing and admission prices.
- Actual walking villagers and basic navigation.
- Visitor groups and individual visitors.
- Animal recognition, species variety and viewing.
- Contextual comments and basic satisfaction.
- Shared ownership data and persistence.

**Exit condition:** A player can open a zoo, earn admission income, observe visitors viewing animals and see their experience reflected in feedback.

### Phase 2 — Complete management loop

**Goal:** Deliver a working zoo business.

- Designation tool for shops and facilities.
- Physical stock, shop pricing and emerald transactions.
- Individual visitor budgets.
- Toilets and visitor needs.
- Dedicated bins and physical litter.
- Manual cleanup.
- Financial reports, reputation and actionable complaints.
- Multiplayer manager permissions and consistency testing.

**Exit condition:** The player can operate a functioning zoo business, make meaningful management decisions and improve performance in response to visitor behaviour.

### Phase 3 — Scale and compatibility

**Goal:** Improve performance and ecosystem support.

- Improve crowd movement and congestion handling.
- Expand visitor limits based on measured performance.
- Test large parks and multiple simultaneous zoos.
- Expand automatic recognition for ATM10 animal mods.
- Improve exhibit evaluation and visitor route selection.
- Add configurable simulation settings and better diagnostics.

**Exit condition:** Representative large-park tests demonstrate stable gameplay, with documented performance expectations and known compatibility limitations.

### Phase 4 — Operations and expansions

**Goal:** Add depth to zoo management.

- Hireable cleaners and animal keepers.
- Staff assignments, wages and work areas.
- More advanced animal welfare and enrichment.
- Breeding, conservation and rarity.
- Souvenir shops, guided tours and attractions.
- More visitor personalities and group behaviours.
- Optional milestones and additional management settings.

**Exit condition:** The core game remains stable while new systems expand the depth of zoo management.

Calendar deadlines should be set only after prototypes establish the difficulty of visitor pathfinding, modded-animal recognition and server performance.

## 12. Locked design decisions

These are the agreed design decisions. They should not be reopened during development unless testing reveals a genuine problem.

| Area | Agreed rule |
|---|---|
| Platform | Minecraft 1.21.1 with NeoForge |
| Distribution | Standalone mod, designed for All the Mods 10 (ATM10) compatibility |
| Building | Unrestricted player-designed construction |
| Zoo boundaries | Custom-shaped, marked by a tool |
| Gates | Multiple entrances and exits |
| Ownership | Creator owns the zoo and invites managers |
| Visitors | Actual walking villagers, arriving individually and in groups |
| Navigation | Prefer player-built paths; accessible terrain is also usable |
| Viewing | Nearby eligible animals count as viewed |
| Animal welfare | Warnings and lower ratings, not automatic animal deaths |
| Currency | Emeralds |
| Initial funding | Player's own emeralds |
| Shops | Designated by a tool, physically stocked and broadly compatible with Minecraft items |
| Litter | Physical litter, manually collected until staff are introduced |
| Progression | Open progression, no mandatory unlock tree |
| Difficulty | Moderate |
| Multiplayer | Shared finances and consistent zoo state |
| Performance | Smooth smaller crowds first; hundreds of visitors remain a longer-term target |
| Staff | Post-launch expansion |

## 13. Remaining design ambiguities

The core concept is defined well enough to begin planning a prototype. The following details should be resolved before the corresponding systems are considered complete.

### Boundary editing and recovery

Define how players place, confirm and cancel boundary points, and what happens when a boundary changes while visitors are inside. Recommended rule: preserve all zoo data and safely redirect or release affected visitors.

### Physical shop storage

Decide whether shops use a designated chest, a shop-owned inventory or a broader storage area. Recommended starting point: a dedicated inventory attached to each designated shop, with freedom to decorate the surrounding building.

### Species identity

Some mods add variants, subspecies, babies, alternate forms or creatures with unusual behaviour. Count recognisable species separately where possible, without allowing trivial variants to create unlimited collection variety.

### Visitor capacity

Do not promise a fixed number of simultaneous visitors until the simulation has been tested. The first release needs a sensible default limit, a configurable maximum and a clear way to reduce crowds on weaker servers.

### Ownership and treasury protection

Define what happens when a manager is removed, an owner leaves the server or a zoo is abandoned. Zoo funds should not become inaccessible or be duplicated by changing ownership.

These details do not prevent development from starting.

## 14. Recommended first development milestone

Build a tiny but fully playable zoo prototype before expanding the feature list.

It should contain:

1. A custom-shaped zoo boundary and at least two usable gates.
2. A way to open the zoo and set an emerald admission price.
3. Actual villagers arriving, paying, walking toward animals and leaving.
4. Basic automatic animal recognition and nearby viewing.
5. A small dashboard showing attendance, income and visitor feedback.
6. Shared state that works when two players manage the same zoo.

Do not build staff, breeding, elaborate shops or complex welfare mechanics yet.

The first test should answer one question:

*Is it enjoyable to watch a zoo you built come alive, with villagers reacting to the things you've placed in it?*

If that loop works, the rest of the management systems have a solid foundation. If it does not, improve visitor movement and feedback before adding more complexity.

---

## 15. Next deliverable

ZooCraft v1.0 is the baseline design. The next useful deliverable is a feature specification for Phase 1, with individual gameplay rules, edge cases and acceptance tests for zoo creation, admission, visitor movement and animal viewing.

This provides a clear starting point without prematurely committing to every detail of the eventual full game.
