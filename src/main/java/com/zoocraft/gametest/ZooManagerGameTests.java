package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import com.zoocraft.zoo.ActionResult;
import com.zoocraft.zoo.Emeralds;
import com.zoocraft.zoo.GateStatus;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ZooCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZooManagerGameTests {
    @GameTest(template = GameTestTemplates.FLOOR)
    public static void createSetsOwnerAndIsFoundByPosition(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Create", TestZoos.rectangle(helper, 2, 2, 29, 29));
        ZooManager zoos = TestZoos.manager(helper);
        helper.assertTrue(zoo.owner().equals(owner.getUUID()), "owner not set");
        helper.assertTrue(zoos.zooAt(helper.getLevel(), helper.absolutePos(new BlockPos(15, 1, 15))).orElse(null) == zoo, "zoo not found inside");
        helper.assertTrue(zoos.zooAt(helper.getLevel(), helper.absolutePos(new BlockPos(31, 1, 15))).isEmpty(), "zoo found outside");
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void overlappingZooIsRefused(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo first = TestZoos.create(helper, owner, "First", TestZoos.rectangle(helper, 2, 2, 20, 20));
        ActionResult second = TestZoos.manager(helper).createZoo(owner, "Second " + first.id().toString().substring(0, 8), helper.getLevel().dimension(),
                TestZoos.rectangle(helper, 10, 10, 29, 29));
        helper.assertFalse(second.success(), "overlapping zoo was created");
        helper.assertTrue(second.message().getString().contains(first.name()), "message should name the other zoo: " + second.message().getString());
        TestZoos.cleanUp(helper, owner, first);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void duplicateNameIsRefusedIgnoringCase(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo first = TestZoos.create(helper, owner, "Named", TestZoos.rectangle(helper, 2, 2, 14, 29));
        ActionResult second = TestZoos.manager(helper).createZoo(owner, first.name().toUpperCase(java.util.Locale.ROOT),
                helper.getLevel().dimension(), TestZoos.rectangle(helper, 17, 2, 29, 29));
        helper.assertFalse(second.success(), "duplicate name was accepted");
        TestZoos.cleanUp(helper, owner, first);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void managerCanSetPriceButNotManagePeople(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ServerPlayer manager = TestZoos.player(helper, "Manager");
        ServerPlayer stranger = TestZoos.player(helper, "Stranger");
        ZooManager zoos = TestZoos.manager(helper);
        Zoo zoo = TestZoos.create(helper, owner, "Perms", TestZoos.rectangle(helper, 2, 2, 29, 29));
        helper.assertTrue(zoos.addManager(owner, zoo, manager.getGameProfile()).success(), "owner could not add manager");

        helper.assertTrue(zoos.setPrice(manager, zoo, 5).success(), "manager could not set price");
        helper.assertFalse(zoos.addManager(manager, zoo, stranger.getGameProfile()).success(), "manager added a manager");
        helper.assertFalse(zoos.transfer(manager, zoo, manager.getGameProfile()).success(), "manager took ownership");
        helper.assertFalse(zoos.delete(manager, zoo).success(), "manager deleted the zoo");
        helper.assertFalse(zoos.setPrice(stranger, zoo, 1).success(), "stranger set the price");
        helper.assertTrue(zoo.admissionPrice() == 5, "price changed by stranger");

        helper.assertTrue(zoos.removeManager(owner, zoo, manager.getUUID(), "Manager").success(), "owner could not remove manager");
        helper.assertFalse(zoos.setPrice(manager, zoo, 9).success(), "removed manager still has access");
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void transferMakesNewOwnerAndDropsThemFromManagers(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ServerPlayer manager = TestZoos.player(helper, "Manager");
        ZooManager zoos = TestZoos.manager(helper);
        Zoo zoo = TestZoos.create(helper, owner, "Transfer", TestZoos.rectangle(helper, 2, 2, 29, 29));
        zoos.addManager(owner, zoo, manager.getGameProfile());
        helper.assertTrue(zoos.transfer(owner, zoo, manager.getGameProfile()).success(), "transfer failed");
        helper.assertTrue(zoo.owner().equals(manager.getUUID()), "owner not changed");
        helper.assertFalse(zoo.managers().contains(manager.getUUID()), "new owner still listed as manager");
        helper.assertFalse(zoos.setPrice(owner, zoo, 3).success(), "old owner kept access");
        TestZoos.cleanUp(helper, manager, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void boundaryEditDetachesGateAndKeepsTreasury(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ZooManager zoos = TestZoos.manager(helper);
        Zoo zoo = TestZoos.create(helper, owner, "Edit", TestZoos.rectangle(helper, 2, 2, 29, 29));
        helper.assertTrue(zoos.addGate(owner, helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 15))).success(), "gate not added");
        owner.getInventory().add(new ItemStack(Items.EMERALD, 12));
        zoos.deposit(owner, zoo, 12);

        ActionResult edit = zoos.editBoundary(owner, zoo, TestZoos.rectangle(helper, 12, 2, 29, 29));
        helper.assertTrue(edit.success(), "edit failed: " + edit.message().getString());
        helper.assertTrue(edit.notes().size() == 1, "expected one detached gate note, got " + edit.notes().size());
        helper.assertTrue(zoo.gates().size() == 1, "gate was deleted");
        helper.assertTrue(zoo.gateStatus(zoo.gates().getFirst(), helper.getLevel().getGameTime()) == GateStatus.DETACHED, "gate not detached");
        helper.assertTrue(zoo.ledger().balance() == 12, "treasury changed");

        zoos.editBoundary(owner, zoo, TestZoos.rectangle(helper, 2, 2, 29, 29));
        helper.assertTrue(zoo.gateStatus(zoo.gates().getFirst(), helper.getLevel().getGameTime()) == GateStatus.OPEN, "gate not re-attached");
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void invalidEditKeepsOldBoundary(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ZooManager zoos = TestZoos.manager(helper);
        Zoo zoo = TestZoos.create(helper, owner, "Bad Edit", TestZoos.rectangle(helper, 2, 2, 29, 29));
        var before = zoo.boundary();
        ActionResult edit = zoos.editBoundary(owner, zoo, TestZoos.rectangle(helper, 2, 2, 4, 4));
        helper.assertFalse(edit.success(), "tiny boundary accepted");
        helper.assertTrue(zoo.boundary().equals(before), "boundary changed after a failed edit");
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void deleteRefusedWhileTreasuryHasFunds(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ZooManager zoos = TestZoos.manager(helper);
        Zoo zoo = TestZoos.create(helper, owner, "Funds", TestZoos.rectangle(helper, 2, 2, 29, 29));
        owner.getInventory().add(new ItemStack(Items.EMERALD, 3));
        zoos.deposit(owner, zoo, 3);
        helper.assertFalse(zoos.delete(owner, zoo).success(), "deleted a zoo with funds");
        helper.assertTrue(zoos.byId(zoo.id()).isPresent(), "zoo gone");
        zoos.withdraw(owner, zoo, 3);
        helper.assertTrue(zoos.delete(owner, zoo).success(), "could not delete empty zoo");
        helper.assertTrue(zoos.byId(zoo.id()).isEmpty(), "zoo still present");
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void depositAndWithdrawMoveExactEmeralds(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ZooManager zoos = TestZoos.manager(helper);
        Zoo zoo = TestZoos.create(helper, owner, "Bank", TestZoos.rectangle(helper, 2, 2, 29, 29));
        owner.getInventory().add(new ItemStack(Items.EMERALD_BLOCK, 2));
        owner.getInventory().add(new ItemStack(Items.EMERALD, 5));
        helper.assertTrue(Emeralds.count(owner.getInventory()) == 23, "setup count wrong");

        helper.assertFalse(zoos.deposit(owner, zoo, 24).success(), "deposited more than held");
        helper.assertTrue(Emeralds.count(owner.getInventory()) == 23, "failed deposit took emeralds");

        helper.assertTrue(zoos.deposit(owner, zoo, 20).success(), "deposit failed");
        helper.assertTrue(Emeralds.count(owner.getInventory()) == 3, "wrong emeralds left: " + Emeralds.count(owner.getInventory()));
        helper.assertTrue(zoo.ledger().balance() == 20, "balance wrong");

        helper.assertFalse(zoos.withdraw(owner, zoo, 21).success(), "withdrew more than balance");
        helper.assertTrue(Emeralds.count(owner.getInventory()) == 3, "failed withdrawal gave emeralds");
        helper.assertTrue(zoo.ledger().balance() == 20, "failed withdrawal changed balance");

        helper.assertTrue(zoos.withdraw(owner, zoo, 20).success(), "withdraw failed");
        helper.assertTrue(Emeralds.count(owner.getInventory()) == 23, "withdraw gave wrong amount");
        helper.assertTrue(zoo.ledger().balance() == 0, "balance not zero");
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void admissionChargedOncePerVisit(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ZooManager zoos = TestZoos.manager(helper);
        Zoo zoo = TestZoos.create(helper, owner, "Tickets", TestZoos.rectangle(helper, 2, 2, 29, 29));
        java.util.UUID visit = java.util.UUID.randomUUID();
        helper.assertTrue(zoos.chargeAdmission(zoo, visit, 4).applied(), "first charge failed");
        helper.assertFalse(zoos.chargeAdmission(zoo, visit, 4).applied(), "second charge applied");
        helper.assertTrue(zoo.ledger().balance() == 4, "charged twice");
        helper.assertTrue(zoo.stats().totalAttendance() == 1, "attendance counted twice");
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void openingNeedsAGate(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ZooManager zoos = TestZoos.manager(helper);
        Zoo zoo = TestZoos.create(helper, owner, "Gates", TestZoos.rectangle(helper, 2, 2, 29, 29));
        helper.assertFalse(zoos.setOpen(owner, zoo, true).success(), "opened without a gate");
        helper.assertFalse(zoos.addGate(owner, helper.getLevel(), helper.absolutePos(new BlockPos(15, 1, 15))).success(), "gate far from edge accepted");
        helper.assertTrue(zoos.addGate(owner, helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 15))).success(), "gate near edge refused");
        helper.assertTrue(zoos.setOpen(owner, zoo, true).success(), "could not open with a gate");
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }
}
