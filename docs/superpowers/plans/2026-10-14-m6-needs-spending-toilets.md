# M6 Needs, Spending and Toilets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Visitors get hungry and need the toilet, run errands to shops and toilets they can reach, buy food, drinks and souvenirs from their own budgets through the M5 exactly-once sale, complain when they can't, and leave early when needs go unmet (Phase 2 spec section 8).

**Architecture:** Pure decisions live in `core.needs`: `Needs` (growth and thresholds), `VisitPlanner` (which errand next), `ProductChoice` (what to buy, or why nothing) and `SouvenirRules`. All are JUnit-tested. On the Minecraft side, a new `VisitorErrands` helper runs errands for `VisitorController`. The controller stays a phase machine and only asks the helper for the next target and what to do on arrival or failure. Facility data comes only from the M5 `FacilityCensus` snapshot, so the real stock is touched only inside `ZooManager.sell`. Toilet capacity is a runtime `ToiletOccupancy` map with expiring entries, and facility reachability is a runtime `FacilityReach` map, the same learned approach as exhibits.

**Tech Stack:** NeoForge 21.1.251, SmartBrainLib walk targets (unchanged), vanilla equipment slots for held items (`CrossedArmsItemLayer` already renders them), entity NBT.

**Spec:** `docs/superpowers/specs/2026-10-14-phase2-management-loop-design.md` (section 8)

**Execution note:** Autonomous build (user memory). Native execution on branch `phase2`.

## Global Constraints

- Needs only rise while the visitor is admitted and exploring. Closing the zoo, the safety net and boundary releases always override errands.
- Hunger starts at 0 to 30 and rises 0.14 to 0.25 per second. Toilet starts at 0 to 20 and rises 0.11 to 0.2 per second. Both rates are rolled per visitor. Errand threshold is `needThreshold` (60), urgent is 85, and leaving early happens after 60 seconds at 100. Food lowers hunger by 60, a drink by 35, and a toilet resets the need to 0.
- Toilet use takes 6 seconds. Capacity is `toiletCapacity` (2). A visitor waits up to 20 seconds when it's full.
- Souvenir wish chances: budget-conscious 10%, general 35%, animal enthusiast 25%, or 35% once it has seen 5 or more species.
- Budgets come from config: budget-conscious 4 to 12, enthusiast 6 to 18, general 5 to 15.
- Sale key `sale:<visitId>:<saleIndex>`; `saleIndex` is saved. The budget is deducted only on SOLD.
- Bought items never drop. The held item's drop chance is 0.
- No world scans per visitor: errands read `FacilityCensus.cached(zoo)`.
- British English in comments.

## Review Focus

- A visitor that is hungry, but every shop with food is unreachable or sold out, must not loop between shops forever: one retry, then a 30-second cooldown before the planner offers food again (GameTest in Task 3: hungry visitor with only a sold-out shop complains and keeps exploring).
- A visitor reloaded from an older save gets DUPLICATE on its next sale key: it must skip to the next index and never pay or take stock twice (GameTest in Task 2: save/load keeps `saleIndex`; JUnit in M5 covers duplicate keys).
- A zoo with no shops at all: budget rules must not send visitors home just for having little money (Task 3: the "can't afford" rule applies only when food exists in the zoo).
- A toilet occupied by a visitor that disappears (released by the safety net or unloaded) must free up by itself (Task 4: occupancy entries expire after 10 seconds without renewal; GameTest).
- A group follower that gets hungry goes on its own errand and doesn't keep following the leader into an exhibit (Task 3: errands take priority over following, checked in `VisitPlanner` input).

---

### Task 1: Needs, planner, product choice and budgets

