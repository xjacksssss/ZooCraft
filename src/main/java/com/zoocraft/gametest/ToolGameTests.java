package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import com.zoocraft.item.BoundaryDraft;
import com.zoocraft.item.BoundaryDrafts;
import com.zoocraft.item.BoundaryToolItem;
import com.zoocraft.item.DesignationToolItem;
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
}
