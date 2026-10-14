package com.zoocraft.zoo;

import com.mojang.authlib.GameProfile;
import com.zoocraft.config.ZooConfig;
import com.zoocraft.core.geometry.BoundaryProblem;
import com.zoocraft.core.geometry.BoundaryValidator;
import com.zoocraft.core.geometry.NamedShape;
import com.zoocraft.core.geometry.Vertex;
import com.zoocraft.core.ledger.LedgerResult;
import com.zoocraft.core.rating.Ratings;
import com.zoocraft.core.ledger.Transaction;
import com.zoocraft.core.ledger.TransactionType;
import com.zoocraft.core.shop.PriceRules;
import com.zoocraft.core.shop.ProductCategory;
import com.zoocraft.core.shop.Sale;
import com.zoocraft.core.shop.SaleRequest;
import com.zoocraft.core.shop.SaleResult;
import com.zoocraft.shop.ContainerPositions;
import com.zoocraft.shop.ProductCatalog;
import com.zoocraft.shop.ShopStock;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The only way to change zoos. Every method runs on the server thread, checks permissions,
 * marks the saved data dirty and notifies listeners.
 */
public final class ZooManager {
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_PRICE = 999;
    /** How long a gate stays marked as blocked after visitors fail to use it. */
    public static final long GATE_BLOCKED_TICKS = 20 * 120;

    private static final List<Consumer<Zoo>> CHANGE_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Zoo>> BOUNDARY_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Zoo>> REMOVE_LISTENERS = new CopyOnWriteArrayList<>();

    private final MinecraftServer server;
    private final ZooSavedData data;

    private ZooManager(MinecraftServer server) {
        this.server = server;
        this.data = ZooSavedData.get(server);
    }

    public static ZooManager get(MinecraftServer server) {
        return new ZooManager(server);
    }

    /** Called after any change to a zoo (settings, finances, managers, gates, boundary). */
    public static void addChangeListener(Consumer<Zoo> listener) {
        CHANGE_LISTENERS.add(listener);
    }

    /** Called after a zoo's boundary is edited, before the general change listeners. */
    public static void addBoundaryListener(Consumer<Zoo> listener) {
        BOUNDARY_LISTENERS.add(listener);
    }

    /** Called after a zoo is deleted. */
    public static void addRemoveListener(Consumer<Zoo> listener) {
        REMOVE_LISTENERS.add(listener);
    }

    // ---------------------------------------------------------------- lookups

    public Collection<Zoo> all() {
        return List.copyOf(data.zoos());
    }

    public Optional<Zoo> byId(UUID id) {
        return Optional.ofNullable(data.get(id));
    }

    public Optional<Zoo> byName(String name) {
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        return data.zoos().stream().filter(z -> z.name().toLowerCase(Locale.ROOT).equals(wanted)).findFirst();
    }

    public Optional<Zoo> zooAt(Level level, BlockPos pos) {
        return data.index().zooAt(level.dimension(), pos);
    }

    public List<Zoo> zoosNear(Level level, BlockPos pos, int chunkRadius) {
        return data.index().zoosNear(level.dimension(), pos, chunkRadius);
    }

    public List<Zoo> managedBy(ServerPlayer player) {
        return data.zoos().stream().filter(z -> can(player, z, ZooPermission.VIEW)).toList();
    }

    public static boolean can(ServerPlayer player, Zoo zoo, ZooPermission permission) {
        if (player.hasPermissions(2) || zoo.owner().equals(player.getUUID())) {
            return true;
        }
        return !permission.ownerOnly() && zoo.managers().contains(player.getUUID());
    }

    // ---------------------------------------------------------------- creation and boundary

    public ActionResult createZoo(ServerPlayer player, String rawName, ResourceKey<Level> dimension, List<BoundaryVertex> boundary) {
        String name = rawName.trim();
        Optional<Component> nameProblem = checkName(name, null);
        if (nameProblem.isPresent()) {
            return ActionResult.fail(nameProblem.get());
        }
        Optional<BoundaryProblem> problem = validateBoundary(dimension, boundary, null);
        if (problem.isPresent()) {
            return ActionResult.fail(describe(problem.get()));
        }
        Zoo zoo = Zoo.create(name, dimension, player.getUUID(), normaliseBoundary(boundary));
        data.put(zoo);
        fireChanged(zoo);
        return ActionResult.ok(Component.translatable("zoocraft.msg.zoo_created", name));
    }

