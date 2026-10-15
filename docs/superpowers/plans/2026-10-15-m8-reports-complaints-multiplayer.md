# M8 Reports, Complaints and Multiplayer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Daily finance history, a recommendation rate, complaints turned into ranked advice that names the cause and the place, Visitors and Finances dashboard tabs, and the Phase 2 multiplayer, save-compatibility and ATM10 checks (Phase 2 spec sections 10 and 11).

**Architecture:** Pure logic in `core.report`: `DayRecord` and `FinanceHistory` (28 days of daily totals), `RecommendationWindow` (the last 100 departures) and `Advice` (ranks issues by count × severity). All of it is JUnit-tested. On the Minecraft side, a single new saved field on `Zoo`, `reports` (`ZooReports`), holds the history, the window and today's complaint subjects. That keeps the zoo codec at 16 fields and lets Phase 1 saves load. `ZooManager` feeds the history from every money and visitor event it already handles. `AdviceBuilder` in `dashboard` gathers issues from tallies, subjects, shop missed sales, bins and hotspots, and words them. The dashboard gains Visitors and Finances tabs; the Overview shows the top 5 advice lines and the recommendation rate.

**Tech Stack:** Codecs (a `DayRecord` is stored as a list of longs so fields can be added later), the existing hand-written snapshot codec, vanilla screen drawing for the bar chart.

**Spec:** `docs/superpowers/specs/2026-10-14-phase2-management-loop-design.md` (sections 10 and 11)

**Execution note:** Autonomous build (user memory). Native execution on branch `phase2`.

## Global Constraints

- History keeps 28 in-game days; the bar chart shows the last 14. Days roll over when `overworld game time / 24000` changes, the same as Phase 1 attendance.
- The recommendation rate is the share of the last 100 departing visitors with satisfaction of 70 or more, shown as "N in 10 visitors would recommend your zoo".
- The Overview shows the top 5 advice lines; the Visitors tab shows all of them. Advice is rebuilt from today's data on every snapshot.
- Everything stays server-authoritative; snapshots carry only the tab being viewed.
- New saved fields are optional, with fresh (never shared) defaults.

## Review Focus

- A Phase 1 zoo (no `reports` field) loads with empty history, and two such zoos never share one history object (Task 2: GameTest through `reloadForTest`, the same trap as the M6 facilities bug).
- Day rollover while the server is down for several in-game days: the history must not invent empty days in between, and today's counters must start at zero (Task 1: JUnit `roll` across a gap).
- A shop or toilet named in an advice subject has since been removed: advice must skip it, not show a raw UUID (Task 3: GameTest).
- Huge numbers (a treasury in the millions) must not overflow the bar chart scaling (Task 1: `FinanceHistory` uses longs; Task 3 scales with doubles).
- A zoo with no departures yet shows "no visitors have left yet" rather than 0 in 10 (Task 3: wording check in GameTest).

---

### Task 1: Finance history, recommendations and advice ranking

**Files:**
- Create: `core/report/DayRecord.java`, `core/report/FinanceHistory.java`, `core/report/RecommendationWindow.java`, `core/report/Advice.java`
- Test: `src/test/java/com/zoocraft/core/report/FinanceHistoryTest.java`, `RecommendationWindowTest.java`, `AdviceTest.java`

**Produces:**

```java
/** Totals for one in-game day. Mutable while it is "today". toList()/fromList() for saving (missing values read as 0). */
public final class DayRecord {
    public long day, admission, shopSales, deposits, withdrawals, closingBalance;
    public long arrivals, refusals, departures, satisfactionTotal, spending, visitTicks, meals, toiletVisits;
    public long budgetVisitors, enthusiastVisitors, generalVisitors;
    public List<Long> toList();  public static DayRecord fromList(List<Long> values);
    public long income();         // admission + shopSales
}
public final class FinanceHistory {
    public static final int DAYS = 28;
    public FinanceHistory();  public FinanceHistory(DayRecord today, List<DayRecord> past);
    public DayRecord today();
    public List<DayRecord> past();                   // oldest first, at most DAYS - 1
    /** Starts a new day if {@code day} differs from today's, closing today with {@code balance}. */
    public void roll(long day, long balance);
    public List<DayRecord> lastDays(int count);      // past plus today, oldest first
}
public final class RecommendationWindow {
    public static final int SIZE = 100; public static final double THRESHOLD = 70;
    public void add(double satisfaction);  public int size();  public int recommended();
    public String encode(); public static RecommendationWindow decode(String s);   // "1"/"0" per departure
}
public final class Advice {
    public record Issue(String kind, List<String> args, int count) {}
    public static int weight(String kind);           // severity per kind, 1 when unknown
    public static List<Issue> rank(List<Issue> issues, int limit);   // count × weight, descending; zero counts dropped
}
```

Weights: `could_not_leave` 5, `hungry_no_food` and `no_toilet` 4, `sold_out`, `cant_reach_shop`, `cant_reach_toilet` and `bin_full` 3, `toilet_queue`, `rubbish` and `cant_reach_exhibit` 2, everything else 1.

- [ ] JUnit `FinanceHistoryTest`: rolling to the next day closes today with the balance and starts a zeroed today; rolling to the same day does nothing; a gap of 5 days adds exactly one closed record (no invented empty days); after 40 rolls `past()` holds 27 records; `lastDays(14)` is oldest first and ends with today; `DayRecord` survives `toList`/`fromList`, and a short list reads missing values as 0.
- [ ] JUnit `RecommendationWindowTest`: keeps only the last 100; 70 counts as recommending, 69.9 doesn't; encode/decode round-trips.
- [ ] JUnit `AdviceTest`: ranks by count × weight; drops zero counts; respects the limit; an unknown kind has weight 1.
- [ ] Run `./gradlew test`, commit.

