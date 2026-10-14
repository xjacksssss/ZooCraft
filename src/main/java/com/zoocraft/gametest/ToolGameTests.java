package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import com.zoocraft.item.BoundaryDraft;
import com.zoocraft.item.BoundaryDrafts;
import com.zoocraft.item.BoundaryToolItem;
import com.zoocraft.item.DesignationMode;
import com.zoocraft.item.DesignationToolItem;
import com.zoocraft.registry.ZooItems;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Drives the tools the way a player would. Clicks target floor blocks at relative y=1. */
@GameTestHolder(ZooCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ToolGameTests {
    private static void click(GameTestHelper helper, ServerPlayer player, int x, int z, boolean sneak) {
        player.setShiftKeyDown(sneak);
        BoundaryToolItem.click(player, helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, z)));
        player.setShiftKeyDown(false);
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void drawCloseAndNameCreatesZoo(GameTestHelper helper) {
        ServerPlayer player = TestZoos.player(helper, "Builder");
        click(helper, player, 2, 2, false);
        click(helper, player, 2, 2, false); // double click is ignored
        click(helper, player, 25, 2, false);
        click(helper, player, 25, 25, false);
        click(helper, player, 9, 9, false);
        click(helper, player, 9, 9, true); // undo the stray corner
        click(helper, player, 2, 25, false);
        BoundaryDraft draft = BoundaryDrafts.get(player).orElseThrow();
        helper.assertTrue(draft.vertices().size() == 4, "expected 4 corners, got " + draft.vertices().size());
        click(helper, player, 2, 2, false); // close on the first corner
        helper.assertTrue(BoundaryDrafts.get(player).orElseThrow().closed(), "draft not closed");

        String name = "Drawn " + UUID.randomUUID().toString().substring(0, 6);
        BoundaryDrafts.finishCreate(player, name);
        ZooManager zoos = TestZoos.manager(helper);
        Zoo zoo = zoos.byName(name).orElse(null);
        helper.assertTrue(zoo != null, "zoo not created");
        helper.assertTrue(BoundaryDrafts.get(player).isEmpty(), "draft not cleared");
        helper.assertTrue(zoo.contains(helper.absolutePos(new BlockPos(10, 1, 10))), "zoo does not contain its middle");
        TestZoos.cleanUp(helper, player, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void cancellingNameKeepsDraft(GameTestHelper helper) {
        ServerPlayer player = TestZoos.player(helper, "Builder");
        click(helper, player, 2, 2, false);
        click(helper, player, 25, 2, false);
        click(helper, player, 25, 25, false);
        click(helper, player, 2, 2, false);
        BoundaryDrafts.finishCreate(player, "");
        BoundaryDraft draft = BoundaryDrafts.get(player).orElseThrow();
        helper.assertFalse(draft.closed(), "draft should reopen after cancelling the name");
        helper.assertTrue(draft.vertices().size() == 3, "corners lost");
        BoundaryDrafts.clear(player);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void editMovesCornerAndSaves(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Editable", TestZoos.rectangle(helper, 2, 2, 25, 25));
        click(helper, owner, 10, 10, false); // inside the zoo: starts editing
        BoundaryDraft draft = BoundaryDrafts.get(owner).orElseThrow();
        helper.assertTrue(draft.mode() == BoundaryDraft.Mode.EDIT, "not in edit mode");
        click(helper, owner, 25, 25, false); // pick up the far corner
        helper.assertTrue(BoundaryDrafts.get(owner).orElseThrow().held() >= 0, "corner not picked up");
        click(helper, owner, 29, 29, false); // put it down further out
        click(helper, owner, 13, 2, true); // sneak on the top edge inserts a corner
        helper.assertTrue(BoundaryDrafts.get(owner).orElseThrow().vertices().size() == 5, "corner not inserted");

        helper.assertTrue(BoundaryDrafts.saveEdit(owner).success(), "save failed");
        helper.assertTrue(zoo.contains(helper.absolutePos(new BlockPos(28, 1, 28))), "boundary not moved");
        helper.assertTrue(zoo.boundary().size() == 5, "inserted corner not saved");
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void strangerCannotEditOrAddGates(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ServerPlayer stranger = TestZoos.player(helper, "Stranger");
        Zoo zoo = TestZoos.create(helper, owner, "Private", TestZoos.rectangle(helper, 2, 2, 25, 25));
        click(helper, stranger, 10, 10, false);
        helper.assertTrue(BoundaryDrafts.get(stranger).isEmpty(), "stranger started editing");
        DesignationToolItem.click(stranger, helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 12)));
        helper.assertTrue(zoo.gates().isEmpty(), "stranger added a gate");
        DesignationToolItem.click(owner, helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 12)));
        helper.assertTrue(zoo.gates().size() == 1, "owner could not add a gate");
        helper.assertTrue(zoo.gates().getFirst().pos().equals(helper.absolutePos(new BlockPos(2, 2, 12))), "gate should stand on top of the clicked floor, was " + helper.relativePos(zoo.gates().getFirst().pos()) + " block " + helper.getBlockState(new BlockPos(2, 2, 12)));
        owner.setShiftKeyDown(true);
        DesignationToolItem.click(owner, helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 12)));
        owner.setShiftKeyDown(false);
        helper.assertTrue(zoo.gates().isEmpty(), "gate not removed");
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void shopModeCreatesCounterAndLinksAndUnlinksChest(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Tool Shop", TestZoos.rectangle(helper, 2, 2, 29, 29));
        DesignationToolItem.click(owner, helper.getLevel(), helper.absolutePos(new BlockPos(10, 1, 10)), DesignationMode.SHOP);
        helper.assertTrue(zoo.shops().size() == 1, "counter not created");
        helper.assertTrue(zoo.shops().getFirst().counter().equals(helper.absolutePos(new BlockPos(10, 2, 10))), "counter should stand on the clicked floor");
        TestShops.placeChest(helper, new BlockPos(11, 2, 10), Items.BREAD, 3);
        DesignationToolItem.click(owner, helper.getLevel(), helper.absolutePos(new BlockPos(11, 2, 10)), DesignationMode.SHOP);
        helper.assertTrue(zoo.shops().getFirst().containers().size() == 1, "chest not linked");
        owner.setShiftKeyDown(true);
        DesignationToolItem.click(owner, helper.getLevel(), helper.absolutePos(new BlockPos(11, 2, 10)), DesignationMode.SHOP);
        helper.assertTrue(zoo.shops().getFirst().containers().isEmpty(), "chest not unlinked");
        DesignationToolItem.click(owner, helper.getLevel(), helper.absolutePos(new BlockPos(10, 1, 10)), DesignationMode.SHOP);
        owner.setShiftKeyDown(false);
        helper.assertTrue(zoo.shops().isEmpty(), "shop not removed");
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void toiletModeAddsAndRemovesToilet(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Tool Toilet", TestZoos.rectangle(helper, 2, 2, 29, 29));
        DesignationToolItem.click(owner, helper.getLevel(), helper.absolutePos(new BlockPos(15, 1, 15)), DesignationMode.TOILET);
        helper.assertTrue(zoo.toilets().size() == 1, "toilet not created");
        owner.setShiftKeyDown(true);
        DesignationToolItem.click(owner, helper.getLevel(), helper.absolutePos(new BlockPos(15, 1, 15)), DesignationMode.TOILET);
        owner.setShiftKeyDown(false);
        helper.assertTrue(zoo.toilets().isEmpty(), "toilet not removed");
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void modesCycleAndAreStoredOnTheTool(GameTestHelper helper) {
        helper.assertTrue(DesignationMode.TOILET.next(1) == DesignationMode.GATE, "TOILET +1 should wrap to GATE");
        helper.assertTrue(DesignationMode.GATE.next(-1) == DesignationMode.TOILET, "GATE -1 should wrap to TOILET");
        ItemStack tool = new ItemStack(ZooItems.DESIGNATION_TOOL.get());
        helper.assertTrue(DesignationToolItem.mode(tool) == DesignationMode.GATE, "new tool should be in Gate mode");
        DesignationToolItem.setMode(tool, DesignationMode.SHOP);
        helper.assertTrue(DesignationToolItem.mode(tool) == DesignationMode.SHOP, "mode not stored");
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void toolInShopModeLinksChestInsteadOfOpeningIt(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ItemStack tool = new ItemStack(ZooItems.DESIGNATION_TOOL.get());
        DesignationToolItem.setMode(tool, DesignationMode.SHOP);
        owner.setItemInHand(InteractionHand.MAIN_HAND, tool);
        TestShops.placeChest(helper, new BlockPos(11, 2, 10), Items.BREAD, 1);
        BlockPos chest = helper.absolutePos(new BlockPos(11, 2, 10));
        PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(owner, InteractionHand.MAIN_HAND, chest,
                new BlockHitResult(Vec3.atCenterOf(chest), Direction.UP, chest, false));
        DesignationToolItem.onRightClickBlock(event);
        helper.assertTrue(event.getUseBlock() == TriState.FALSE, "the chest would open instead of being linked");
        helper.succeed();
    }
}
