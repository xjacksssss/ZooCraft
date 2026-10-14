# M5 Shops and Stock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Shops designated with the Zoo Designation Tool, physical stock in linked containers, per-item prices, an exactly-once sale, stock protection, shop and toilet outlines, and a Shops tab on a tab-scoped dashboard (Phase 2 spec sections 4, 6 and 7).

**Architecture:** Pure rules live in `core.shop` (`ProductRules`, `PriceRules`, `Sale` over a `StockSource` interface) and are JUnit-tested. `Shop` and `Toilet` are records in `com.zoocraft.zoo` next to `Gate`, so only `ZooManager` can change them through package-private mutators, and they are stored in a nested `ZooFacilities` field so the zoo codec stays under 16 fields and Phase 1 saves decode. The `shop` package holds the Minecraft adapters: `ShopStock` (NeoForge item handler capability), `ProductCatalog` (item to facts), `FacilityCensus` (container validation and stock summary on the census timer) and `StockProtection` (events).

**Tech Stack:** NeoForge 21.1.251 `Capabilities.ItemHandler.BLOCK`, data components (`DeferredRegister.DataComponents`), item tags, `InputEvent.MouseScrollingEvent`, payloads, vanilla screens.

**Spec:** `docs/superpowers/specs/2026-10-14-phase2-management-loop-design.md` (sections 4, 6, 7)

**Execution note:** The user asked for uninterrupted execution (memory: autonomous build). Native execution in this session.

## Global Constraints

- Every change to shops, toilets and prices goes through `ZooManager` on the server thread, with `ZooPermission.EDIT` for designations and `ZooPermission.SETTINGS` for prices, names and open/close.
- Shop prices are whole emeralds from 1 to 999. Default prices: food 2, drink 1, souvenir 3.
- Limits: `maxShopsPerZoo` 16, `maxContainersPerShop` 4, `shopLinkRadius` 6.
- A sale is one call on the server thread: check, take one item, then pay. Key `sale:<visitId>:<saleIndex>`.
- Phase 1 saves must load: new fields use `optionalFieldOf` with empty defaults.
- British English in player-facing text.
- Deviation from spec 7.4, noted: containers are looked up with `level.getCapability(...)` on each read instead of `BlockCapabilityCache`. Reads happen only on the census timer, at a sale and for dashboard viewers, so caching isn't worth its lifecycle bookkeeping.

## Review Focus

- A linked container broken after the census but before a sale: the sale fails with OUT_OF_STOCK and changes nothing (GameTest in Task 4).
- A shulker box or bundle with contents in stock: never sold, shown as "Not for sale" (JUnit in Task 2).
- A shop whose counter ends up outside the boundary after an edit: detached, and sales are refused with SHOP_CLOSED (GameTest in Task 4).
- Linking a container that is outside the zoo, in another zoo, or already stock for another shop: refused with its own message (GameTest in Task 4).
- Setting a price for an unsellable item, or out of range: refused (GameTest in Task 4).

---

### Task 1: Ledger shop sales

**Files:**
- Modify: `core/ledger/TransactionType.java` (add `SHOP_SALE(1)`), `core/ledger/Ledger.java` (`totalShopSales`, new constructor parameter), `zoo/ZooCodecs.java` (`optionalFieldOf("totalShopSales", 0L)`)
- Test: `src/test/java/com/zoocraft/core/ledger/LedgerTest.java`

**Produces:** `Ledger.totalShopSales()`; `Ledger(long balance, long totalAdmission, long totalShopSales, long totalDeposits, long totalWithdrawals, Collection<Transaction> recent, Collection<String> keys)`.

- [ ] Test: a `SHOP_SALE` adds to balance and `totalShopSales`; a repeated sale key is `DUPLICATE` and changes nothing.
- [ ] Implement, run `./gradlew test --tests "com.zoocraft.core.ledger.*"`, commit.

### Task 2: Product rules, price rules and the sale

**Files:**
- Create: `core/shop/ProductCategory.java`, `core/shop/ProductFacts.java`, `core/shop/ProductRules.java`, `core/shop/PriceRules.java`, `core/shop/StockSource.java`, `core/shop/SaleRequest.java`, `core/shop/SaleResult.java`, `core/shop/Sale.java`
- Test: `src/test/java/com/zoocraft/core/shop/ProductRulesTest.java`, `PriceRulesTest.java`, `SaleTest.java`