**Files:**
- Create: `core/needs/Need.java`, `core/needs/Needs.java`, `core/needs/Errand.java`, `core/needs/PlannerInput.java`, `core/needs/VisitPlanner.java`, `core/needs/Offer.java`, `core/needs/ProductChoice.java`, `core/needs/SouvenirRules.java`
- Modify: `core/visitor/VisitorProfile.java` (new default budget ranges; `rollBudget(RandomGenerator, int min, int max)`), `config/ZooConfig.java` (a `needs` section with `needThreshold` and `toiletCapacity`; a `budgets` section with six min/max keys)
- Test: `src/test/java/com/zoocraft/core/needs/NeedsTest.java`, `VisitPlannerTest.java`, `ProductChoiceTest.java`, `SouvenirRulesTest.java`; update `core/visitor/VisitorProfileTest.java`

**Produces:**

```java
public enum Need { HUNGER, TOILET }

public final class Needs {                    // mutable, saved by the visitor
    public static final double URGENT = 85, FULL = 100, OVERDUE_SECONDS = 60;
    public static final double FOOD_RELIEF = 60, DRINK_RELIEF = 35;
    public static Needs roll(RandomGenerator random);
    public Needs(double hunger, double toilet, double hungerRate, double toiletRate, double hungerFullSeconds, double toiletFullSeconds);
    public void tick(double seconds);          // rises, capped at 100, counts seconds spent at 100
    public double level(Need need);
    public boolean wants(Need need, double threshold);
    public boolean urgent(Need need);
    public boolean overdue(Need need);         // at 100 for OVERDUE_SECONDS
    public void eat(boolean drink);            // hunger -= 60 or 35, not below 0; resets the full timer
    public void useToilet();
    public void set(Need need, double value);  // tests and /zoo debug needs
    // getters for saving: hunger(), toilet(), hungerRate(), toiletRate(), hungerFullSeconds(), toiletFullSeconds()
}

public enum Errand { EXHIBIT, FOOD, TOILET, SOUVENIR, LEAVE }

/** What the planner knows. "available" means reachable, open and suitable per the cached facility snapshot. */
public record PlannerInput(boolean leaving, boolean hungry, boolean foodAvailable, boolean needsToilet, boolean toiletAvailable,
                           boolean foodOnCooldown, boolean toiletOnCooldown, boolean endingByChoice, boolean souvenirWish,
                           boolean souvenirAvailable, boolean lastStopDone) {}

public final class VisitPlanner {
    public static Errand next(PlannerInput in);
    /** Needs over the threshold with nothing that can meet them: these become complaints. */
    public static List<Need> unmet(PlannerInput in);
}
```

Planner order: `leaving` gives LEAVE. Then TOILET (needs a toilet, one is available, no cooldown). Then FOOD (hungry, food available, no cooldown). Then, if `endingByChoice`: SOUVENIR when there is a wish, a souvenir is available and the last stop isn't done, otherwise LEAVE. Otherwise EXHIBIT.

```java
/** One thing a shop could sell to this visitor. categoryDefault is that category's default price. */
public record Offer(UUID shopId, String itemId, ProductCategory category, int price, int categoryDefault, double distance) {}

public final class ProductChoice {
    public enum Outcome { CHOSEN, NOTHING_STOCKED, TOO_EXPENSIVE, CANT_AFFORD }
    public record Result(Outcome outcome, Optional<Offer> offer) {}
    /** Among offers in the wanted categories: drop those over budget, then those over the visitor's acceptable price,
     *  then pick at random weighted by 1 / (1 + distance / 16), and also by 1 / price for budget-conscious visitors. */
    public static Result choose(List<Offer> offers, Set<ProductCategory> wanted, int budget, VisitorProfile profile,
                                double personalFactor, double reputation, double random);
}

public final class SouvenirRules {
    public static double chance(VisitorProfile profile, int speciesSeen);
}
```

