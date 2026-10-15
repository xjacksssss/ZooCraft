package com.zoocraft.visitor;

import com.zoocraft.config.ZooConfig;
import com.zoocraft.core.needs.Errand;
import com.zoocraft.core.needs.Need;
import com.zoocraft.core.needs.Needs;
import com.zoocraft.core.needs.Offer;
import com.zoocraft.core.needs.PlannerInput;
import com.zoocraft.core.needs.ProductChoice;
import com.zoocraft.core.needs.SouvenirRules;
import com.zoocraft.core.needs.VisitPlanner;
import com.zoocraft.core.shop.PriceRules;
import com.zoocraft.core.shop.ProductCategory;
import com.zoocraft.core.shop.SaleResult;
import com.zoocraft.core.visitor.VisitorProfile;
import com.zoocraft.feedback.CommentType;
import com.zoocraft.shop.FacilityCensus;
import com.zoocraft.shop.FacilityReach;
import com.zoocraft.shop.FacilitySnapshot;
import com.zoocraft.shop.ProductCatalog;
import com.zoocraft.shop.ToiletOccupancy;
import com.zoocraft.litter.Littering;
import com.zoocraft.litter.RubbishBinBlockEntity;
import com.zoocraft.registry.ZooItems;
import com.zoocraft.zoo.Shop;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Runs one visitor's errands to shops (Phase 2 spec section 8): choosing what to do next with {@link VisitPlanner},
 * picking what to buy with {@link ProductChoice}, buying through {@link ZooManager#sell}, and eating or carrying
 * the purchase. Reads only the cached facility snapshot; the real stock is touched only by the sale itself.
 * {@link VisitorController} keeps the phases and the walking.
 */
public final class VisitorErrands {
    static final long COOLDOWN_TICKS = 30 * 20;
    static final long UNREACHABLE_TICKS = 60 * 20;
    static final long EAT_TICKS = 8 * 20;
    static final long CARRY_TICKS = 10 * 20;
    /** Pause at a counter while being served. */
    static final long SERVE_TICKS = 40;
    /** Shops tried for one errand before giving up for a while. */
    static final int MAX_SHOPS_PER_ERRAND = 2;
    /** Satisfaction lost each controller step while a need is urgent (1 every 10 seconds). */
    static final double URGENT_LOSS_PER_STEP = 1.0 / (10 * 20 / VisitorController.STEP);
    static final Set<ProductCategory> REFRESHMENTS = Set.of(ProductCategory.FOOD, ProductCategory.DRINK);
    static final Set<ProductCategory> SOUVENIRS = Set.of(ProductCategory.SOUVENIR);
    static final long TOILET_USE_TICKS = 6 * 20;
    static final long TOILET_WAIT_TICKS = 20 * 20;
    /** While waiting for a free toilet, check again this often. */
    static final long TOILET_RETRY_TICKS = 40;
    /** How long a visitor carries rubbish looking for a bin before dropping it or taking it home. */
    static final long RUBBISH_CARRY_TICKS = 30 * 20;

    /** The next thing to do: walk to {@code target}, or leave when the errand is {@link Errand#LEAVE}. */
    record Choice(Errand errand, Optional<BlockPos> target) {
    }

    private final Visitor visitor;
    private Errand current = Errand.EXHIBIT;
    private @Nullable UUID shopId;
    private @Nullable String itemId;
    private final Set<UUID> triedShops = new HashSet<>();
    private long foodCooldownUntil;
    private long toiletCooldownUntil;
    private boolean wantsToGoHome;

    // The toilet errand: which toilet, and whether the visitor is using it or waiting for a place.
    private @Nullable UUID toiletId;
    private @Nullable BlockPos toiletPos;
    private final Set<UUID> triedToilets = new HashSet<>();
    private long usingUntil;
    private long waitingSince;

    // Rubbish left over from eating, and the bin the visitor is taking it to.
    private long rubbishSince;
    private int rubbishVariant;
    private @Nullable BlockPos binPos;

    // What the visitor is holding: something to eat or drink, or a souvenir to carry.
    private long holdUntil;
    private boolean eating;
    private boolean drink;

    VisitorErrands(Visitor visitor) {
        this.visitor = visitor;
    }

    public Errand current() {
        return current;
    }

    /** On an errand rather than looking at exhibits. */
    boolean active() {
        return current != Errand.EXHIBIT;
    }

    /** The visitor has run out of money for what it needs and wants to go home. */
    boolean wantsToGoHome() {
        return wantsToGoHome;
    }

    // ------------------------------------------------------------------ needs

    /**
     * Raises needs by one controller step and reacts to urgent ones.
     *
     * @return true when a need has gone unmet for so long that the visitor leaves
     */
    boolean tickNeeds() {
        Needs needs = visitor.needs;
        needs.tick(VisitorController.STEP / 20.0);
        if (needs.urgent(Need.HUNGER)) {
            visitor.remarkOnce(CommentType.STARVING);
            visitor.changeSatisfaction(-URGENT_LOSS_PER_STEP);
        }
        if (needs.urgent(Need.TOILET)) {
            visitor.remarkOnce(CommentType.NEED_TOILET);
            visitor.changeSatisfaction(-URGENT_LOSS_PER_STEP);
        }
        if (needs.overdue(Need.HUNGER)) {
            visitor.remarkOnce(CommentType.HUNGRY_NO_FOOD);
            return true;
        }
        if (needs.overdue(Need.TOILET)) {
            visitor.remarkOnce(CommentType.NO_TOILET);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ choosing

    Choice choose(Zoo zoo, ServerLevel level, long now, boolean endingByChoice) {
        if (current == Errand.TOILET && toiletPos != null && (usingUntil != 0 || waitingSince != 0)) {
            // Still at the toilet, using it or waiting for a place: stay put.
            return new Choice(Errand.TOILET, Optional.of(toiletPos));
        }
        FacilitySnapshot snapshot = FacilityCensus.of(zoo, level);
        int threshold = ZooConfig.NEED_THRESHOLD.get();
        // A visitor still eating its last purchase isn't hungry for another.
        boolean hungry = visitor.needs.wants(Need.HUNGER, threshold) && !(eating && holdUntil != 0);
        PlannerInput input = new PlannerInput(false,
                hungry, anyStocked(zoo, snapshot, REFRESHMENTS, now),
                visitor.needs.wants(Need.TOILET, threshold), toiletAvailable(zoo, snapshot, now),
                now < foodCooldownUntil, now < toiletCooldownUntil, endingByChoice,
                visitor.budget > 0 && visitor.souvenirRoll < SouvenirRules.chance(visitor.profile, visitor.seenSpecies.size()),
                anyStocked(zoo, snapshot, SOUVENIRS, now), visitor.lastStopDone, holdingRubbish(), nearestBin(zoo, snapshot, now).isPresent());
        for (Need need : VisitPlanner.unmet(input)) {
            visitor.remarkOnce(need == Need.HUNGER ? CommentType.HUNGRY_NO_FOOD : CommentType.NO_TOILET);
        }
        Errand errand = VisitPlanner.next(input);
        switch (errand) {
            case LEAVE -> {
                current = Errand.EXHIBIT;
                return new Choice(Errand.LEAVE, Optional.empty());
            }
            case FOOD -> {
                Optional<BlockPos> counter = startShopping(zoo, snapshot, REFRESHMENTS, Errand.FOOD, now);
                if (counter.isPresent()) {
                    return new Choice(Errand.FOOD, counter);
                }
            }
            case SOUVENIR -> {
                visitor.lastStopDone = true;
                Optional<BlockPos> counter = startShopping(zoo, snapshot, SOUVENIRS, Errand.SOUVENIR, now);
                if (counter.isPresent()) {
                    return new Choice(Errand.SOUVENIR, counter);
                }
                return new Choice(Errand.LEAVE, Optional.empty());
            }
            case BIN -> {
                Optional<FacilitySnapshot.BinState> bin = nearestBin(zoo, snapshot, now);
                if (bin.isPresent()) {
                    current = Errand.BIN;
                    binPos = bin.get().pos();
                    return new Choice(Errand.BIN, Optional.of(binPos));
                }
            }
            case TOILET -> {
                Optional<FacilitySnapshot.ToiletState> toilet = nearestToilet(zoo, snapshot, now);
                if (toilet.isPresent()) {
                    current = Errand.TOILET;
                    toiletId = toilet.get().toiletId();
                    toiletPos = toilet.get().pos();
                    return new Choice(Errand.TOILET, Optional.of(toiletPos));
                }
                toiletCooldownUntil = now + COOLDOWN_TICKS;
            }
            case EXHIBIT -> {
            }
        }
        current = Errand.EXHIBIT;
        return new Choice(Errand.EXHIBIT, VisitorExploration.chooseTarget(visitor, zoo, level));
    }

    boolean holdingRubbish() {
        return rubbishSince != 0 && ZooItems.isRubbish(visitor.getMainHandItem());
    }

    /** The nearest bin with room, within walking distance for rubbish, that visitors have not failed to reach. */
    private Optional<FacilitySnapshot.BinState> nearestBin(Zoo zoo, FacilitySnapshot snapshot, long now) {
        double radius = ZooConfig.BIN_SEARCH_RADIUS.get();
        return snapshot.bins().stream()
                .filter(b -> b.usable() && !FacilityReach.isUnreachable(zoo, b.id(), now)
                        && b.pos().distToCenterSqr(visitor.position()) <= radius * radius)
                .min(java.util.Comparator.comparingDouble(b -> b.pos().distToCenterSqr(visitor.position())));
    }

    private boolean toiletAvailable(Zoo zoo, FacilitySnapshot snapshot, long now) {
        return snapshot.toilets().stream().anyMatch(t -> t.usable() && !FacilityReach.isUnreachable(zoo, t.toiletId(), now));
    }

    private Optional<FacilitySnapshot.ToiletState> nearestToilet(Zoo zoo, FacilitySnapshot snapshot, long now) {
        return snapshot.toilets().stream()
                .filter(t -> t.usable() && !triedToilets.contains(t.toiletId()) && !FacilityReach.isUnreachable(zoo, t.toiletId(), now))
                .min(java.util.Comparator.comparingDouble(t -> t.pos().distSqr(visitor.blockPosition())));
    }

    /** A usable, reachable shop has something in stock in one of the categories. */
    private static boolean anyStocked(Zoo zoo, FacilitySnapshot snapshot, Set<ProductCategory> categories, long now) {
        for (FacilitySnapshot.ShopState shop : snapshot.shops()) {
            if (!shop.usable() || FacilityReach.isUnreachable(zoo, shop.shopId(), now)) {
                continue;
            }
            for (FacilitySnapshot.Product product : shop.products()) {
                if (categories.contains(product.category()) && product.count() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Picks what to buy and where. Returns the counter to walk to, or empty when nothing suits. */
    private Optional<BlockPos> startShopping(Zoo zoo, FacilitySnapshot snapshot, Set<ProductCategory> wanted, Errand errand, long now) {
        List<Offer> offers = new ArrayList<>();
        for (FacilitySnapshot.ShopState shop : snapshot.shops()) {
            if (!shop.usable() || triedShops.contains(shop.shopId()) || FacilityReach.isUnreachable(zoo, shop.shopId(), now)) {
                continue;
            }
            double distance = Math.sqrt(shop.counter().distSqr(visitor.blockPosition()));
            for (FacilitySnapshot.Product product : shop.products()) {
                if (product.count() > 0) {
                    offers.add(new Offer(shop.shopId(), product.itemId(), product.category(), product.price(),
                            ProductCatalog.defaultPrice(product.category()), distance));
                }
            }
        }
        ProductChoice.Result result = ProductChoice.choose(offers, wanted, visitor.budget, visitor.profile, visitor.personalFactor,
                zoo.reputation(), visitor.getRandom().nextDouble());
        if (result.outcome() == ProductChoice.Outcome.CHOSEN) {
            Offer offer = result.offer().orElseThrow();
            current = errand;
            shopId = offer.shopId();
            itemId = offer.itemId();
            return snapshot.shop(offer.shopId()).map(FacilitySnapshot.ShopState::counter);
        }
        boolean food = errand == Errand.FOOD;
        switch (result.outcome()) {
            case TOO_EXPENSIVE -> {
                complainTooExpensive();
                cheapest(offers, wanted).ifPresent(o -> ZooManager.get(visitor.getServer()).recordMissedSale(zoo, o.shopId(), "too_expensive", o.itemId()));
            }
            case CANT_AFFORD -> {
                if (food) {
                    visitor.remarkOnce(CommentType.CANT_AFFORD);
                    wantsToGoHome = true;
                }
            }
            default -> {
            }
        }
        if (food) {
            giveUpOnFood(now);
        }
        return Optional.empty();
    }

    private static Optional<Offer> cheapest(List<Offer> offers, Set<ProductCategory> wanted) {
        return offers.stream().filter(o -> wanted.contains(o.category())).min(java.util.Comparator.comparingInt(Offer::price));
    }

    private void complainTooExpensive() {
        visitor.remark(CommentType.SHOP_TOO_EXPENSIVE);
        if (visitor.profile == VisitorProfile.BUDGET_CONSCIOUS) {
            visitor.changeSatisfaction(-2);
        }
    }

    private void giveUpOnFood(long now) {
        foodCooldownUntil = now + COOLDOWN_TICKS;
        triedShops.clear();
        current = Errand.EXHIBIT;
    }

    // ------------------------------------------------------------------ arriving

    /**
     * The visitor reached its errand's spot.
     *
     * @return how long to stay there, in ticks
     */
    long onArrived(Zoo zoo, ServerLevel level, long now) {
        if (current == Errand.FOOD || current == Errand.SOUVENIR) {
            buy(zoo, level, now);
            return SERVE_TICKS;
        }
        if (current == Errand.TOILET && toiletId != null) {
            return atToilet(zoo, now);
        }
        if (current == Errand.BIN && binPos != null) {
            atBin(zoo, level);
            return 10;
        }
        current = Errand.EXHIBIT;
        return 0;
    }

    /** Puts the rubbish in the bin, checking the real bin now. If it has filled up or gone, the rubbish ends up beside it. */
    private void atBin(Zoo zoo, ServerLevel level) {
        current = Errand.EXHIBIT;
        if (level.getBlockEntity(binPos) instanceof RubbishBinBlockEntity bin && bin.deposit()) {
            FacilityReach.clear(zoo, FacilitySnapshot.binId(binPos));
            level.playSound(null, binPos, SoundEvents.COMPOSTER_FILL, SoundSource.BLOCKS, 0.6f, 1.0f);
        } else {
            Littering.drop(level, zoo, visitor.position(), rubbishVariant);
        }
        dropRubbishFromHand();
    }

    private void dropRubbishFromHand() {
        visitor.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        rubbishSince = 0;
        binPos = null;
    }

    /** Called after eating: the visitor is left holding a wrapper, cup or scrap of paper. */
    void startCarryingRubbish(long now) {
        rubbishVariant = visitor.getRandom().nextInt(ZooItems.RUBBISH_VARIANTS);
        visitor.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ZooItems.rubbish(rubbishVariant)));
        rubbishSince = now;
    }

    /**
     * Every step while exploring: rubbish carried for too long without finding a bin is dropped (spec 9.2), unless
     * this visitor is the sort to take it home.
     */
    void tickRubbish(Zoo zoo, ServerLevel level, long now) {
        if (!holdingRubbish() || current == Errand.BIN || now - rubbishSince < RUBBISH_CARRY_TICKS) {
            return;
        }
        if (visitor.litterRoll < ZooConfig.LITTER_CHANCE_WITHOUT_BIN.get()) {
            Littering.drop(level, zoo, visitor.position(), rubbishVariant);
        }
        dropRubbishFromHand();
    }

    /** Uses the toilet if there is room, otherwise waits a while, then tries another or complains (spec 8.4). */
    private long atToilet(Zoo zoo, long now) {
        UUID toilet = toiletId;
        if (usingUntil != 0) {
            if (now < usingUntil) {
                ToiletOccupancy.tryOccupy(toilet, visitor.getUUID(), now, Integer.MAX_VALUE);
                return usingUntil - now;
            }
            visitor.needs.useToilet();
            ZooManager.get(visitor.getServer()).recordToiletVisit(zoo);
            ToiletOccupancy.release(toilet, visitor.getUUID());
            FacilityReach.clear(zoo, toilet);
            finishToiletErrand();
            return 20;
        }
        if (ToiletOccupancy.tryOccupy(toilet, visitor.getUUID(), now, ZooConfig.TOILET_CAPACITY.get())) {
            usingUntil = now + TOILET_USE_TICKS;
            waitingSince = 0;
            return TOILET_USE_TICKS;
        }
        if (waitingSince == 0) {
            waitingSince = now;
        }
        if (now - waitingSince < TOILET_WAIT_TICKS) {
            return TOILET_RETRY_TICKS;
        }
        // Waited long enough: try another toilet, or give up for a while.
        triedToilets.add(toilet);
        waitingSince = 0;
        current = Errand.EXHIBIT;
        if (nearestToilet(zoo, FacilityCensus.cached(zoo), now).isEmpty()) {
            visitor.remark(CommentType.TOILET_QUEUE, toilet.toString());
            toiletCooldownUntil = now + COOLDOWN_TICKS;
            triedToilets.clear();
        }
        return 0;
    }

    private void finishToiletErrand() {
        current = Errand.EXHIBIT;
        usingUntil = 0;
        waitingSince = 0;
        triedToilets.clear();
    }

    private void buy(Zoo zoo, ServerLevel level, long now) {
        Errand errand = current;
        UUID shop = shopId;
        String item = itemId;
        current = Errand.EXHIBIT;
        Optional<Shop> maybeShop = shop == null ? Optional.empty() : zoo.shop(shop);
        if (maybeShop.isEmpty() || item == null) {
            retryOrGiveUp(errand, shop, now);
            return;
        }
        FacilityReach.clear(zoo, shop);
        ZooManager zoos = ZooManager.get(level.getServer());
        // Prices can change while the visitor walks over; it checks again at the counter.
        int price = ProductCatalog.priceFor(maybeShop.get(), item);
        Optional<Item> itemType = ProductCatalog.item(item);
        ProductCategory category = itemType.map(i -> ProductCatalog.category(new ItemStack(i))).orElse(ProductCategory.SOUVENIR);
        if (price <= visitor.budget && !PriceRules.accepts(price, ProductCatalog.defaultPrice(category), visitor.profile,
                visitor.personalFactor, zoo.reputation())) {
            complainTooExpensive();
            zoos.recordMissedSale(zoo, shop, "too_expensive", item);
            if (errand == Errand.FOOD) {
                giveUpOnFood(now);
            }
            return;
        }
        String key = "sale:" + visitor.visitId + ":" + visitor.saleIndex;
        SaleResult result = zoos.sell(level, zoo, shop, key, item, visitor.budget, "Visitor");
        switch (result) {
            case SOLD -> {
                visitor.budget -= price;
                visitor.saleIndex++;
                visitor.spentInShops += price;
                triedShops.clear();
                itemType.ifPresent(i -> hold(new ItemStack(i), category, now));
            }
            case DUPLICATE -> visitor.saleIndex++; // already sold before a reload: never pay twice
            case OUT_OF_STOCK -> {
                visitor.remark(CommentType.SOLD_OUT);
                retryOrGiveUp(errand, shop, now);
            }
            case SHOP_CLOSED -> retryOrGiveUp(errand, shop, now);
            case CANT_AFFORD -> {
                if (errand == Errand.FOOD) {
                    visitor.remarkOnce(CommentType.CANT_AFFORD);
                    wantsToGoHome = true;
                    giveUpOnFood(now);
                }
            }
            case INVALID_PRICE -> {
                if (errand == Errand.FOOD) {
                    giveUpOnFood(now);
                }
            }
        }
    }

    /** One other shop may be tried; after that the visitor stops trying for a while. */
    private void retryOrGiveUp(Errand errand, @Nullable UUID shop, long now) {
        if (shop != null) {
            triedShops.add(shop);
        }
        if (errand == Errand.FOOD && triedShops.size() >= MAX_SHOPS_PER_ERRAND) {
            giveUpOnFood(now);
        }
    }

    /** The visitor couldn't get to its errand's spot. */
    void onUnreachable(Zoo zoo, long now) {
        Errand errand = current;
        current = Errand.EXHIBIT;
        if ((errand == Errand.FOOD || errand == Errand.SOUVENIR) && shopId != null) {
            FacilityReach.markUnreachable(zoo, shopId, now + UNREACHABLE_TICKS);
            visitor.remark(CommentType.CANT_REACH_SHOP, shopId.toString());
            retryOrGiveUp(errand, shopId, now);
        } else if (errand == Errand.TOILET && toiletId != null) {
            FacilityReach.markUnreachable(zoo, toiletId, now + UNREACHABLE_TICKS);
            visitor.remark(CommentType.CANT_REACH_TOILET, toiletId.toString());
            triedToilets.add(toiletId);
            usingUntil = 0;
            waitingSince = 0;
            if (nearestToilet(zoo, FacilityCensus.cached(zoo), now).isEmpty()) {
                toiletCooldownUntil = now + COOLDOWN_TICKS;
                triedToilets.clear();
            }
        } else if (errand == Errand.BIN && binPos != null) {
            FacilityReach.markUnreachable(zoo, FacilitySnapshot.binId(binPos), now + UNREACHABLE_TICKS);
            binPos = null;
        }
    }

    // ------------------------------------------------------------------ holding

    /** Ticks of eating left, or 0 when not eating. */
    public long eatingTicksLeft() {
        return holdUntil != 0 && eating ? Math.max(0, holdUntil - visitor.level().getGameTime()) : 0;
    }

    /** Saves what the visitor is holding and for how long, so a reload doesn't lose a meal or its rubbish. */
    void save(net.minecraft.nbt.CompoundTag tag, long now) {
        if (holdUntil != 0) {
            tag.putLong("holdLeft", Math.max(0, holdUntil - now));
            tag.putBoolean("eating", eating);
            tag.putBoolean("drink", drink);
        }
        if (rubbishSince != 0) {
            tag.putLong("rubbishAge", Math.max(0, now - rubbishSince));
            tag.putInt("rubbishVariant", rubbishVariant);
        }
    }

    /** @return true when the visitor was holding a purchase or rubbish when saved */
    boolean load(net.minecraft.nbt.CompoundTag tag, long now) {
        holdUntil = 0;
        rubbishSince = 0;
        if (tag.contains("holdLeft")) {
            holdUntil = now + Math.max(1, tag.getLong("holdLeft"));
            eating = tag.getBoolean("eating");
            drink = tag.getBoolean("drink");
        }
        if (tag.contains("rubbishAge")) {
            rubbishSince = Math.max(1, now - tag.getLong("rubbishAge"));
            rubbishVariant = tag.getInt("rubbishVariant");
        }
        return holdUntil != 0 || rubbishSince != 0;
    }

    /** GameTests only. */
    void startEating(ItemStack stack, long now) {
        hold(stack, ProductCategory.FOOD, now);
    }

    private void hold(ItemStack stack, ProductCategory category, long now) {
        visitor.setItemSlot(EquipmentSlot.MAINHAND, stack);
        eating = category == ProductCategory.FOOD || category == ProductCategory.DRINK;
        drink = category == ProductCategory.DRINK;
        holdUntil = now + (eating ? EAT_TICKS : CARRY_TICKS);
    }

    /** Eats or carries the held purchase; called every controller step while exploring. */
    void tickHeld(ServerLevel level, long now) {
        if (holdUntil == 0) {
            return;
        }
        ItemStack held = visitor.getMainHandItem();
        if (eating && !held.isEmpty()) {
            level.playSound(null, visitor, drink ? SoundEvents.GENERIC_DRINK : SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.5f,
                    0.9f + visitor.getRandom().nextFloat() * 0.2f);
            if (!drink) {
                level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, held), visitor.getX(), visitor.getEyeY() - 0.3,
                        visitor.getZ(), 4, 0.1, 0.1, 0.1, 0.05);
            }
        }
        if (now < holdUntil) {
            return;
        }
        holdUntil = 0;
        if (eating) {
            visitor.needs.eat(drink);
            visitor.remark(CommentType.TASTY);
            visitor.zoo().ifPresent(zoo -> ZooManager.get(level.getServer()).recordMeal(zoo));
            startCarryingRubbish(now);
        } else {
            visitor.remark(CommentType.SOUVENIR);
            visitor.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }
}
