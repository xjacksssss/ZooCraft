package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import com.zoocraft.core.needs.Errand;
import com.zoocraft.core.needs.Need;
import com.zoocraft.core.visitor.VisitorProfile;
import com.zoocraft.feedback.CommentType;
import com.zoocraft.registry.ZooEntities;
import com.zoocraft.shop.FacilityCensus;
import com.zoocraft.visitor.Visitor;
import com.zoocraft.visitor.VisitorPhase;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Visitor needs, errands and toilets (Phase 2 spec section 8). */
@GameTestHolder(ZooCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeedsGameTests {
    // A zoo covering x 16..44 of the large floor, gate on the west edge, free entry; shop counter and chest near the middle.
    private static final BlockPos GATE = new BlockPos(16, 2, 24);
    private static final BlockPos COUNTER = new BlockPos(30, 2, 24);
    private static final BlockPos CHEST = new BlockPos(31, 2, 24);
    private static final BlockPos START = new BlockPos(24, 2, 24);

    private record Scene(ServerPlayer owner, ZooManager zoos, Zoo zoo, Visitor visitor) {
    }

    /** An open zoo with a shop whose chest holds {@code count} of {@code item} (no shop when count is negative). */
    private static Scene scene(GameTestHelper helper, Item item, int count, int budget, double hunger) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ZooManager zoos = TestZoos.manager(helper);
        Zoo zoo = TestZoos.create(helper, owner, "Needs", TestZoos.rectangle(helper, 16, 4, 44, 44));
        TestShops.assertOk(helper, zoos.addGate(owner, helper.getLevel(), helper.absolutePos(GATE)));
        zoos.setPrice(owner, zoo, 0);
        zoos.setOpen(owner, zoo, true);
        if (count >= 0) {
            TestShops.assertOk(helper, zoos.createShop(owner, helper.getLevel(), helper.absolutePos(COUNTER)));
            TestShops.placeChest(helper, CHEST, item, count);
            TestShops.assertOk(helper, zoos.linkContainer(owner, helper.getLevel(), helper.absolutePos(CHEST)));
        }
        FacilityCensus.refresh(zoo, helper.getLevel());
        Visitor visitor = ZooEntities.VISITOR.get().create(helper.getLevel());
        BlockPos at = helper.absolutePos(START);
        visitor.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        visitor.begin(zoo, zoo.gates().getFirst(), UUID.randomUUID(), true);
        visitor.testSetBudget(VisitorProfile.GENERAL, budget, 1.0);
        visitor.testSetNeeds(hunger, 0);
        visitor.testSetSouvenirRoll(0.99);
        helper.getLevel().addFreshEntity(visitor);
        visitor.testSetPhase(VisitorPhase.EXPLORING);
        return new Scene(owner, zoos, zoo, visitor);
    }

    private static int tally(Zoo zoo, CommentType type) {
        return zoo.stats().commentTally().getOrDefault(type.key(), 0);
    }

    private static void finish(GameTestHelper helper, Scene scene) {
        scene.visitor().discard();
        TestZoos.cleanUp(helper, scene.owner(), scene.zoo());
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1200)
    public static void hungryVisitorBuysFoodOnce(GameTestHelper helper) {
        Scene s = scene(helper, Items.BREAD, 5, 10, 70);
        helper.succeedWhen(() -> {
            helper.assertTrue(TestShops.count(helper, CHEST, Items.BREAD) == 4, "bread not bought yet: errand " + s.visitor().errands().current() + " phase " + s.visitor().phase() + " at " + helper.relativePos(s.visitor().blockPosition()) + " target " + (s.visitor().controller().target() == null ? null : helper.relativePos(s.visitor().controller().target())) + " hunger " + Math.round(s.visitor().needs().hunger()) + " budget " + s.visitor().budget() + " tally " + s.zoo().stats().commentTally() + " missed " + s.zoo().shops().getFirst().stats().missedToday());
            helper.assertTrue(s.zoo().ledger().balance() == 2, "treasury should be 2, is " + s.zoo().ledger().balance());
            helper.assertTrue(s.visitor().budget() == 8, "budget should be 8, is " + s.visitor().budget());
            helper.assertTrue(s.visitor().needs().hunger() < 70, "still hungry: " + s.visitor().needs().hunger());
            s.visitor().discard();
            TestZoos.cleanUp(helper, s.owner(), s.zoo());
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 400)
    public static void hungryVisitorWithNoShopComplainsOnce(GameTestHelper helper) {
        Scene s = scene(helper, Items.BREAD, -1, 10, 70);
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(tally(s.zoo(), CommentType.HUNGRY_NO_FOOD) == 1, "complaints: " + tally(s.zoo(), CommentType.HUNGRY_NO_FOOD));
            helper.assertTrue(s.visitor().phase() == VisitorPhase.EXPLORING, "should keep exploring, is " + s.visitor().phase());
            finish(helper, s);
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1200)
    public static void soldOutShopIsTriedOnceThenLeftAlone(GameTestHelper helper) {
        Scene s = scene(helper, Items.BREAD, 5, 10, 70);
        ((Container) helper.getBlockEntity(CHEST)).clearContent(); // the cached snapshot still says there is bread
        helper.succeedWhen(() -> {
            helper.assertTrue(s.zoo().shops().getFirst().stats().missedToday(TestShops.BREAD) >= 1, "no missed sale yet");
            helper.assertTrue(tally(s.zoo(), CommentType.SOLD_OUT) == 1, "should say it's sold out once");
            helper.assertTrue(s.visitor().errands().current() != Errand.FOOD, "still on the food errand");
            helper.assertTrue(s.visitor().phase() == VisitorPhase.EXPLORING, "should keep exploring");
            helper.assertTrue(s.zoo().ledger().balance() == 0, "paid for nothing");
            s.visitor().discard();
            TestZoos.cleanUp(helper, s.owner(), s.zoo());
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 600)
    public static void closingTheZooStopsAnErrand(GameTestHelper helper) {
        Scene s = scene(helper, Items.BREAD, 5, 10, 70);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(s.visitor().errands().current() == Errand.FOOD, "should be heading for food, is " + s.visitor().errands().current());
            s.zoos().setOpen(s.owner(), s.zoo(), false);
        });
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(s.visitor().isRemoved() || s.visitor().phase() != VisitorPhase.EXPLORING, "still exploring a closed zoo");
            helper.assertTrue(TestShops.count(helper, CHEST, Items.BREAD) == 5, "bought food in a closed zoo");
            helper.assertTrue(s.zoo().ledger().balance() == 0, "charged in a closed zoo");
            finish(helper, s);
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1200)
    public static void visitorLeavingByChoiceBuysASouvenir(GameTestHelper helper) {
        Scene s = scene(helper, Items.POPPY, 5, 10, 0);
        s.visitor().testSetSouvenirRoll(0);
        s.visitor().testSetInterest(0);
        helper.succeedWhen(() -> {
            helper.assertTrue(TestShops.count(helper, CHEST, Items.POPPY) == 4, "no souvenir bought yet");
            helper.assertTrue(s.zoo().ledger().balance() == 3, "souvenir price should be 3");
            helper.assertTrue(s.visitor().isRemoved() || s.visitor().phase() != VisitorPhase.EXPLORING, "should be on the way out");
            s.visitor().discard();
            TestZoos.cleanUp(helper, s.owner(), s.zoo());
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 600)
    public static void visitorWhoCantAffordFoodSaysSoAndLeaves(GameTestHelper helper) {
        Scene s = scene(helper, Items.BREAD, 5, 1, 70);
        helper.succeedWhen(() -> {
            helper.assertTrue(tally(s.zoo(), CommentType.CANT_AFFORD) == 1, "should say it can't afford anything");
            helper.assertTrue(s.visitor().isRemoved() || s.visitor().phase() != VisitorPhase.EXPLORING, "should be leaving");
            helper.assertTrue(TestShops.count(helper, CHEST, Items.BREAD) == 5 && s.zoo().ledger().balance() == 0, "sold something anyway");
            s.visitor().discard();
            TestZoos.cleanUp(helper, s.owner(), s.zoo());
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void needsAndShoppingStateSurviveSaveAndLoad(GameTestHelper helper) {
        Visitor visitor = ZooEntities.VISITOR.get().create(helper.getLevel());
        visitor.testSetNeeds(70, 40);
        visitor.testSetSouvenirRoll(0.2);
        visitor.testSetShopping(3, true, 9);

        CompoundTag saved = new CompoundTag();
        visitor.saveWithoutId(saved);
        Visitor copy = ZooEntities.VISITOR.get().create(helper.getLevel());
        copy.load(saved);

        helper.assertTrue(Math.abs(copy.needs().level(Need.HUNGER) - 70) < 1e-9, "hunger lost: " + copy.needs().hunger());
        helper.assertTrue(Math.abs(copy.needs().level(Need.TOILET) - 40) < 1e-9, "toilet need lost");
        helper.assertTrue(Math.abs(copy.needs().hungerRate() - visitor.needs().hungerRate()) < 1e-9, "hunger rate lost");
        helper.assertTrue(copy.saleIndex() == 3, "sale index lost: " + copy.saleIndex());
        helper.assertTrue(copy.lastStopDone(), "last stop flag lost");
        helper.assertTrue(copy.spentInShops() == 9, "spending lost");
        helper.assertTrue(Math.abs(copy.souvenirRoll() - 0.2) < 1e-9, "souvenir roll lost");
        visitor.discard();
        copy.discard();
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void phaseOneVisitorLoadsWithFreshNeeds(GameTestHelper helper) {
        Visitor visitor = ZooEntities.VISITOR.get().create(helper.getLevel());
        CompoundTag saved = new CompoundTag();
        visitor.saveWithoutId(saved);
        CompoundTag visit = saved.getCompound("ZooVisit");
        for (String key : new String[]{"needs", "saleIndex", "souvenirRoll", "lastStopDone", "spentInShops"}) {
            visit.remove(key);
        }
        Visitor old = ZooEntities.VISITOR.get().create(helper.getLevel());
        old.load(saved);
        helper.assertTrue(old.saleIndex() == 0 && !old.lastStopDone() && old.spentInShops() == 0, "shopping state should start empty");
        helper.assertTrue(old.needs().hungerRate() >= 0.14 && old.needs().hungerRate() <= 0.25, "needs should be rolled: " + old.needs().hungerRate());
        helper.assertTrue(old.souvenirRoll() >= 0 && old.souvenirRoll() < 1, "souvenir roll should be rolled");
        visitor.discard();
        old.discard();
        helper.succeed();
    }
}