    public ActionResult editBoundary(ServerPlayer player, Zoo zoo, List<BoundaryVertex> boundary) {
        if (!can(player, zoo, ZooPermission.EDIT)) {
            return noPermission();
        }
        Optional<BoundaryProblem> problem = validateBoundary(zoo.dimension(), boundary, zoo);
        if (problem.isPresent()) {
            return ActionResult.fail(describe(problem.get()));
        }
        long gameTime = server.overworld().getGameTime();
        List<Gate> wasAttached = zoo.gates().stream().filter(g -> zoo.gateStatus(g, gameTime) != GateStatus.DETACHED).toList();
        List<Shop> shopsInside = zoo.shops().stream().filter(s -> !zoo.isDetached(s.counter())).toList();
        List<Toilet> toiletsInside = zoo.toilets().stream().filter(t -> !zoo.isDetached(t.pos())).toList();
        zoo.setBoundary(normaliseBoundary(boundary));
        data.reindex(zoo);
        List<Component> notes = new ArrayList<>();
        for (Gate gate : wasAttached) {
            if (zoo.gateStatus(gate, gameTime) == GateStatus.DETACHED) {
                notes.add(Component.translatable("zoocraft.msg.gate_detached", gate.pos().getX(), gate.pos().getY(), gate.pos().getZ()));
            }
        }
        for (Shop shop : shopsInside) {
            if (zoo.isDetached(shop.counter())) {
                notes.add(Component.translatable("zoocraft.msg.shop_detached", shop.name()));
            }
        }
        for (Toilet toilet : toiletsInside) {
            if (zoo.isDetached(toilet.pos())) {
                notes.add(Component.translatable("zoocraft.msg.toilet_detached", toilet.pos().getX(), toilet.pos().getY(), toilet.pos().getZ()));
            }
        }
        BOUNDARY_LISTENERS.forEach(l -> l.accept(zoo));
        fireChanged(zoo);
        return ActionResult.ok(Component.translatable("zoocraft.msg.boundary_saved", zoo.name()), notes);
    }

    public Optional<BoundaryProblem> validateBoundary(ResourceKey<Level> dimension, List<BoundaryVertex> boundary, Zoo ignoring) {
        List<Vertex> vertices = boundary.stream().map(BoundaryVertex::toVertex).toList();
        List<NamedShape> others = data.zoos().stream()
                .filter(z -> z != ignoring && z.dimension().equals(dimension))
                .map(z -> new NamedShape(z.name(), z.shape()))
                .toList();
        return BoundaryValidator.validate(vertices, ZooConfig.boundaryLimits(), others);
    }

    private static List<BoundaryVertex> normaliseBoundary(List<BoundaryVertex> boundary) {
        List<BoundaryVertex> result = new ArrayList<>();
        for (BoundaryVertex v : boundary) {
            if (result.isEmpty() || !result.getLast().toVertex().equals(v.toVertex())) {
                result.add(v);
            }
        }
        while (result.size() > 1 && result.getFirst().toVertex().equals(result.getLast().toVertex())) {
            result.removeLast();
        }
        return result;
    }

    public static Component describe(BoundaryProblem problem) {
        return switch (problem.kind()) {
            case TOO_FEW_CORNERS -> Component.translatable("zoocraft.boundary.too_few_corners");
            case TOO_MANY_CORNERS -> Component.translatable("zoocraft.boundary.too_many_corners", problem.limit());
            case SELF_INTERSECTING -> problem.at() == null
                    ? Component.translatable("zoocraft.boundary.self_intersecting")
                    : Component.translatable("zoocraft.boundary.self_intersecting_at", problem.at().x(), problem.at().z());
            case TOO_SMALL -> Component.translatable("zoocraft.boundary.too_small", problem.limit());
            case TOO_LARGE -> Component.translatable("zoocraft.boundary.too_large", problem.limit());
            case OVERLAPS -> Component.translatable("zoocraft.boundary.overlaps", problem.otherZoo());
        };
    }

