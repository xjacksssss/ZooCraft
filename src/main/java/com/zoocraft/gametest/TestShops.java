package com.zoocraft.gametest;

import com.zoocraft.zoo.ActionResult;
import com.zoocraft.zoo.Shop;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Helpers for GameTests that need a stocked shop. Things stand at relative y=2. */
public final class TestShops {
    public static final BlockPos COUNTER = new BlockPos(10, 2, 10);
    public static final BlockPos CHEST = new BlockPos(11, 2, 10);
    public static final String BREAD = "minecraft:bread";

    /** A zoo covering most of the floor, with a shop at COUNTER and a chest of 5 bread at CHEST linked to it. */
    public record Setup(ServerPlayer owner, ZooManager zoos, Zoo zoo, Shop shop) {
    }

    private TestShops() {
    }

    public static Setup setUp(GameTestHelper helper, String name) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ZooManager zoos = TestZoos.manager(helper);
        Zoo zoo = TestZoos.create(helper, owner, name, TestZoos.rectangle(helper, 2, 2, 29, 29));
        assertOk(helper, zoos.createShop(owner, helper.getLevel(), helper.absolutePos(COUNTER)));
        placeChest(helper, CHEST, Items.BREAD, 5);
        assertOk(helper, zoos.linkContainer(owner, helper.getLevel(), helper.absolutePos(CHEST)));
        return new Setup(owner, zoos, zoo, zoo.shops().getFirst());
    }

    public static void placeChest(GameTestHelper helper, BlockPos pos, Item item, int count) {
        helper.setBlock(pos, Blocks.CHEST);
        if (count > 0) {
            ((Container) helper.getBlockEntity(pos)).setItem(0, new ItemStack(item, count));
        }
    }

    public static int count(GameTestHelper helper, BlockPos pos, Item item) {
        Container container = (Container) helper.getBlockEntity(pos);
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).is(item)) {
                total += container.getItem(i).getCount();
            }
        }
        return total;
    }

    public static void assertOk(GameTestHelper helper, ActionResult result) {
        helper.assertTrue(result.success(), "expected success: " + result.message().getString());
    }
}
