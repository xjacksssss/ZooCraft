package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import com.zoocraft.animal.AnimalCensus;
import com.zoocraft.animal.Exhibit;
import com.zoocraft.dashboard.DashboardService;
import com.zoocraft.dashboard.DashboardTab;
import com.zoocraft.litter.LitterEntity;
import com.zoocraft.litter.LitterTracker;
import com.zoocraft.litter.Littering;
import com.zoocraft.registry.ZooEntities;
import com.zoocraft.shop.FacilityCensus;
import com.zoocraft.visitor.Visitor;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Follow-ups after Phase 2: things that should survive a restart, and small dashboard and cleanliness fixes. */
@GameTestHolder(ZooCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FollowUpGameTests {
    @GameTest(template = GameTestTemplates.FLOOR)
    public static void exhibitViewCountsSurviveARestart(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ZooManager zoos = TestZoos.manager(helper);
        Zoo zoo = TestZoos.create(helper, owner, "Remembered", TestZoos.rectangle(helper, 2, 2, 29, 29));
        helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(10, 2, 10));
        helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(11, 2, 10));
        Exhibit exhibit = AnimalCensus.refresh(zoo, helper.getLevel()).exhibits().getFirst();
        exhibit.addView();
        exhibit.addView();
        exhibit.addView();
        AnimalCensus.refresh(zoo, helper.getLevel()); // views are saved with each census

        // A restart: the census cache is gone and the zoo is read back from its saved form.
        AnimalCensus.forgetForTest(zoo);
        Zoo reloaded = zoos.reloadForTest(zoo, false);
        Exhibit after = AnimalCensus.refresh(reloaded, helper.getLevel()).exhibits().getFirst();
        helper.assertTrue(after.viewCount() == 3, "view count after a restart is " + after.viewCount());
        helper.assertTrue(after.id().equals(exhibit.id()), "the exhibit should keep its identity");
        AnimalCensus.forgetForTest(reloaded);
        TestZoos.cleanUp(helper, owner, reloaded);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void overviewShowsTodaysShopIncome(GameTestHelper helper) {
        TestShops.Setup s = TestShops.setUp(helper, "Income Today");
        s.zoos().sell(helper.getLevel(), s.zoo(), s.shop().id(), "sale:t:0", TestShops.BREAD, 10, "Test");
        helper.assertTrue(DashboardService.snapshot(s.owner(), s.zoo(), DashboardTab.OVERVIEW).shopIncomeToday() == 2, "today's shop income missing");
        s.zoos().rollDayForTest(s.zoo(), s.zoo().reports().history().today().day + 1);
        var next = DashboardService.snapshot(s.owner(), s.zoo(), DashboardTab.OVERVIEW);
        helper.assertTrue(next.shopIncomeToday() == 0 && next.shopIncome() == 2, "a new day should start at 0 with the total kept");
        TestZoos.cleanUp(helper, s.owner(), s.zoo());
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void aVisitorReloadedMidMealFinishesIt(GameTestHelper helper) {
        Visitor visitor = ZooEntities.VISITOR.get().create(helper.getLevel());
        visitor.testStartEating(new ItemStack(Items.BREAD));
        CompoundTag saved = new CompoundTag();
        visitor.saveWithoutId(saved);
        Visitor copy = ZooEntities.VISITOR.get().create(helper.getLevel());
        copy.load(saved);
        helper.assertTrue(copy.getMainHandItem().is(Items.BREAD), "the meal was lost on reload");
        helper.assertTrue(copy.errands().eatingTicksLeft() > 0, "the reloaded visitor isn't eating any more");
        visitor.discard();
        copy.discard();
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void cleanlinessMovesWithNoVisitorsAround(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ZooManager zoos = TestZoos.manager(helper);
        Zoo zoo = TestZoos.create(helper, owner, "Empty Clean", TestZoos.rectangle(helper, 2, 2, 29, 29));
        for (int i = 0; i < 30; i++) {
            zoos.recordCleanlinessSample(zoo, 0);
        }
        double dirty = zoo.stats().cleanliness();
        FacilityCensus.refresh(zoo, helper.getLevel());
        helper.assertTrue(zoo.stats().cleanliness() > dirty, "a litter-free empty zoo should get cleaner: " + zoo.stats().cleanliness());

        for (int i = 0; i < 30; i++) {
            zoos.recordCleanlinessSample(zoo, 100);
        }
        BlockPos at = helper.absolutePos(new BlockPos(10, 2, 10));
        for (int i = 0; i < 6; i++) {
            Littering.drop(helper.getLevel(), zoo, new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5), i);
        }
        double clean = zoo.stats().cleanliness();
        FacilityCensus.refresh(zoo, helper.getLevel());
        helper.assertTrue(zoo.stats().cleanliness() < clean, "an empty zoo full of litter should get dirtier");
        List.copyOf(LitterTracker.litterIn(zoo)).forEach(LitterEntity::discard);
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }
}