- [ ] JUnit `NeedsTest`: rolled start values and rates stay within the spec ranges over 1000 rolls; hunger at the slowest and fastest rate crosses 60 between 4 and 7 minutes from a start of 0 to 30 (check both extremes); urgent at 85; overdue only after 60 seconds at 100; eating food or a drink lowers hunger by 60 or 35 and never below 0; the toilet resets to 0.
- [ ] JUnit `VisitPlannerTest`: toilet beats food; food beats exhibits; cooldown suppresses an errand; `leaving` always gives LEAVE; ending by choice gives SOUVENIR only with a wish, availability and no last stop yet, else LEAVE; `unmet` lists hunger when hungry with no food available, but not when only on cooldown.
- [ ] JUnit `ProductChoiceTest`: an empty list gives NOTHING_STOCKED; all over budget gives CANT_AFFORD; all affordable but over the acceptable price gives TOO_EXPENSIVE; only wanted categories are considered; with random 0 and a cheap and a dear offer at the same distance, a budget-conscious visitor picks the cheap one.
- [ ] JUnit `SouvenirRulesTest`: 0.10, 0.35, 0.25, and 0.35 for an enthusiast with 5 species.
- [ ] JUnit `VisitorProfileTest`: budgets roll within the passed min and max.
- [ ] Run `./gradlew test`, commit.

### Task 2: Visitor state, comments, config and debug hooks

**Files:**
- Modify: `visitor/Visitor.java` (fields `needs`, `saleIndex`, `souvenirRoll`, `lastStopDone`, `spentInShops`, errand state; saved in `ZooVisit`, with defaults when missing; budget from config in `begin`; held item drop chance 0; test hooks `testSetNeeds(double hunger, double toilet)` and `testSetSouvenirRoll(double)`; `mobInteract` info shows hunger and toilet), `feedback/CommentType.java` (new types below), `command/ZooDebugCommands.java` (`/zoo debug needs <hunger> <toilet> [zoo]`; `/zoo debug visitors` shows needs), lang
- Test: `gametest/NeedsGameTests.java`

New comment types and satisfaction effects: `HUNGRY_NO_FOOD` (-6, "There's nowhere to get something to eat."), `NO_TOILET` (-8, "I couldn't find a toilet."), `NEED_TOILET` (0, "I really need a toilet!"), `STARVING` (0, "I'm starving!"), `TOILET_QUEUE` (-4, "The queue for the toilet is too long."), `CANT_REACH_TOILET` (-6, "We couldn't get to the toilets."), `CANT_REACH_SHOP` (-6, "We couldn't get to the shop."), `SOLD_OUT` (-4, "They've sold out!"), `SHOP_TOO_EXPENSIVE` (-3, "That's far too expensive."), `CANT_AFFORD` (-2, "I can't afford anything here."), `TASTY` (+4, "That was delicious!", not a complaint), `SOUVENIR` (+5, "I'll take this home as a souvenir!", not a complaint).

- [ ] GameTest: a visitor with needs (70, 40), sale index 3, souvenir roll 0.2 and last stop done saves and loads them unchanged. A tag without the new keys loads with rolled needs and sale index 0.
- [ ] Implement, run `tools/gametest.sh`, commit.

### Task 3: Errands, shopping and early departure

**Files:**
- Create: `visitor/VisitorErrands.java` (builds `PlannerInput` from the visitor and `FacilityCensus.cached(zoo)`; picks a shop and item with `ProductChoice`; walks to the counter; on arrival calls `ZooManager.sell` with key `sale:<visitId>:<saleIndex>`; on SOLD it deducts the budget, increments `saleIndex`, adds to `spentInShops`, holds the item and starts eating or carrying; on failure it reacts and records a missed sale, tries one other shop at most, then starts a 30-second cooldown for that need), `shop/FacilityReach.java` (runtime: `markUnreachable(Zoo, UUID facilityId, long until)`, `isUnreachable(Zoo, UUID, long now)`, `clear(Zoo, UUID)`)
- Modify: `visitor/VisitorController.java` (in EXPLORING: tick needs each step; urgent and overdue handling; a "can't afford" rule; target choice goes through `VisitorErrands.chooseTarget`; arrival and failure are passed to the errand when one is active; the souvenir last stop happens before LEAVING when leaving by choice), `visitor/VisitorExploration.java` (exhibit choice unchanged, now called by the errand helper for EXHIBIT)

