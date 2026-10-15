package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import com.zoocraft.core.report.DayRecord;
import com.zoocraft.core.visitor.VisitorProfile;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Daily reports, recommendations and complaint subjects (Phase 2 spec section 10). */
@GameTestHolder(ZooCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReportGameTests {
    @GameTest(template = GameTestTemplates.FLOOR)
    public static void todaysRecordFollowsTheMoney(GameTestHelper helper) {
        TestShops.Setup s = TestShops.setUp(helper, "Report Money");
        ZooManager zoos = s.zoos();
        s.owner().getInventory().add(new ItemStack(Items.EMERALD, 10));
        zoos.deposit(s.owner(), s.zoo(), 10);
        zoos.chargeAdmission(s.zoo(), UUID.randomUUID(), 3, VisitorProfile.ANIMAL_ENTHUSIAST);
        zoos.sell(helper.getLevel(), s.zoo(), s.shop().id(), "sale:r:0", TestShops.BREAD, 10, "Test");
        zoos.withdraw(s.owner(), s.zoo(), 4);
        DayRecord today = s.zoo().reports().history().today();
        helper.assertTrue(today.deposits == 10 && today.withdrawals == 4, "deposits/withdrawals " + today.deposits + "/" + today.withdrawals);
        helper.assertTrue(today.admission == 3 && today.shopSales == 2, "income " + today.admission + "/" + today.shopSales);
        helper.assertTrue(today.arrivals == 1 && today.enthusiastVisitors == 1, "arrival not counted");
        helper.assertTrue(today.spending == 5, "visitor spending " + today.spending);
        helper.assertTrue(today.admission == s.zoo().ledger().totalAdmission() && today.shopSales == s.zoo().ledger().totalShopSales(),
                "today's record disagrees with the ledger");
        TestZoos.cleanUp(helper, s.owner(), s.zoo());
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void rollingOverClosesTheDayWithTheTreasury(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ZooManager zoos = TestZoos.manager(helper);
        Zoo zoo = TestZoos.create(helper, owner, "Report Roll", TestZoos.rectangle(helper, 2, 2, 29, 29));
        zoos.chargeAdmission(zoo, UUID.randomUUID(), 6, VisitorProfile.GENERAL);
        long day = zoo.reports().history().today().day;
        zoos.rollDayForTest(zoo, day + 1);
        DayRecord yesterday = zoo.reports().history().past().getLast();
        helper.assertTrue(yesterday.day == day && yesterday.admission == 6, "yesterday lost");
        helper.assertTrue(yesterday.closingBalance == 6, "closing balance " + yesterday.closingBalance);
        helper.assertTrue(zoo.reports().history().today().admission == 0, "today should start at zero");
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void phaseOneZoosGetTheirOwnEmptyReports(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ZooManager zoos = TestZoos.manager(helper);
        Zoo a = zoos.reloadForTest(TestZoos.create(helper, owner, "Old Report A", TestZoos.rectangle(helper, 2, 2, 14, 29)), true);
        Zoo b = zoos.reloadForTest(TestZoos.create(helper, owner, "Old Report B", TestZoos.rectangle(helper, 17, 2, 29, 29)), true);
        helper.assertTrue(a.reports().history().past().isEmpty(), "a Phase 1 zoo should have no history");
        zoos.chargeAdmission(a, UUID.randomUUID(), 5, VisitorProfile.GENERAL);
        helper.assertTrue(b.reports().history().today().admission == 0, "one zoo's takings appeared in another's report");
        Zoo reloaded = zoos.reloadForTest(a, false);
        helper.assertTrue(reloaded.reports().history().today().admission == 5, "the report was lost on save");
        TestZoos.cleanUp(helper, owner, reloaded);
        TestZoos.cleanUp(helper, owner, b);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void happyVisitorsRecommendTheZoo(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ZooManager zoos = TestZoos.manager(helper);
        Zoo zoo = TestZoos.create(helper, owner, "Report Recommend", TestZoos.rectangle(helper, 2, 2, 29, 29));
        zoos.recordDeparture(zoo, 80, 20 * 60 * 5);
        zoos.recordDeparture(zoo, 40, 20 * 60 * 3);
        helper.assertTrue(zoo.reports().recommendations().size() == 2, "departures not counted");
        helper.assertTrue(zoo.reports().recommendations().recommended() == 1, "only the happy visitor recommends");
        DayRecord today = zoo.reports().history().today();
        helper.assertTrue(today.departures == 2 && today.visitTicks == 20 * 60 * 8, "visit lengths not recorded");
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void complaintsKeepTheirSubject(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ZooManager zoos = TestZoos.manager(helper);
        Zoo zoo = TestZoos.create(helper, owner, "Report Subject", TestZoos.rectangle(helper, 2, 2, 29, 29));
        String toilet = UUID.randomUUID().toString();
        zoos.recordComment(zoo, "toilet_queue", toilet);
        zoos.recordComment(zoo, "toilet_queue", toilet);
        helper.assertTrue(zoo.reports().subjectsToday().getOrDefault("toilet_queue|" + toilet, 0) == 2, "subject not tallied");
        helper.assertTrue(zoo.stats().commentTally().getOrDefault("toilet_queue", 0) == 2, "comment not tallied");
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }
}
