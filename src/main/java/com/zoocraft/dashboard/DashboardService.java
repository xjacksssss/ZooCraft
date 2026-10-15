package com.zoocraft.dashboard;

import com.zoocraft.core.perf.PerfSlot;
import com.zoocraft.perf.SimulationTimers;
import com.mojang.authlib.GameProfile;
import com.zoocraft.ZooCraft;
import com.zoocraft.animal.AnimalCensus;
import com.zoocraft.animal.Exhibit;
import com.zoocraft.animal.ZooCensus;
import com.zoocraft.config.ZooConfig;
import com.zoocraft.core.report.DayRecord;
import com.zoocraft.core.shop.ProductCategory;
import com.zoocraft.feedback.CommentType;
import com.zoocraft.network.DashboardPayloads;
import com.zoocraft.network.DashboardSnapshot;
import com.zoocraft.shop.ProductCatalog;
import com.zoocraft.shop.FacilityCensus;
import com.zoocraft.shop.FacilitySnapshot;
import com.zoocraft.core.litter.LitterHotspots;
import com.zoocraft.litter.LitterTracker;
import com.zoocraft.shop.ShopStock;
import com.zoocraft.visitor.VisitorTracker;
import com.zoocraft.zoo.ActionResult;
import com.zoocraft.zoo.Gate;
import com.zoocraft.zoo.GateStatus;
import com.zoocraft.zoo.PlayerNames;
import com.zoocraft.zoo.Shop;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooAlerts;
import com.zoocraft.zoo.ZooManager;
import com.zoocraft.zoo.ZooPermission;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Serves the dashboard (spec section 9.3, Phase 2 spec section 4.5). The server is the only source of truth:
 * players subscribe by opening the dashboard, get a fresh snapshot of the tab they are viewing about once a second,
 * and every button is a request that goes through {@link ZooManager} with the usual permission checks.
 */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class DashboardService {
    private static final int REFRESH_TICKS = 20;
    private static final int MAX_COMMENTS = 24;
    private static final int OVERVIEW_ADVICE = 5;
    private static final int ALL_ADVICE = 20;
    private static final int CHART_DAYS = 14;
    private static final int RECENT_TRANSACTIONS = 8;
    private static final int MAX_PRODUCTS = 64;
    private static final int HOTSPOT_CELL = 16;
    private static final int HOTSPOT_MIN_UNITS = 3;
    private static final int HOTSPOT_LIMIT = 3;

    private record Subscription(UUID zooId, DashboardTab tab) {
    }

    private static final Map<UUID, Subscription> SUBSCRIPTIONS = new HashMap<>();

    private DashboardService() {
    }

    /** Opens the dashboard for the requested zoo, the zoo the player is standing in, or their only zoo. */
    public static void open(ServerPlayer player, Optional<UUID> requested) {
        ZooManager zoos = ZooManager.get(player.server);
        Optional<Zoo> zoo = requested.flatMap(zoos::byId).filter(z -> ZooManager.can(player, z, ZooPermission.VIEW));
        if (zoo.isEmpty() && requested.isEmpty()) {
            zoo = zoos.zooAt(player.level(), player.blockPosition()).filter(z -> ZooManager.can(player, z, ZooPermission.VIEW));
        }
        if (zoo.isEmpty()) {
            List<Zoo> mine = zoos.managedBy(player).stream().filter(z -> z.isOwnerOrManager(player.getUUID())).toList();
            if (mine.isEmpty() && player.hasPermissions(2)) {
                mine = zoos.managedBy(player);
            }
            if (mine.size() == 1) {
                zoo = Optional.of(mine.getFirst());
            } else if (mine.size() > 1) {
                PacketDistributor.sendToPlayer(player, new DashboardPayloads.ZooList(mine.stream()
                        .map(z -> new DashboardPayloads.ZooList.Entry(z.id(), z.name(), z.open())).toList()));
                return;
            } else {
                player.sendSystemMessage(Component.translatable("zoocraft.cmd.no_zoos").withStyle(ChatFormatting.GRAY));
                return;
            }
        }
        SUBSCRIPTIONS.put(player.getUUID(), new Subscription(zoo.get().id(), DashboardTab.OVERVIEW));
        PacketDistributor.sendToPlayer(player, snapshot(player, zoo.get(), DashboardTab.OVERVIEW).withOpenScreen(true));
    }

    public static void close(ServerPlayer player) {
        SUBSCRIPTIONS.remove(player.getUUID());
    }

    /** The player switched tab: remember it and send that tab straight away. */
    public static void setTab(ServerPlayer player, int tabOrdinal) {
        Subscription subscription = SUBSCRIPTIONS.get(player.getUUID());
        if (subscription == null) {
            return;
        }
        DashboardTab tab = DashboardTab.byOrdinal(tabOrdinal);
        SUBSCRIPTIONS.put(player.getUUID(), new Subscription(subscription.zooId(), tab));
        ZooManager.get(player.server).byId(subscription.zooId())
                .filter(zoo -> ZooManager.can(player, zoo, ZooPermission.VIEW))
                .ifPresent(zoo -> PacketDistributor.sendToPlayer(player, snapshot(player, zoo, tab)));
    }

    /** Whether the player is currently subscribed to the zoo (for tests). */
    public static boolean subscribed(ServerPlayer player, UUID zooId) {
        Subscription subscription = SUBSCRIPTIONS.get(player.getUUID());
        return subscription != null && zooId.equals(subscription.zooId());
    }

    /** Runs a dashboard button through ZooManager and refreshes everyone watching that zoo. */
    public static ActionResult handle(ServerPlayer player, DashboardPayloads.Action request) {
        ZooManager zoos = ZooManager.get(player.server);
        Optional<Zoo> maybeZoo = zoos.byId(request.zooId());
        if (maybeZoo.isEmpty()) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.zoo_gone"));
        }
        Zoo zoo = maybeZoo.get();
        if (!ZooManager.can(player, zoo, ZooPermission.VIEW)) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.no_permission"));
        }
        ActionResult result = switch (request.action()) {
            case OPEN_ZOO -> zoos.setOpen(player, zoo, true);
            case CLOSE_ZOO -> zoos.setOpen(player, zoo, false);
            case SET_PRICE -> zoos.setPrice(player, zoo, request.number());
            case KEEP_LOADED -> zoos.setKeepLoaded(player, zoo, request.number() != 0);
            case DEPOSIT -> zoos.deposit(player, zoo, request.number());
            case WITHDRAW -> zoos.withdraw(player, zoo, request.number());
            case ADD_MANAGER -> withProfile(player, request.text(), profile -> zoos.addManager(player, zoo, profile));
            case TRANSFER -> withProfile(player, request.text(), profile -> zoos.transfer(player, zoo, profile));
            case REMOVE_MANAGER -> parseId(request.text())
                    .map(id -> zoos.removeManager(player, zoo, id, PlayerNames.name(player.server, id)))
                    .orElseGet(DashboardService::badRequest);
            case SHOP_OPEN -> parseId(request.text())
                    .map(id -> zoos.setShopOpen(player, zoo, id, request.number() != 0))
                    .orElseGet(DashboardService::badRequest);
            case SHOP_PRICE -> withShopAndText(request.text(), (id, itemId) -> zoos.setItemPrice(player, zoo, id, itemId, request.number()));
            case SHOP_RENAME -> withShopAndText(request.text(), (id, name) -> zoos.renameShop(player, zoo, id, name));
        };
        refreshZoo(player.server, zoo);
        return result;
    }

    private static ActionResult withProfile(ServerPlayer player, String name, java.util.function.Function<GameProfile, ActionResult> action) {
        Optional<GameProfile> profile = PlayerNames.profile(player.server, name);
        return profile.map(action).orElseGet(() -> ActionResult.fail(Component.translatable("zoocraft.msg.unknown_player", name)));
    }

    /** For actions whose text is {@code "<shopId>|<value>"}. */
    private static ActionResult withShopAndText(String text, java.util.function.BiFunction<UUID, String, ActionResult> action) {
        int bar = text.indexOf('|');
        if (bar < 0) {
            return badRequest();
        }
        return parseId(text.substring(0, bar)).map(id -> action.apply(id, text.substring(bar + 1))).orElseGet(DashboardService::badRequest);
    }

    private static Optional<UUID> parseId(String text) {
        try {
            return Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static ActionResult badRequest() {
        return ActionResult.fail(Component.translatable("zoocraft.msg.no_permission"));
    }

    // ------------------------------------------------------------------ refreshing

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % REFRESH_TICKS == 0 && !SUBSCRIPTIONS.isEmpty()) {
            long timer = SimulationTimers.start();
            try {
                refreshAll(event.getServer());
            } finally {
                SimulationTimers.stop(PerfSlot.DASHBOARD, null, timer);
            }
        }
    }

    static void refreshAll(MinecraftServer server) {
        ZooManager zoos = ZooManager.get(server);
        for (Map.Entry<UUID, Subscription> entry : List.copyOf(SUBSCRIPTIONS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                SUBSCRIPTIONS.remove(entry.getKey());
                continue;
            }
            Optional<Zoo> zoo = zoos.byId(entry.getValue().zooId());
            if (zoo.isEmpty() || !ZooManager.can(player, zoo.get(), ZooPermission.VIEW)) {
                SUBSCRIPTIONS.remove(entry.getKey());
                PacketDistributor.sendToPlayer(player, DashboardPayloads.Closed.INSTANCE);
                player.sendSystemMessage(Component.translatable("zoocraft.msg.dashboard_lost_access").withStyle(ChatFormatting.GRAY));
                continue;
            }
            PacketDistributor.sendToPlayer(player, snapshot(player, zoo.get(), entry.getValue().tab()));
        }
    }

    private static void refreshZoo(MinecraftServer server, Zoo zoo) {
        for (Map.Entry<UUID, Subscription> entry : List.copyOf(SUBSCRIPTIONS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null && entry.getValue().zooId().equals(zoo.id())) {
                if (ZooManager.can(player, zoo, ZooPermission.VIEW) && ZooManager.get(server).byId(zoo.id()).isPresent()) {
                    PacketDistributor.sendToPlayer(player, snapshot(player, zoo, entry.getValue().tab()));
                }
            }
        }
    }

    // ------------------------------------------------------------------ snapshot

    public static DashboardSnapshot snapshot(ServerPlayer viewer, Zoo zoo, DashboardTab tab) {
        MinecraftServer server = viewer.server;
        long now = server.overworld().getGameTime();
        ServerLevel level = server.getLevel(zoo.dimension());
        ZooCensus census = AnimalCensus.cached(zoo);
        if (census.takenAt() < 0 && level != null) {
            census = AnimalCensus.refresh(zoo, level);
        }

        List<Component> alerts = new ArrayList<>();
        if (zoo.gates().isEmpty()) {
            alerts.add(Component.translatable("zoocraft.alert.no_gates"));
        }
        alerts.addAll(ZooAlerts.messages(zoo, now));
        for (Gate gate : zoo.gates()) {
            GateStatus status = zoo.gateStatus(gate, now);
            if (status != GateStatus.OPEN) {
                alerts.add(Component.translatable("zoocraft.alert.gate_" + status.getSerializedName(), gate.pos().getX(), gate.pos().getZ()));
            }
        }
        // Litter (Phase 2 spec section 9): full bins and where litter is piling up.
        for (FacilitySnapshot.BinState bin : FacilityCensus.cached(zoo).bins()) {
            if (!bin.usable()) {
                alerts.add(Component.translatable("zoocraft.alert.bin_full", bin.pos().getX(), bin.pos().getZ()));
            }
        }
        List<LitterHotspots.Point> litter = LitterTracker.litterIn(zoo).stream()
                .map(l -> new LitterHotspots.Point(l.getBlockX(), l.getBlockZ(), l.pile())).toList();
        for (LitterHotspots.Hotspot spot : LitterHotspots.find(litter, HOTSPOT_CELL, HOTSPOT_MIN_UNITS, HOTSPOT_LIMIT)) {
            alerts.add(Component.translatable("zoocraft.alert.litter_hotspot", spot.centreX(), spot.centreZ()));
        }
        List<DashboardSnapshot.ExhibitLine> exhibits = new ArrayList<>();
        for (Exhibit exhibit : census.exhibits()) {
            Component label = speciesLabel(exhibit, census);
            boolean problem = exhibit.viewingProblem(now);
            if (problem) {
                alerts.add(Component.translatable("zoocraft.alert.cant_see", label));
            }
            List<Component> warnings = new ArrayList<>();
            if (exhibit.rating().warnings().contains(com.zoocraft.core.exhibit.ExhibitRating.Warning.CRAMPED)) {
                warnings.add(Component.translatable("zoocraft.warning.cramped", label));
            }
            if (exhibit.rating().warnings().contains(com.zoocraft.core.exhibit.ExhibitRating.Warning.BARE)) {
                warnings.add(Component.translatable("zoocraft.warning.bare", label));
            }
            exhibits.add(new DashboardSnapshot.ExhibitLine(label, exhibit.animals().size(), exhibit.viewCount(), problem,
                    exhibit.rating().score(), exhibit.rating().grade().ordinal(), warnings));
        }
        exhibits.sort(Comparator.comparingInt(DashboardSnapshot.ExhibitLine::views).reversed());

        // Everything visitors said today, for the Visitors tab; the Overview shows advice instead.
        List<DashboardSnapshot.CommentLine> comments = tab != DashboardTab.VISITORS ? List.of() : zoo.stats().commentTally().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(MAX_COMMENTS)
                .map(e -> {
                    CommentType type = CommentType.byKey(e.getKey());
                    Component text = type == null ? Component.literal(e.getKey()) : Component.translatable(type.translationKey());
                    return new DashboardSnapshot.CommentLine(text, e.getValue());
                })
                .toList();

        List<DashboardSnapshot.SpeciesLine> species = new ArrayList<>();
        if (tab == DashboardTab.ANIMALS) {
            ZooCensus finalCensus = census;
            census.speciesCounts().forEach((id, count) -> species.add(new DashboardSnapshot.SpeciesLine(
                    finalCensus.speciesNames().getOrDefault(id, Component.literal(id)), count)));
            species.sort(Comparator.comparingInt(DashboardSnapshot.SpeciesLine::count).reversed());
        } else {
            exhibits.clear();
        }

        List<DashboardSnapshot.GateLine> gates = zoo.gates().stream()
                .map(g -> new DashboardSnapshot.GateLine(g.pos(), zoo.gateStatus(g, now).ordinal())).toList();
        List<DashboardSnapshot.ManagerLine> managers = tab != DashboardTab.SETTINGS ? List.of() : zoo.managers().stream()
                .map(id -> new DashboardSnapshot.ManagerLine(id, PlayerNames.name(server, id))).toList();

        List<DashboardSnapshot.ShopLine> shops = new ArrayList<>();
        List<DashboardSnapshot.ToiletLine> toilets = new ArrayList<>();
        if (tab == DashboardTab.SHOPS && level != null) {
            for (Shop shop : zoo.shops()) {
                shops.add(shopLine(level, zoo, shop));
            }
            zoo.toilets().forEach(t -> toilets.add(new DashboardSnapshot.ToiletLine(t.pos(), zoo.isDetached(t.pos()))));
        }

        return new DashboardSnapshot(false, tab.ordinal(), zoo.id(), zoo.name(), PlayerNames.name(server, zoo.owner()),
                zoo.owner().equals(viewer.getUUID()) || viewer.hasPermissions(2),
                zoo.open(), zoo.admissionPrice(), ZooManager.MAX_PRICE, zoo.keepLoaded(), ZooConfig.ALLOW_ZOO_CHUNK_LOADING.get(),
                zoo.ledger().balance(), zoo.ledger().totalAdmission(), zoo.ledger().totalShopSales(), todaysShopSales(zoo, now),
                zoo.stats().attendanceToday(), zoo.stats().totalAttendance(),
                zoo.stats().refusedAdmissions(), VisitorTracker.count(zoo.id()), zoo.satisfaction(), zoo.reputation(),
                (float) zoo.stats().cleanliness(), zoo.stats().litterCollected(),
                alerts, comments, species, exhibits, gates, managers, shops, toilets,
                tab == DashboardTab.OVERVIEW || tab == DashboardTab.VISITORS
                        ? AdviceBuilder.build(zoo, tab == DashboardTab.OVERVIEW ? OVERVIEW_ADVICE : ALL_ADVICE) : List.of(),
                zoo.reports().recommendations().recommended(), zoo.reports().recommendations().size(),
                tab == DashboardTab.VISITORS ? visitorPanel(zoo) : DashboardSnapshot.VisitorPanel.EMPTY,
                tab == DashboardTab.FINANCES ? financePanel(zoo) : DashboardSnapshot.FinancePanel.EMPTY);
    }

    /** Shop income so far today, or 0 if the in-game day has moved on since the last thing was recorded. */
    private static long todaysShopSales(Zoo zoo, long gameTime) {
        DayRecord today = zoo.reports().history().today();
        return today.day == gameTime / 24000L ? today.shopSales : 0;
    }

    private static DashboardSnapshot.VisitorPanel visitorPanel(Zoo zoo) {
        DayRecord today = zoo.reports().history().today();
        Map<String, Integer> tally = zoo.stats().commentTally();
        double minutes = today.departures == 0 ? 0 : today.visitTicks / (double) today.departures / 20.0 / 60.0;
        double spend = today.arrivals == 0 ? 0 : today.spending / (double) today.arrivals;
        return new DashboardSnapshot.VisitorPanel((int) today.arrivals, (int) today.refusals, (int) today.departures, minutes, spend,
                (int) today.budgetVisitors, (int) today.enthusiastVisitors, (int) today.generalVisitors, (int) today.meals,
                tally.getOrDefault(CommentType.HUNGRY_NO_FOOD.key(), 0), (int) today.toiletVisits, tally.getOrDefault(CommentType.NO_TOILET.key(), 0));
    }

    private static DashboardSnapshot.FinancePanel financePanel(Zoo zoo) {
        DayRecord today = zoo.reports().history().today();
        List<DashboardSnapshot.DayBar> days = zoo.reports().history().lastDays(CHART_DAYS).stream()
                .map(d -> new DashboardSnapshot.DayBar(d.day, d.admission, d.shopSales)).toList();
        List<DashboardSnapshot.ShopRevenue> shops = zoo.shops().stream()
                .map(s -> new DashboardSnapshot.ShopRevenue(s.name(), s.stats().revenueToday(), s.stats().revenueWeek(), s.stats().revenueTotal()))
                .toList();
        List<Component> recent = zoo.ledger().recent().stream().limit(RECENT_TRANSACTIONS)
                .map(t -> (Component) Component.translatable("zoocraft.transaction." + t.type().name().toLowerCase(java.util.Locale.ROOT),
                        t.type().sign() > 0 ? "+" + t.amount() : "-" + t.amount(), t.actor()))
                .toList();
        return new DashboardSnapshot.FinancePanel(today.admission, today.shopSales, today.deposits, today.withdrawals, days, shops, recent);
    }

    /**
     * One shop with its products: everything in stock, plus items that have a price or sold or were missed today,
     * shown with a stock of 0 so shortages stand out.
     */
    private static DashboardSnapshot.ShopLine shopLine(ServerLevel level, Zoo zoo, Shop shop) {
        ShopStock.Reading reading = ShopStock.read(level, shop);
        Map<String, DashboardSnapshot.ProductLine> products = new LinkedHashMap<>();
        boolean anySellable = false;
        for (ShopStock.StockLine line : reading.lines()) {
            anySellable |= line.category() != ProductCategory.UNSELLABLE && line.count() > 0;
            products.put(line.itemId(), product(shop, line.icon(), line.itemId(), line.category(), line.count()));
        }
        List<String> others = new ArrayList<>(shop.prices().keySet());
        others.addAll(shop.stats().soldToday().keySet());
        for (String key : shop.stats().missedToday().keySet()) {
            others.add(key.substring(key.indexOf('|') + 1));
        }
        for (String itemId : others) {
            if (!products.containsKey(itemId)) {
                ProductCatalog.item(itemId).ifPresent(item -> {
                    ItemStack icon = new ItemStack(item);
                    products.put(itemId, product(shop, icon, itemId, ProductCatalog.category(icon), 0));
                });
            }
        }
        int status = zoo.isDetached(shop.counter()) ? DashboardSnapshot.SHOP_DETACHED
                : !shop.open() ? DashboardSnapshot.SHOP_CLOSED
                : !anySellable ? DashboardSnapshot.SHOP_SOLD_OUT
                : DashboardSnapshot.SHOP_OPEN;
        return new DashboardSnapshot.ShopLine(shop.id(), shop.name(), status, shop.stats().revenueToday(), shop.containers().size(),
                reading.unknown(), products.values().stream().limit(MAX_PRODUCTS).toList());
    }

    private static DashboardSnapshot.ProductLine product(Shop shop, ItemStack icon, String itemId, ProductCategory category, int count) {
        return new DashboardSnapshot.ProductLine(icon, itemId, category.ordinal(), count, ProductCatalog.priceFor(shop, itemId),
                shop.stats().soldToday().getOrDefault(itemId, 0), shop.stats().missedToday(itemId));
    }

    static Component speciesLabel(Exhibit exhibit, ZooCensus census) {
        MutableComponent label = Component.empty();
        boolean first = true;
        for (String species : exhibit.species()) {
            if (!first) {
                label.append(", ");
            }
            label.append(census.speciesNames().getOrDefault(species, Component.literal(species)));
            first = false;
        }
        return label;
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SUBSCRIPTIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SUBSCRIPTIONS.clear();
    }
}