**Produces:**

```java
public enum ProductCategory { UNSELLABLE, FOOD, DRINK, SOUVENIR }

public record ProductFacts(boolean unsellableTag, boolean spawnEgg, boolean hasContents,
                           boolean drinkTag, boolean foodComponent, boolean foodTag, boolean notVisitorFood) {}

public final class ProductRules {
    public static ProductCategory classify(ProductFacts f) {
        if (f.unsellableTag() || f.spawnEgg() || f.hasContents()) return ProductCategory.UNSELLABLE;
        if (f.drinkTag() && !f.notVisitorFood()) return ProductCategory.DRINK;
        if ((f.foodComponent() || f.foodTag()) && !f.notVisitorFood()) return ProductCategory.FOOD;
        return ProductCategory.SOUVENIR;
    }
}

public final class PriceRules {
    public static final int MIN_PRICE = 1, MAX_PRICE = 999;
    public static boolean valid(int price);
    public static double profileShopFactor(VisitorProfile p); // 0.8 budget, 1.0 enthusiast, 1.2 general
    /** categoryDefault × profileShopFactor × personalFactor × (0.5 + reputation / 100) */
    public static double maxAcceptable(int categoryDefault, VisitorProfile p, double personalFactor, double reputation);
    public static boolean accepts(int price, int categoryDefault, VisitorProfile p, double personalFactor, double reputation);
}

/** Physical stock. Implementations must not change anything in canTake. */
public interface StockSource {
    boolean canTake(String itemId);
    /** Removes one unit; false if nothing was removed. */
    boolean take(String itemId);
    /** Puts back the unit removed by the last successful take. */
    void undoTake();
}

public record SaleRequest(String key, String itemId, int price, int budget, boolean shopUsable, long gameTime, String actor) {}
public enum SaleResult { SOLD, SHOP_CLOSED, DUPLICATE, CANT_AFFORD, OUT_OF_STOCK, INVALID_PRICE; public boolean sold(); }

public final class Sale {
    public static SaleResult attempt(Ledger ledger, StockSource stock, SaleRequest r) {
        if (!r.shopUsable()) return SaleResult.SHOP_CLOSED;
        if (!PriceRules.valid(r.price())) return SaleResult.INVALID_PRICE;
        if (ledger.hasKey(r.key())) return SaleResult.DUPLICATE;
        if (r.price() > r.budget()) return SaleResult.CANT_AFFORD;
        if (!stock.canTake(r.itemId()) || !stock.take(r.itemId())) return SaleResult.OUT_OF_STOCK;
        LedgerResult paid = ledger.apply(new Transaction(r.key(), TransactionType.SHOP_SALE, r.price(), r.gameTime(), r.actor()));
        if (!paid.applied()) { stock.undoTake(); throw new IllegalStateException("sale payment failed after checks: " + paid); }
        return SaleResult.SOLD;
    }
}
```

The caller deducts the buyer's budget only on `SOLD`. `Sale` never touches the budget, so a visitor and the debug command share it.

- [ ] Tests: `ProductRulesTest` covers each rule in spec 7.2, including food with contents (UNSELLABLE), a drink that is also in `not_visitor_food` (SOUVENIR), a modded food with only the food component (FOOD), and an ordinary block (SOUVENIR).
- [ ] Tests: `PriceRulesTest` covers bounds, the reputation-50 general visitor accepting up to 1.2 times the default, and budget-conscious visitors being stricter than general ones.
- [ ] Tests: `SaleTest` uses a fake `StockSource` (a counting map). Success takes exactly one unit, adds one ledger entry and returns SOLD. Each of closed, invalid price, duplicate key, can't afford, `canTake` false and `take` false leaves the stock count, ledger balance and key set unchanged. A ledger that rejects the payment (simulated with a key added between canTake and take) triggers `undoTake`.
- [ ] Run `./gradlew test --tests "com.zoocraft.core.shop.*"`, commit.

### Task 3: Shop and toilet records in the zoo