    // ---------------------------------------------------------------- gates

    /** @param feet where visitors will stand when passing through the gate */
    public ActionResult addGate(ServerPlayer player, ServerLevel level, BlockPos feet) {
        Optional<Zoo> target = nearestEdgeZoo(level, feet);
        if (target.isEmpty()) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.gate_not_near_edge", (int) Zoo.GATE_EDGE_DISTANCE));
        }
        Zoo zoo = target.get();
        if (!can(player, zoo, ZooPermission.EDIT)) {
            return noPermission();
        }
        if (gateNear(zoo, feet).isPresent()) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.gate_exists"));
        }
        double[] normal = zoo.shape().outwardNormalAt(feet.getX() + 0.5, feet.getZ() + 0.5);
        int ox = (int) Math.round(normal[0] * 2);
        int oz = (int) Math.round(normal[1] * 2);
        BlockPos outsideGuess = feet.offset(ox, 0, oz);
        BlockPos insideGuess = feet.offset(-ox, 0, -oz);
        Optional<BlockPos> outside = Standing.findStandable(level, outsideGuess, 2);
        Optional<BlockPos> inside = Standing.findStandable(level, insideGuess, 2);
        Gate gate = new Gate(UUID.randomUUID(), feet, outside.orElse(outsideGuess), inside.orElse(insideGuess));
        zoo.addGate(gate);
        data.setDirty();
        fireChanged(zoo);
        List<Component> notes = new ArrayList<>();
        if (!Standing.isStandable(level, feet)) {
            notes.add(Component.translatable("zoocraft.msg.gate_warning_gate"));
        }
        if (outside.isEmpty()) {
            notes.add(Component.translatable("zoocraft.msg.gate_warning_outside"));
        }
        if (inside.isEmpty()) {
            notes.add(Component.translatable("zoocraft.msg.gate_warning_inside"));
        }
        return ActionResult.ok(Component.translatable("zoocraft.msg.gate_added", zoo.name(), zoo.gates().size()), notes);
    }

    public ActionResult removeGate(ServerPlayer player, ServerLevel level, BlockPos pos) {
        for (Zoo zoo : zoosNear(level, pos, 1)) {
            Optional<Gate> gate = gateNear(zoo, pos);
            if (gate.isPresent()) {
                if (!can(player, zoo, ZooPermission.EDIT)) {
                    return noPermission();
                }
                zoo.removeGate(gate.get().id());
                data.setDirty();
                fireChanged(zoo);
                return ActionResult.ok(Component.translatable("zoocraft.msg.gate_removed", zoo.name()));
            }
        }
        return ActionResult.fail(Component.translatable("zoocraft.msg.no_gate_here"));
    }

    /** A gate at or right next to the position, if any. */
    public static Optional<Gate> gateNear(Zoo zoo, BlockPos pos) {
        return zoo.gates().stream()
                .filter(g -> Math.abs(g.pos().getX() - pos.getX()) <= 1 && Math.abs(g.pos().getZ() - pos.getZ()) <= 1
                        && Math.abs(g.pos().getY() - pos.getY()) <= 2)
                .findFirst();
    }

    private Optional<Zoo> nearestEdgeZoo(ServerLevel level, BlockPos pos) {
        Zoo best = null;
        double bestDistance = Zoo.GATE_EDGE_DISTANCE;
        for (Zoo zoo : zoosNear(level, pos, 1)) {
            double d = zoo.shape().distanceToEdge(pos.getX() + 0.5, pos.getZ() + 0.5);
            if (d <= bestDistance) {
                best = zoo;
                bestDistance = d;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Visitor code reports gates it could not get through. */
    public void markGateBlocked(Zoo zoo, Gate gate) {
        zoo.markGateBlocked(gate.id(), server.overworld().getGameTime() + GATE_BLOCKED_TICKS);
        fireChanged(zoo);
    }

    // ---------------------------------------------------------------- shops and toilets

    /** @param counter where customers stand */
    public ActionResult createShop(ServerPlayer player, ServerLevel level, BlockPos counter) {
        Optional<Zoo> target = zooAt(level, counter);
        if (target.isEmpty()) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.not_in_zoo"));
        }
        Zoo zoo = target.get();
        if (!can(player, zoo, ZooPermission.EDIT)) {
            return noPermission();
        }
        if (zoo.shops().size() >= ZooConfig.MAX_SHOPS_PER_ZOO.get()) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.shop_limit", ZooConfig.MAX_SHOPS_PER_ZOO.get()));
        }
        if (designationNear(zoo, counter)) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.designation_exists"));
        }
        int number = 1;
        while (shopNamed(zoo, "Shop " + number).isPresent()) {
            number++;
        }
        Shop shop = Shop.create("Shop " + number, counter);
        zoo.addShop(shop);
        data.setDirty();
        fireChanged(zoo);
        List<Component> notes = new ArrayList<>();
        if (!Standing.isStandable(level, counter)) {
            notes.add(Component.translatable("zoocraft.msg.counter_warning"));
        }
        return ActionResult.ok(Component.translatable("zoocraft.msg.shop_created", shop.name(), zoo.name()), notes);
    }

    public ActionResult removeShop(ServerPlayer player, ServerLevel level, BlockPos near) {
        for (Zoo zoo : zoosNear(level, near, 1)) {
            Optional<Shop> shop = shopAt(zoo, near);
            if (shop.isPresent()) {
                if (!can(player, zoo, ZooPermission.EDIT)) {
                    return noPermission();
                }
                zoo.removeShop(shop.get().id());
                data.setDirty();
                fireChanged(zoo);
                return ActionResult.ok(Component.translatable("zoocraft.msg.shop_removed", shop.get().name()));
            }
        }
        return ActionResult.fail(Component.translatable("zoocraft.msg.no_shop_here"));
    }

    public ActionResult linkContainer(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!ContainerPositions.hasItemHandler(level, pos)) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.not_a_container"));
        }
        Optional<ShopRef> existing = shopOwningContainer(level, pos);
        if (existing.isPresent()) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.container_already_linked", existing.get().shop().name()));
        }
        Optional<Zoo> target = zooAt(level, pos);
        if (target.isEmpty()) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.not_in_zoo"));
        }
        Zoo zoo = target.get();
        if (!can(player, zoo, ZooPermission.EDIT)) {
            return noPermission();
        }
        int radius = ZooConfig.SHOP_LINK_RADIUS.get();
        Optional<Shop> nearest = zoo.shops().stream()
                .filter(s -> s.counter().distSqr(pos) <= (double) radius * radius)
                .min(java.util.Comparator.comparingDouble(s -> s.counter().distSqr(pos)));
        if (nearest.isEmpty()) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.no_counter_near", radius));
        }
        Shop shop = nearest.get();
        if (shop.containers().size() >= ZooConfig.MAX_CONTAINERS_PER_SHOP.get()) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.container_limit", shop.name(), ZooConfig.MAX_CONTAINERS_PER_SHOP.get()));
        }
        shop.addContainer(pos);
        data.setDirty();
        fireChanged(zoo);
        return ActionResult.ok(Component.translatable("zoocraft.msg.container_linked", shop.name(), shop.containers().size()));
    }

    public ActionResult unlinkContainer(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Optional<ShopRef> ref = shopOwningContainer(level, pos);
        if (ref.isEmpty()) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.not_linked"));
        }
        Zoo zoo = ref.get().zoo();
        Shop shop = ref.get().shop();
        if (!can(player, zoo, ZooPermission.EDIT)) {
            return noPermission();
        }
        for (BlockPos part : ContainerPositions.parts(level, pos)) {
            shop.removeContainer(part);
        }
        data.setDirty();
        fireChanged(zoo);
        return ActionResult.ok(Component.translatable("zoocraft.msg.container_unlinked", shop.name()));
    }

    /** Used by the facility census when a linked container has gone. */
    public void unlinkMissing(Zoo zoo, Shop shop, BlockPos pos) {
        if (shop.removeContainer(pos)) {
            data.setDirty();
            fireChanged(zoo);
        }
    }

    public ActionResult renameShop(ServerPlayer player, Zoo zoo, UUID shopId, String rawName) {
        if (!can(player, zoo, ZooPermission.SETTINGS)) {
            return noPermission();
        }
        Optional<Shop> shop = zoo.shop(shopId);
        if (shop.isEmpty()) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.no_such_shop"));
        }
        String name = rawName.trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.bad_name", MAX_NAME_LENGTH));
        }
        Optional<Shop> clash = shopNamed(zoo, name);
        if (clash.isPresent() && clash.get() != shop.get()) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.shop_name_taken", name));
        }
        shop.get().setName(name);
        data.setDirty();
        fireChanged(zoo);
        return ActionResult.ok(Component.translatable("zoocraft.msg.shop_renamed", name));
    }

    public ActionResult setShopOpen(ServerPlayer player, Zoo zoo, UUID shopId, boolean open) {
        if (!can(player, zoo, ZooPermission.SETTINGS)) {
            return noPermission();
        }
        Optional<Shop> shop = zoo.shop(shopId);
        if (shop.isEmpty()) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.no_such_shop"));
        }
        shop.get().setOpen(open);
        data.setDirty();
        fireChanged(zoo);
        return ActionResult.ok(Component.translatable(open ? "zoocraft.msg.shop_opened" : "zoocraft.msg.shop_closed", shop.get().name()));
    }

    public ActionResult setItemPrice(ServerPlayer player, Zoo zoo, UUID shopId, String itemId, int price) {
        if (!can(player, zoo, ZooPermission.SETTINGS)) {
            return noPermission();
        }
        Optional<Shop> shop = zoo.shop(shopId);
        if (shop.isEmpty()) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.no_such_shop"));
        }
        if (!PriceRules.valid(price)) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.bad_shop_price", PriceRules.MIN_PRICE, PriceRules.MAX_PRICE));
        }
        Optional<Item> item = ProductCatalog.item(itemId);
        if (item.isEmpty() || ProductCatalog.category(new ItemStack(item.get())) == ProductCategory.UNSELLABLE) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.not_for_sale"));
        }
        shop.get().setPrice(itemId, price);
        data.setDirty();
        fireChanged(zoo);
        return ActionResult.ok(Component.translatable("zoocraft.msg.item_price_set", item.get().getDescription(), price, shop.get().name()));
    }

    public ActionResult createToilet(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Optional<Zoo> target = zooAt(level, pos);
        if (target.isEmpty()) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.not_in_zoo"));
        }
        Zoo zoo = target.get();
        if (!can(player, zoo, ZooPermission.EDIT)) {
            return noPermission();
        }
        if (designationNear(zoo, pos)) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.designation_exists"));
        }
        zoo.addToilet(new Toilet(UUID.randomUUID(), pos.immutable()));
        data.setDirty();
        fireChanged(zoo);
        List<Component> notes = new ArrayList<>();
        if (!Standing.isStandable(level, pos)) {
            notes.add(Component.translatable("zoocraft.msg.toilet_warning"));
        }
        return ActionResult.ok(Component.translatable("zoocraft.msg.toilet_created", zoo.name(), zoo.toilets().size()), notes);
    }

    public ActionResult removeToilet(ServerPlayer player, ServerLevel level, BlockPos near) {
        for (Zoo zoo : zoosNear(level, near, 1)) {
            Optional<Toilet> toilet = zoo.toilets().stream().filter(t -> isNear(t.pos(), near)).findFirst();
            if (toilet.isPresent()) {
                if (!can(player, zoo, ZooPermission.EDIT)) {
                    return noPermission();
                }
                zoo.removeToilet(toilet.get().id());
                data.setDirty();
                fireChanged(zoo);
                return ActionResult.ok(Component.translatable("zoocraft.msg.toilet_removed", zoo.name()));
            }
        }
        return ActionResult.fail(Component.translatable("zoocraft.msg.no_toilet_here"));
    }

    /**
     * Sells one unit of an item (spec section 7.5). The caller deducts the buyer's budget only on
     * {@link SaleResult#SOLD}. Failures other than a repeated key are recorded as missed sales.
     */
    public SaleResult sell(ServerLevel level, Zoo zoo, UUID shopId, String key, String itemId, int budget, String actor) {
        Optional<Shop> maybeShop = zoo.shop(shopId);
        if (maybeShop.isEmpty()) {
            return SaleResult.SHOP_CLOSED;
        }
        Shop shop = maybeShop.get();
        long gameTime = server.overworld().getGameTime();
        shop.stats().rollDay(gameTime / 24000L);
        int price = ProductCatalog.priceFor(shop, itemId);
        boolean usable = shop.open() && !zoo.isDetached(shop.counter());
        SaleResult result = Sale.attempt(zoo.ledger(), new ShopStock(level, shop),
                new SaleRequest(key, itemId, price, budget, usable, gameTime, actor));
        if (result.sold()) {
            shop.stats().recordSale(itemId, price);
            if (shop.price(itemId).isEmpty()) {
                shop.setPrice(itemId, price);
            }
            data.setDirty();
            fireChanged(zoo);
        } else if (result != SaleResult.DUPLICATE) {
            recordMissedSale(zoo, shopId, result.name().toLowerCase(Locale.ROOT), itemId);
        }
        return result;
    }

    /** A purchase that didn't happen, for shortage and pricing advice. */
    public void recordMissedSale(Zoo zoo, UUID shopId, String reason, String itemId) {
        zoo.shop(shopId).ifPresent(shop -> {
            shop.stats().rollDay(server.overworld().getGameTime() / 24000L);
            shop.stats().recordMissed(reason, itemId);
            data.setDirty();
        });
    }

    /** The shop whose stock is in the container at {@code pos}, checking both halves of a double chest. */
    public Optional<ShopRef> shopOwningContainer(Level level, BlockPos pos) {
        List<BlockPos> parts = ContainerPositions.parts(level, pos);
        for (Zoo zoo : zoosNear(level, pos, 1)) {
            if (!zoo.dimension().equals(level.dimension())) {
                continue;
            }
            for (Shop shop : zoo.shops()) {
                for (BlockPos linked : shop.containers()) {
                    if (parts.contains(linked)) {
                        return Optional.of(new ShopRef(zoo, shop));
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** The shop whose counter is at or right next to the position. */
    public static Optional<Shop> shopAt(Zoo zoo, BlockPos pos) {
        return zoo.shops().stream().filter(s -> isNear(s.counter(), pos)).findFirst();
    }

    private static Optional<Shop> shopNamed(Zoo zoo, String name) {
        return zoo.shops().stream().filter(s -> s.name().equalsIgnoreCase(name)).findFirst();
    }

    private static boolean designationNear(Zoo zoo, BlockPos pos) {
        return gateNear(zoo, pos).isPresent() || shopAt(zoo, pos).isPresent()
                || zoo.toilets().stream().anyMatch(t -> isNear(t.pos(), pos));
    }

    private static boolean isNear(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) <= 1 && Math.abs(a.getZ() - b.getZ()) <= 1 && Math.abs(a.getY() - b.getY()) <= 2;
    }

    // ---------------------------------------------------------------- settings

    public ActionResult setOpen(ServerPlayer player, Zoo zoo, boolean open) {
        if (!can(player, zoo, ZooPermission.SETTINGS)) {
            return noPermission();
        }
        if (open && zoo.gates().isEmpty()) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.needs_gate"));
        }
        zoo.setOpen(open);
        data.setDirty();
        fireChanged(zoo);
        return ActionResult.ok(Component.translatable(open ? "zoocraft.msg.opened" : "zoocraft.msg.closed", zoo.name()));
    }

    public ActionResult setPrice(ServerPlayer player, Zoo zoo, int price) {
        if (!can(player, zoo, ZooPermission.SETTINGS)) {
            return noPermission();
        }
        if (price < 0 || price > MAX_PRICE) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.bad_price", MAX_PRICE));
        }
        zoo.setAdmissionPrice(price);
        data.setDirty();
        fireChanged(zoo);
        return ActionResult.ok(Component.translatable("zoocraft.msg.price_set", zoo.name(), price));
    }

    public ActionResult setKeepLoaded(ServerPlayer player, Zoo zoo, boolean keepLoaded) {
        if (!can(player, zoo, ZooPermission.SETTINGS)) {
            return noPermission();
        }
        if (keepLoaded && !ZooConfig.ALLOW_ZOO_CHUNK_LOADING.get()) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.chunk_loading_disabled"));
        }
        zoo.setKeepLoaded(keepLoaded);
        data.setDirty();
        fireChanged(zoo);
        return ActionResult.ok(Component.translatable(keepLoaded ? "zoocraft.msg.keep_loaded_on" : "zoocraft.msg.keep_loaded_off", zoo.name()));
    }

    public ActionResult rename(ServerPlayer player, Zoo zoo, String rawName) {
        if (!can(player, zoo, ZooPermission.SETTINGS)) {
            return noPermission();
        }
        String name = rawName.trim();
        Optional<Component> problem = checkName(name, zoo);
        if (problem.isPresent()) {
            return ActionResult.fail(problem.get());
        }
        zoo.setName(name);
        data.setDirty();
        fireChanged(zoo);
        return ActionResult.ok(Component.translatable("zoocraft.msg.renamed", name));
    }

    private Optional<Component> checkName(String name, Zoo ignoring) {
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            return Optional.of(Component.translatable("zoocraft.msg.bad_name", MAX_NAME_LENGTH));
        }
        Optional<Zoo> existing = byName(name);
        if (existing.isPresent() && existing.get() != ignoring) {
            return Optional.of(Component.translatable("zoocraft.msg.name_taken", name));
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- people

    public ActionResult addManager(ServerPlayer player, Zoo zoo, GameProfile manager) {
        if (!can(player, zoo, ZooPermission.MANAGE_MANAGERS)) {
            return noPermission();
        }
        if (zoo.owner().equals(manager.getId())) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.already_owner", manager.getName()));
        }
        if (zoo.managers().contains(manager.getId())) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.already_manager", manager.getName()));
        }
        zoo.addManager(manager.getId());
        data.setDirty();
        fireChanged(zoo);
        return ActionResult.ok(Component.translatable("zoocraft.msg.manager_added", manager.getName(), zoo.name()));
    }

    public ActionResult removeManager(ServerPlayer player, Zoo zoo, UUID manager, String managerName) {
        if (!can(player, zoo, ZooPermission.MANAGE_MANAGERS)) {
            return noPermission();
        }
        if (!zoo.removeManager(manager)) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.not_manager", managerName));
        }
        data.setDirty();
        fireChanged(zoo);
        return ActionResult.ok(Component.translatable("zoocraft.msg.manager_removed", managerName, zoo.name()));
    }

    public ActionResult transfer(ServerPlayer player, Zoo zoo, GameProfile newOwner) {
        if (!can(player, zoo, ZooPermission.TRANSFER)) {
            return noPermission();
        }
        if (zoo.owner().equals(newOwner.getId())) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.already_owner", newOwner.getName()));
        }
        zoo.setOwner(newOwner.getId());
        data.setDirty();
        fireChanged(zoo);
        return ActionResult.ok(Component.translatable("zoocraft.msg.transferred", zoo.name(), newOwner.getName()));
    }

    public ActionResult delete(ServerPlayer player, Zoo zoo) {
        if (!can(player, zoo, ZooPermission.DELETE)) {
            return noPermission();
        }
        if (zoo.ledger().balance() > 0) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.delete_has_funds", zoo.ledger().balance()));
        }
        data.remove(zoo);
        REMOVE_LISTENERS.forEach(l -> l.accept(zoo));
        return ActionResult.ok(Component.translatable("zoocraft.msg.deleted", zoo.name()));
    }

    // ---------------------------------------------------------------- money

    public ActionResult deposit(ServerPlayer player, Zoo zoo, int amount) {
        if (!can(player, zoo, ZooPermission.FINANCES)) {
            return noPermission();
        }
        if (amount <= 0) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.bad_amount"));
        }
        if (Emeralds.count(player.getInventory()) < amount) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.not_enough_emeralds", amount));
        }
        Transaction tx = new Transaction("deposit-" + UUID.randomUUID(), TransactionType.DEPOSIT, amount,
                server.overworld().getGameTime(), player.getGameProfile().getName());
        LedgerResult result = zoo.ledger().apply(tx);
        if (!result.applied()) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.transaction_failed"));
        }
        Emeralds.remove(player, amount);
        data.setDirty();
        fireChanged(zoo);
        return ActionResult.ok(Component.translatable("zoocraft.msg.deposited", amount, zoo.name(), zoo.ledger().balance()));
    }

    public ActionResult withdraw(ServerPlayer player, Zoo zoo, int amount) {
        if (!can(player, zoo, ZooPermission.FINANCES)) {
            return noPermission();
        }
        if (amount <= 0) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.bad_amount"));
        }
        Transaction tx = new Transaction("withdraw-" + UUID.randomUUID(), TransactionType.WITHDRAW, amount,
                server.overworld().getGameTime(), player.getGameProfile().getName());
        LedgerResult result = zoo.ledger().apply(tx);
        if (result == LedgerResult.INSUFFICIENT_FUNDS) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.not_enough_funds", zoo.ledger().balance()));
        }
        if (!result.applied()) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.transaction_failed"));
        }
        Emeralds.give(player, amount);
        data.setDirty();
        fireChanged(zoo);
        return ActionResult.ok(Component.translatable("zoocraft.msg.withdrew", amount, zoo.name(), zoo.ledger().balance()));
    }

    /**
     * Charges admission for one visit, exactly once per visit ID. Visitor code calls this and only marks the visitor
     * admitted (and deducts its budget) when it returns {@link LedgerResult#APPLIED}.
     */
    public LedgerResult chargeAdmission(Zoo zoo, UUID visitId, int price) {
        long gameTime = server.overworld().getGameTime();
        zoo.stats().rollDay(gameTime / 24000L);
        if (price > 0) {
            LedgerResult result = zoo.ledger().apply(new Transaction("admission-" + visitId, TransactionType.ADMISSION, price, gameTime, "Visitor"));
            if (!result.applied()) {
                return result;
            }
        }
        // Free entry has no ledger entry; the visitor's own admitted flag stops it being counted twice.
        zoo.stats().totalAttendance++;
        zoo.stats().attendanceToday++;
        data.setDirty();
        fireChanged(zoo);
        return LedgerResult.APPLIED;
    }

    // ---------------------------------------------------------------- visitor feedback

    /** Tallies a visitor comment for today's dashboard summary. */
    public void recordComment(Zoo zoo, String commentKey) {
        zoo.stats().rollDay(server.overworld().getGameTime() / 24000L);
        zoo.stats().commentTally.merge(commentKey, 1, Integer::sum);
        data.setDirty();
    }

    public void recordRefusal(Zoo zoo, int visitors) {
        zoo.stats().refusedAdmissions += visitors;
        data.setDirty();
        fireChanged(zoo);
    }

    /** A visitor has left: satisfaction moves a small step towards their final mood, reputation follows slowly. */
    public void recordDeparture(Zoo zoo, double visitorSatisfaction) {
        double satisfaction = Ratings.blend(zoo.satisfaction(), visitorSatisfaction, Ratings.SATISFACTION_WEIGHT);
        zoo.setSatisfaction((float) satisfaction);
        zoo.setReputation((float) Ratings.blend(zoo.reputation(), satisfaction, Ratings.REPUTATION_WEIGHT));
        data.setDirty();
        fireChanged(zoo);
    }

    // ---------------------------------------------------------------- helpers

    private static ActionResult noPermission() {
        return ActionResult.fail(Component.translatable("zoocraft.msg.no_permission"));
    }

    private void fireChanged(Zoo zoo) {
        CHANGE_LISTENERS.forEach(l -> l.accept(zoo));
    }

    /** For other packages that update visitor-driven fields. */
    public void markDirty(Zoo zoo) {
        data.setDirty();
        fireChanged(zoo);
    }
}