### Task 2: Recording reports on the zoo

**Files:**
- Create: `zoo/ZooReports.java` (history, recommendations, and today's complaint subjects `Map<String, Integer>` keyed `"<comment>|<subject>"`, cleared on rollover; codec with a list-of-longs `DayRecord` codec)
- Modify: `zoo/Zoo.java` (field `reports`, `optionalFieldOf("reports")` mapped to a fresh `ZooReports` when missing), `zoo/ZooManager.java` (one private `rollDay(Zoo)` used by every recorder; `chargeAdmission` gains the visitor's profile; `sell`, `deposit`, `withdraw`, `recordRefusal` feed today's record; `recordDeparture(Zoo, double satisfaction, int spent, long visitTicks)`; new `recordMeal(Zoo)`, `recordToiletVisit(Zoo)`, `recordComment(Zoo, String key, @Nullable String subject)`; `reloadForTest` also strips `reports` in the Phase 1 shape; `rollDayForTest(Zoo, long day)`), `zoo/ShopStats.java` (daily revenue history for 28 days as `[day, revenue]` pairs, optional in the codec), `visitor/Admission.java`, `visitor/Visitor.java` (`remark(CommentType, String subject)`; departure passes spending and visit length), `visitor/VisitorErrands.java` (subjects: shop ID for `CANT_REACH_SHOP`, toilet ID for `TOILET_QUEUE` and `CANT_REACH_TOILET`; `recordMeal` after eating; `recordToiletVisit` after using a toilet), `visitor/VisitorExploration.java` (`RUBBISH_EVERYWHERE` with the 16 × 16 cell as subject)
- Test: `gametest/ReportGameTests.java`

- [ ] GameTest: an admission, a sale, a deposit and a withdrawal show up in today's record with the right amounts, and match the ledger totals.
- [ ] GameTest: `rollDayForTest` to the next day closes the record with the treasury as closing balance, and today starts at zero.
- [ ] GameTest: two zoos reloaded in the Phase 1 shape get separate, empty reports; recording in one doesn't touch the other; a normal reload keeps the history.
- [ ] GameTest: a departure with satisfaction 80 adds a recommendation; 40 doesn't.
- [ ] GameTest: a comment recorded with a subject appears in the subject tally.
- [ ] Implement, run `tools/gametest.sh`, commit.

### Task 3: Advice, Visitors and Finances tabs

**Files:**
- Create: `dashboard/AdviceBuilder.java` (gathers `Advice.Issue`s from today's comment tally, the subject tally (resolving shop and toilet IDs to names and positions, skipping ones that no longer exist), shop missed sales (`out_of_stock` → `sold_out` with shop and item names; `too_expensive` with shop, item and current price), full bins and litter hotspots; ranks them; words each as `zoocraft.advice.<kind>`)
- Modify: `dashboard/DashboardTab.java` (OVERVIEW, ANIMALS, SHOPS, VISITORS, FINANCES, SETTINGS), `network/DashboardSnapshot.java` (`List<Component> advice`, `int recommended`, `int recommendSample`, `VisitorPanel visitors` and `FinancePanel finances`, each with an `empty()` for other tabs), `dashboard/DashboardService.java`, `client/DashboardScreen.java` (six tabs; Overview right column shows "Needs attention" alerts and the top 5 advice lines; Visitors and Finances tabs; the bar chart), lang (advice and tab strings)

```java
public record VisitorPanel(int arrivals, int refusals, int departures, double averageMinutes, double averageSpend,
                           int budget, int enthusiast, int general, int meals, int hungryUnmet, int toiletVisits, int toiletUnmet,
                           List<CommentLine> comments) {}
public record FinancePanel(long admissionToday, long shopToday, long depositsToday, long withdrawalsToday,
                           List<DayBar> days, List<ShopRevenue> shops, List<Component> recent) {}
public record DayBar(long day, long admission, long shop) {}
public record ShopRevenue(String name, long today, long week, long total) {}
```

- [ ] GameTest: a VISITORS snapshot has the visitor panel and no shop lines; a FINANCES snapshot has today's income split and up to 14 day bars ending today; an OVERVIEW snapshot has neither panel.
- [ ] GameTest: a sold-out item produces the advice line "<shop> ran out of <item>. <n> visitors left without buying." in the Overview.
- [ ] GameTest: a toilet-queue complaint whose toilet has been removed produces no advice line with a raw ID.
- [ ] GameTest: with no departures the Overview's recommendation sample is 0 (the screen says "No visitors have left yet").
- [ ] Manual: open each tab in the dev client and take screenshots.
- [ ] Run `./gradlew build` and `tools/gametest.sh`, commit.

### Task 4: Multiplayer, compatibility and Phase 2 wrap-up

**Files:**
- Create: `docs/playtests/m8-reports.md`
- Modify: `docs/compatibility.md` (Phase 2 results), `CLAUDE.md` (Phase 2 complete; code map)

- [ ] Manual (dev server and two clients, Dev and Manager): the owner adds Manager; both open the Shops tab; a price changed by one appears on the other; Manager restocks a chest and the owner's stock count updates; the owner removes Manager, whose dashboard closes and who can no longer open the stock chest. Scripted over RCON where the UI isn't needed.
- [ ] Manual: stop and restart the server with visitors mid-purchase and mid-meal; no double charges; the shop, toilets, bins, litter and history are all still there.
- [ ] Manual (ATM10 copy, `-Patm10`): the mod loads; Farmer's Delight foods are classed as food; a Sophisticated Storage chest links as stock; litter isn't picked up by magnets; results go in `docs/compatibility.md`.
- [ ] Run `./gradlew build` and `tools/gametest.sh`; final self-review of Phase 2 against the spec's section 1 success criteria; commit.