**Files:**
- Create: `zoo/Shop.java`, `zoo/ShopStats.java`, `zoo/Toilet.java`, `zoo/ZooFacilities.java`
- Modify: `zoo/Zoo.java` (field `facilities`, `optionalFieldOf("facilities", ZooFacilities.empty())`; getters `shops()`, `shop(UUID)`, `toilets()`, `isDetached(BlockPos)`), `zoo/ZooCodecs.java`
- Test: `gametest/ShopGameTests.java` (codec round trip and Phase 1 decode)

**Produces:**

```java
/** Records in com.zoocraft.zoo. Mutators are package-private, for ZooManager only. */
public final class Shop {
    UUID id(); String name(); BlockPos counter(); List<BlockPos> containers(); boolean open();
    Map<String, Integer> prices(); ShopStats stats();
    Optional<Integer> price(String itemId);
}
public final class ShopStats {   // rolls over with the zoo day, like ZooStats
    long revenueTotal(); long unitsTotal(); long revenueToday(); int unitsToday();
    Map<String, Integer> soldToday(); Map<String, Integer> missedToday(); // key "reason:itemId"
}
public record Toilet(UUID id, BlockPos pos) {}
public final class ZooFacilities { List<Shop> shops; List<Toilet> toilets; static ZooFacilities empty(); }
```

- [ ] GameTest: a zoo with one shop (two containers, two prices, some stats) and one toilet encodes to NBT and decodes equal.
- [ ] GameTest: an NBT zoo with the `facilities` and `ledger.totalShopSales` keys removed (the Phase 1 shape) decodes with no shops, no toilets and a 0 shop total, and keeps its treasury.
- [ ] Implement, run `tools/gametest.sh`, commit.

### Task 4: Stock adapter, catalogue and ZooManager operations

