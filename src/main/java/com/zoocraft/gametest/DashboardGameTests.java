package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import com.zoocraft.dashboard.DashboardAction;
import com.zoocraft.dashboard.DashboardService;
import com.zoocraft.network.DashboardPayloads;
import com.zoocraft.network.DashboardSnapshot;
import com.zoocraft.zoo.ActionResult;
import com.zoocraft.zoo.Emeralds;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ZooCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DashboardGameTests {
    private static DashboardPayloads.Action action(Zoo zoo, DashboardAction action, int number, String text) {
        return new DashboardPayloads.Action(zoo.id(), action, number, text);
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void ownerAndManagerSeeTheSameNumbers(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ServerPlayer manager = TestZoos.player(helper, "Manager");
        ZooManager zoos = TestZoos.manager(helper);
        Zoo zoo = TestZoos.create(helper, owner, "Shared", TestZoos.rectangle(helper, 2, 2, 29, 29));
        zoos.addManager(owner, zoo, manager.getGameProfile());
        zoos.addGate(owner, helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 15)));
        owner.getInventory().add(new ItemStack(Items.EMERALD, 9));
        zoos.deposit(owner, zoo, 9);
        helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(10, 2, 10));

        DashboardSnapshot ownerView = DashboardService.snapshot(owner, zoo);
        DashboardSnapshot managerView = DashboardService.snapshot(manager, zoo);
        helper.assertTrue(ownerView.treasury() == 9 && managerView.treasury() == 9, "treasury differs");
        helper.assertTrue(ownerView.gates().size() == 1 && managerView.gates().size() == 1, "gates differ");
        helper.assertTrue(ownerView.species().size() == 1, "pig not listed: " + ownerView.species());
        helper.assertTrue(ownerView.viewerIsOwner() && !managerView.viewerIsOwner(), "owner flag wrong");
        helper.assertTrue(ownerView.managers().size() == 1, "manager not listed");

        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void removedManagerIsRefused(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ServerPlayer manager = TestZoos.player(helper, "Manager");
        ZooManager zoos = TestZoos.manager(helper);
        Zoo zoo = TestZoos.create(helper, owner, "Revoked", TestZoos.rectangle(helper, 2, 2, 29, 29));
        zoos.addManager(owner, zoo, manager.getGameProfile());
        helper.assertTrue(DashboardService.handle(manager, action(zoo, DashboardAction.SET_PRICE, 4, "")).success(), "manager could not set price");
        helper.assertFalse(DashboardService.handle(manager, action(zoo, DashboardAction.REMOVE_MANAGER, 0, owner.getUUID().toString())).success(),
                "manager used an owner-only action");
        helper.assertTrue(DashboardService.handle(owner, action(zoo, DashboardAction.REMOVE_MANAGER, 0, manager.getUUID().toString())).success(),
                "owner could not remove manager");
        ActionResult after = DashboardService.handle(manager, action(zoo, DashboardAction.SET_PRICE, 9, ""));
        helper.assertFalse(after.success(), "removed manager still changed the price");
        helper.assertTrue(zoo.admissionPrice() == 4, "price changed");
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void simultaneousWithdrawalsPayOutOnce(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ServerPlayer manager = TestZoos.player(helper, "Manager");
        ZooManager zoos = TestZoos.manager(helper);
        Zoo zoo = TestZoos.create(helper, owner, "Bank Run", TestZoos.rectangle(helper, 2, 2, 29, 29));
        zoos.addManager(owner, zoo, manager.getGameProfile());
        owner.getInventory().add(new ItemStack(Items.EMERALD, 5));
        zoos.deposit(owner, zoo, 5);

        boolean first = DashboardService.handle(owner, action(zoo, DashboardAction.WITHDRAW, 5, "")).success();
        boolean second = DashboardService.handle(manager, action(zoo, DashboardAction.WITHDRAW, 5, "")).success();
        helper.assertTrue(first ^ second, "expected exactly one withdrawal to succeed");
        helper.assertTrue(zoo.ledger().balance() == 0, "balance wrong");
        long paidOut = Emeralds.count(owner.getInventory()) + Emeralds.count(manager.getInventory());
        helper.assertTrue(paidOut == 5, "paid out " + paidOut);
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }
}
