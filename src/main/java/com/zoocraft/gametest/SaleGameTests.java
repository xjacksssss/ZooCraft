package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import com.zoocraft.core.shop.SaleResult;
import com.zoocraft.zoo.ActionResult;
import com.zoocraft.gametest.TestShops.Setup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Designating shops, linking stock and selling (Phase 2 spec sections 6 and 7). Things stand at relative y=2. */
@GameTestHolder(ZooCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SaleGameTests {
    private static final BlockPos CHEST = TestShops.CHEST;
    private static final String BREAD = TestShops.BREAD;

    private static Setup setUp(GameTestHelper helper, String name) {
        return TestShops.setUp(helper, name);
    }

    private static void placeChest(GameTestHelper helper, BlockPos pos, Item item, int count) {
        TestShops.placeChest(helper, pos, item, count);
    }

    private static int count(GameTestHelper helper, BlockPos pos, Item item) {
        return TestShops.count(helper, pos, item);
    }

    private static void assertOk(GameTestHelper helper, ActionResult result) {
        TestShops.assertOk(helper, result);
    }

    private static SaleResult sell(GameTestHelper helper, Setup s, String key, int budget) {
        return s.zoos().sell(helper.getLevel(), s.zoo(), s.shop().id(), key, BREAD, budget, "Test");
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void saleTakesOneItemAndPaysOnce(GameTestHelper helper) {
        Setup s = setUp(helper, "Sale Once");
        helper.assertTrue(sell(helper, s, "sale:k", 10) == SaleResult.SOLD, "first sale should succeed");
        helper.assertTrue(count(helper, CHEST, Items.BREAD) == 4, "one bread should be gone");
        helper.assertTrue(s.zoo().ledger().balance() == 2, "treasury should rise by the default food price 2");
        helper.assertTrue(sell(helper, s, "sale:k", 10) == SaleResult.DUPLICATE, "same key must not sell twice");
        helper.assertTrue(count(helper, CHEST, Items.BREAD) == 4, "duplicate took stock");
        helper.assertTrue(s.zoo().ledger().balance() == 2, "duplicate paid twice");
        helper.assertTrue(s.shop().stats().unitsToday() == 1, "stats should count one sale");
        TestZoos.cleanUp(helper, s.owner(), s.zoo());
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void emptiedChestFailsWithoutChanges(GameTestHelper helper) {
        Setup s = setUp(helper, "Sale Empty");
        ((Container) helper.getBlockEntity(CHEST)).clearContent();
        helper.assertTrue(sell(helper, s, "sale:e", 10) == SaleResult.OUT_OF_STOCK, "empty chest should be out of stock");
        helper.assertTrue(s.zoo().ledger().balance() == 0, "paid for nothing");
        helper.assertTrue(s.shop().stats().missedToday(BREAD) == 1, "missed sale not recorded");
        TestZoos.cleanUp(helper, s.owner(), s.zoo());
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void brokenChestFailsWithoutChanges(GameTestHelper helper) {
        Setup s = setUp(helper, "Sale Broken");
        helper.setBlock(CHEST, Blocks.AIR);
        helper.assertTrue(sell(helper, s, "sale:b", 10) == SaleResult.OUT_OF_STOCK, "broken chest should be out of stock");
        helper.assertTrue(s.zoo().ledger().balance() == 0, "paid for nothing");
        TestZoos.cleanUp(helper, s.owner(), s.zoo());
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void detachedShopRefusesSales(GameTestHelper helper) {
        Setup s = setUp(helper, "Sale Detached");
        assertOk(helper, s.zoos().editBoundary(s.owner(), s.zoo(), TestZoos.rectangle(helper, 2, 2, 29, 8)));
        helper.assertTrue(sell(helper, s, "sale:d", 10) == SaleResult.SHOP_CLOSED, "detached shop sold");
        helper.assertTrue(count(helper, CHEST, Items.BREAD) == 5, "detached shop took stock");
        TestZoos.cleanUp(helper, s.owner(), s.zoo());
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void unaffordableSaleChangesNothing(GameTestHelper helper) {
        Setup s = setUp(helper, "Sale Poor");
        helper.assertTrue(sell(helper, s, "sale:p", 1) == SaleResult.CANT_AFFORD, "budget 1 bought a price-2 item");
        helper.assertTrue(count(helper, CHEST, Items.BREAD) == 5 && s.zoo().ledger().balance() == 0, "something changed");
        TestZoos.cleanUp(helper, s.owner(), s.zoo());
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void doubleChestLinksOnce(GameTestHelper helper) {
        Setup s = setUp(helper, "Double Chest");
        BlockPos left = new BlockPos(12, 2, 12), right = new BlockPos(13, 2, 12);
        helper.setBlock(left, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
        helper.setBlock(right, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
        assertOk(helper, s.zoos().linkContainer(s.owner(), helper.getLevel(), helper.absolutePos(left)));
        ActionResult second = s.zoos().linkContainer(s.owner(), helper.getLevel(), helper.absolutePos(right));
        helper.assertFalse(second.success(), "the other half of a linked double chest was linked again");
        helper.assertTrue(s.shop().containers().size() == 2, "expected the single chest and the double chest, got " + s.shop().containers().size());
        TestZoos.cleanUp(helper, s.owner(), s.zoo());
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void badLinksAreRefused(GameTestHelper helper) {
        Setup s = setUp(helper, "Bad Links");
        ServerPlayer owner = s.owner();
        // Too far from any counter.
        placeChest(helper, new BlockPos(10, 2, 20), Items.APPLE, 1);
        helper.assertFalse(s.zoos().linkContainer(owner, helper.getLevel(), helper.absolutePos(new BlockPos(10, 2, 20))).success(), "far chest linked");
        // Outside the zoo, even though a counter is close.
        assertOk(helper, s.zoos().createShop(owner, helper.getLevel(), helper.absolutePos(new BlockPos(28, 2, 20))));
        placeChest(helper, new BlockPos(30, 2, 20), Items.APPLE, 1);
        helper.assertFalse(s.zoos().linkContainer(owner, helper.getLevel(), helper.absolutePos(new BlockPos(30, 2, 20))).success(), "outside chest linked");
        // Already stock for another shop.
        assertOk(helper, s.zoos().createShop(owner, helper.getLevel(), helper.absolutePos(new BlockPos(12, 2, 10))));
        ActionResult again = s.zoos().linkContainer(owner, helper.getLevel(), helper.absolutePos(CHEST));
        helper.assertFalse(again.success(), "chest linked to a second shop");
        helper.assertTrue(again.message().getString().contains(s.shop().name()), "message should name the shop: " + again.message().getString());
        // Not a container at all.
        helper.assertFalse(s.zoos().linkContainer(owner, helper.getLevel(), helper.absolutePos(new BlockPos(10, 1, 12))).success(), "grass linked");
        TestZoos.cleanUp(helper, owner, s.zoo());
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void pricesAreCheckedAndNeedPermission(GameTestHelper helper) {
        Setup s = setUp(helper, "Prices");
        ServerPlayer manager = TestZoos.player(helper, "Manager");
        ServerPlayer stranger = TestZoos.player(helper, "Stranger");
        s.zoos().addManager(s.owner(), s.zoo(), manager.getGameProfile());
        UUID shop = s.shop().id();
        helper.assertFalse(s.zoos().setItemPrice(s.owner(), s.zoo(), shop, BREAD, 0).success(), "price 0 accepted");
        helper.assertFalse(s.zoos().setItemPrice(s.owner(), s.zoo(), shop, BREAD, 1000).success(), "price 1000 accepted");
        helper.assertFalse(s.zoos().setItemPrice(s.owner(), s.zoo(), shop, "minecraft:command_block", 5).success(), "unsellable item priced");
        helper.assertFalse(s.zoos().setItemPrice(stranger, s.zoo(), shop, BREAD, 5).success(), "stranger set a price");
        assertOk(helper, s.zoos().setItemPrice(manager, s.zoo(), shop, BREAD, 5));
        helper.assertTrue(s.shop().price(BREAD).orElse(0) == 5, "price not stored");
        helper.assertTrue(sell(helper, s, "sale:price", 10) == SaleResult.SOLD && s.zoo().ledger().balance() == 5, "sale did not use the set price");
        TestZoos.cleanUp(helper, s.owner(), s.zoo());
        helper.succeed();
    }
}