**Files:**
- Create: `shop/ShopStock.java` (a `StockSource` over a shop's containers, plus `read(ServerLevel, Shop) -> List<StockLine>`), `shop/ProductCatalog.java` (`facts(ItemStack)`, `category(ItemStack)`, `itemId(ItemStack)`, `defaultPrice(ProductCategory)`), `shop/ContainerPositions.java` (`hasItemHandler(level, pos)`, `canonical(level, pos)` for double chests)
- Modify: `registry/ZooTags.java` (item tags `unsellable`, `shop_drinks`, `shop_food`, `not_visitor_food`), `zoo/ZooManager.java`, `config/ZooConfig.java` (a `shops` section with the six keys from spec 12)
- Create: `src/main/resources/data/zoocraft/tags/item/unsellable.json`, `shop_drinks.json`, `shop_food.json`, `not_visitor_food.json`
- Test: `gametest/ShopGameTests.java`

**Produces (ZooManager):**

```java
ActionResult createShop(ServerPlayer player, ServerLevel level, BlockPos counter);
ActionResult removeShop(ServerPlayer player, ServerLevel level, BlockPos near);
ActionResult linkContainer(ServerPlayer player, ServerLevel level, BlockPos container);
ActionResult unlinkContainer(ServerPlayer player, ServerLevel level, BlockPos container);
ActionResult renameShop(ServerPlayer player, Zoo zoo, UUID shopId, String name);
ActionResult setShopOpen(ServerPlayer player, Zoo zoo, UUID shopId, boolean open);
ActionResult setItemPrice(ServerPlayer player, Zoo zoo, UUID shopId, String itemId, int price);
ActionResult createToilet(ServerPlayer player, ServerLevel level, BlockPos pos);
ActionResult removeToilet(ServerPlayer player, ServerLevel level, BlockPos near);
/** For visitors and /zoo debug sell. The caller deducts the budget only on SOLD. */
SaleResult sell(ServerLevel level, Zoo zoo, UUID shopId, String key, String itemId, int budget, String actor);
void recordMissedSale(Zoo zoo, UUID shopId, SaleResult reason, String itemId);
Optional<Shop> shopOwningContainer(Level level, BlockPos pos);   // used by StockProtection
```

Rules (spec 6 and 7): the counter must be inside a zoo the player can EDIT and must not already be a counter, gate or toilet within 1 block. The linked container must expose the item handler, must be inside the same zoo, within `shopLinkRadius` of the nearest counter, and not linked anywhere (checking both halves of a double chest). `sell` uses the stored price, `zoo.isDetached(counter)` and `shop.open()` for `shopUsable`, records stats on SOLD and a missed sale otherwise, marks the data dirty and fires change listeners.

Messages (lang): `zoocraft.msg.shop_created`, `shop_removed`, `no_shop_here`, `shop_limit`, `shop_exists`, `not_a_container`, `no_counter_near`, `container_linked`, `container_unlinked`, `container_already_linked`, `container_limit`, `container_other_zoo`, `shop_renamed`, `shop_opened`, `shop_closed`, `item_price_set`, `not_for_sale`, `bad_shop_price`, `toilet_created`, `toilet_removed`, `no_toilet_here`, `not_in_zoo`.

- [ ] GameTests: create a counter, link a chest holding 5 bread, sell with key K and budget 10: SOLD, chest has 4, treasury +2. Sell again with K: DUPLICATE, chest still 4, treasury unchanged.
- [ ] GameTests: empty the chest, then sell: OUT_OF_STOCK, nothing changes, and a missed sale is recorded.
- [ ] GameTests: break the chest (`setBlock` to air), then sell: OUT_OF_STOCK, nothing changes.
- [ ] GameTests: move the boundary so the counter is outside, then sell: SHOP_CLOSED.
- [ ] GameTests: budget 1 for a price-2 item: CANT_AFFORD, nothing changes.
- [ ] GameTests: link both halves of a double chest: the second half is refused as already linked.
- [ ] GameTests: linking a chest outside the zoo, 10 blocks from the counter, or already linked to another shop is refused.
- [ ] GameTests: `setItemPrice` refuses 0, 1000 and an unsellable item (`minecraft:command_block`); a manager can set a price, a stranger can't.
- [ ] Implement, run `tools/gametest.sh`, commit.

### Task 5: Facility census and stock protection

**Files:**
- Create: `shop/FacilityCensus.java` (on the census timer, for each zoo with shops: unlink containers that no longer expose the handler and raise an alert; build a cached `FacilitySnapshot` of per-shop usable flag, in-stock item IDs with counts and prices, and the cheapest price per category. M6 reads it.), `shop/FacilitySnapshot.java`, `shop/StockProtection.java` (`@SubscribeEvent` on `PlayerInteractEvent.RightClickBlock` and `BlockEvent.BreakEvent`; `static boolean mayAccess(ServerPlayer, Zoo)`)
- Modify: `zoo/ZooAlerts.java` (alerts carry a `Component`: `raise(Zoo, String id, Component text)`, `clear(Zoo, String id)`, `messages(Zoo) -> List<Component>`; the old key-only calls become `raise(zoo, key, Component.translatable(key))`), `dashboard/DashboardService.java` (use `messages`), `zoo/ZooManager.java` (an internal `unlinkMissing(Zoo, Shop, BlockPos)`)
- Test: `gametest/ShopGameTests.java`

- [ ] GameTest: break a linked chest and run `FacilityCensus.refresh(zoo, level)`: the link is gone and the alert "A stock container at (x, y, z) was removed." is active.
- [ ] GameTest: `StockProtection.mayAccess` is false for a stranger and true for the owner, a manager and an operator. The event handler cancels for a stranger (call the handler with a constructed event).
- [ ] GameTest: after `FacilityCensus.refresh`, the snapshot lists bread with count 5 and price 2, and the cheapest food is 2.
- [ ] Implement, run `tools/gametest.sh`, commit.

### Task 6: Designation tool modes

**Files:**
- Create: `item/DesignationMode.java` (enum GATE, SHOP, TOILET with `Codec` and `StreamCodec`, `next(int direction)`), `registry/ZooComponents.java` (`DESIGNATION_MODE` data component), `network/ToolModePayload.java` (C2S, `int direction`), `client/ToolScrollHandler.java` (`InputEvent.MouseScrollingEvent`: when sneaking and holding the tool, cancel and send the payload)
- Modify: `item/DesignationToolItem.java` (mode-aware `click`; sneak-use on air cycles the mode; `getName` appends the mode; tooltips per mode), `network/ZooNetwork.java`, `ZooCraft.java` (register components), lang
- Test: `gametest/ToolGameTests.java`

**Produces:** `DesignationToolItem.mode(ItemStack) -> DesignationMode`, `DesignationToolItem.setMode(ItemStack, DesignationMode)`, `DesignationToolItem.click(ServerPlayer, ServerLevel, BlockPos, DesignationMode)`. The old three-argument `click` stays and uses GATE, so the Phase 1 tests keep working.

Click rules: GATE as in Phase 1. SHOP: if the clicked block has an item handler, sneak unlinks it, otherwise it links. Otherwise the feet position is used, where sneak removes a shop and a plain click creates one. TOILET: sneak removes, a plain click creates.

- [ ] GameTests: in SHOP mode, a click on the floor creates a counter, a click on a chest links it, and a sneak-click on the chest unlinks it. In TOILET mode, a click creates a toilet. Cycling from TOILET with direction +1 gives GATE.
- [ ] Implement, run `tools/gametest.sh`, commit.

### Task 7: Outlines for shops and toilets

**Files:**
- Modify: `network/ZooOutlinesPayload.java` (`Outline` gains `List<ShopMarker> shops` with counter, containers and status, and `List<ToiletMarker> toilets` with pos and status; status 0 ok, 1 detached or problem), `item/ToolOutlineSync.java`, `client/BoundaryRenderer.java` (counter box gold, lines from the counter to each container plus a container box, toilet box blue, any problem red)

- [ ] Build and check in game with `runClient` (manual, screenshot via `tools/screenshot.ps1`), commit.

### Task 8: Tab-scoped dashboard and the Shops tab

**Files:**
- Modify: `network/DashboardSnapshot.java` (adds `int tab`, `List<ShopLine> shops`, `List<ToiletLine> toilets`, and `long shopIncome`; tab-specific lists are empty when the tab isn't being viewed), `network/DashboardPayloads.java` (`SetTab(int tab)` C2S; `Action` text limit raised to 160), `dashboard/DashboardAction.java` (`SHOP_PRICE` with text `shopId|itemId` and number price, `SHOP_OPEN` with text shopId and number 1/0, `SHOP_RENAME` with text `shopId|name`), `dashboard/DashboardService.java` (a subscription holds the zoo ID and tab; the snapshot fills species and exhibits only for ANIMALS, gates and managers only for SETTINGS, shops and toilets only for SHOPS; alerts, comments and the header always), `client/DashboardScreen.java` (tabs Overview, Animals, Shops, Settings; sends `SetTab` on switch; Shops tab), `network/ZooNetwork.java`, lang

**Produces:**

```java
public record ShopLine(UUID id, String name, int status /* 0 open, 1 closed, 2 sold out, 3 detached */, long revenueToday,
                       boolean stockUnknown, List<ProductLine> products) {}
public record ProductLine(ItemStack icon, String itemId, int category /* ProductCategory ordinal */, int stock, int price,
                          int soldToday, int missedToday) {}
public record ToiletLine(BlockPos pos, boolean detached) {}
```

Shops tab layout: a left column (120 px) lists shop buttons, then toilets. The right side shows the selected shop's name box with Rename, an Open/Close button and revenue today, then product rows with icon, name, category, stock (red at 0, gold at 4 or fewer), price with − and + buttons, and sold today. Unsellable rows show "Not for sale". The rows scroll.

- [ ] GameTest: a SHOPS-tab snapshot holds shop and product lines and no species or managers; an OVERVIEW snapshot holds no shop lines.
- [ ] GameTest: `DashboardService.handle` with `SHOP_PRICE` sets the price; a stranger's request is refused.
- [ ] Manual: open the Shops tab, change a price with + and −, rename the shop, close and reopen it.
- [ ] Run `./gradlew build` and `tools/gametest.sh`, commit.

### Task 9: Commands, play-test checklist and wrap-up

**Files:**
- Modify: `command/ZooCommands.java` (`/zoo shop list|price|open|close|rename`, with shops named or matched by name within the zoo), `command/ZooDebugCommands.java` (`/zoo debug sell <shop> <item>` sells one unit to a fake buyer with a budget of 999 and a random key, and reports the result), lang
- Create: `docs/playtests/m5-shops.md`
- Modify: `CLAUDE.md` (code map: `shop` package and `core.shop`)

- [ ] Run `./gradlew build` and `tools/gametest.sh`; all pass.
- [ ] Manual: `runClient` with a barrel of bread and a chest of flowers; set prices on the Shops tab; `/zoo debug sell` lowers the stock and raises the treasury once per call; a second player who isn't a manager can't open the chest (runServer and two clients).
- [ ] Commit.