Rules from spec 8.1 to 8.3:
- Each step while exploring: `needs.tick(0.5)`. Crossing urgent says `NEED_TOILET` or `STARVING` once per visit, then satisfaction falls by 0.05 per step while urgent. When a need is overdue, the visitor says `HUNGRY_NO_FOOD` or `NO_TOILET` and leaves.
- When the planner gives no errand for a need over the threshold (`unmet`), the visitor says `HUNGRY_NO_FOOD` or `NO_TOILET` once per visit.
- Can't afford: if hungry, the snapshot has food in some usable shop, and the visitor's budget is below the cheapest food or drink, it says `CANT_AFFORD` once and starts leaving after its current activity. This applies only when food exists in the zoo.
- Eating: hold the item for 8 seconds with eating sounds and item particles every half second, then `needs.eat(drink)`, clear the hand and say `TASTY` (remark, rate-limited). Souvenir: hold it for 10 seconds, say `SOUVENIR`, then clear. While holding, the visitor keeps exploring.
- Errands win over following the group leader.

- [ ] GameTest (FLOOR_LARGE, timeout 1200 ticks): a hungry visitor (hunger 70) near a shop with 5 bread walks there and buys once: the chest has 4, the treasury rises by 2, the budget falls by 2, and hunger drops below 70 within the timeout.
- [ ] GameTest: hungry with no shop in the zoo: the `hungry_no_food` tally is exactly 1 after 200 ticks, and the visitor is still exploring.
- [ ] GameTest: hungry with only a sold-out shop: no sale; the shop records a missed sale; within the timeout the visitor is exploring again (not stuck walking back and forth).
- [ ] GameTest: close the zoo while a hungry visitor walks to the shop: it heads for the exit, the chest still has 5 and the treasury is unchanged.
- [ ] GameTest: a visitor with souvenir roll 0, a shop stocked with poppies, and interest set to 0 buys one poppy, then moves to LEAVING.
- [ ] GameTest: hungry with 1 emerald and bread at 2: says `cant_afford` and starts leaving; nothing is sold.
- [ ] Implement, run `tools/gametest.sh`, commit.

### Task 4: Toilets

**Files:**
- Create: `shop/ToiletOccupancy.java` (runtime: `tryOccupy(UUID toiletId, UUID visitorId, long now)` within capacity, `renew`, `release`; entries expire 10 seconds after their last renewal)
- Modify: `visitor/VisitorErrands.java` (TOILET errand: pick the nearest usable toilet not marked unreachable or tried this errand; on arrival occupy, stand 6 seconds renewing the entry, then `needs.useToilet()` and release; if full, wait nearby up to 20 seconds, then try another toilet or say `TOILET_QUEUE`; three failed routes mark the toilet unreachable for 60 seconds and say `CANT_REACH_TOILET`), `shop/FacilitySnapshot.java` and `shop/FacilityCensus.java` (add `ToiletState(id, pos, usable)` to the snapshot, usable = not detached)

- [ ] GameTest: an open toilet near a visitor with toilet need 70 is used, and the need is reset to 0 within the timeout.
- [ ] GameTest (timeout 2600 ticks): a toilet inside a closed stone box is never used; the visitor says `cant_reach_toilet` and `FacilityReach` reports it unreachable.
- [ ] GameTest: with the only toilet filled by two fake occupants that keep renewing, a visitor waits, then says `toilet_queue`.
- [ ] GameTest: an occupant that stops renewing is gone after 10 seconds (call `ToiletOccupancy` directly with game times).
- [ ] Implement, run `tools/gametest.sh`, commit.

### Task 5: Play-test checklist, manual run and wrap-up

**Files:**
- Create: `docs/playtests/m6-needs.md`
- Modify: `CLAUDE.md` (code map: `core.needs`, `VisitorErrands`, `ToiletOccupancy`, `FacilityReach`)

- [ ] Manual (dev server and client, scripted over RCON): a zoo with a café of bread and honey, a souvenir stall and two toilets. Visitors eat with bread in their arms, use the toilets, and the café's stock and the treasury move. Emptying the café produces "They've sold out!" and restocking stops it.
- [ ] Run `./gradlew build` and `tools/gametest.sh`, commit.
