package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import com.zoocraft.core.shop.ProductCategory;
import com.zoocraft.shop.FacilityCensus;
import com.zoocraft.shop.FacilitySnapshot;
import com.zoocraft.shop.StockProtection;
import com.zoocraft.zoo.ZooAlerts;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import com.zoocraft.zoo.Shop;
import com.zoocraft.zoo.ShopStats;
import com.zoocraft.zoo.Toilet;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooFacilities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shops, stock and sales (Phase 2 spec section 7). */
@GameTestHolder(ZooCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShopGameTests {
    @GameTest(template = GameTestTemplates.FLOOR)
    public static void facilitiesSurviveCodecRoundTrip(GameTestHelper helper) {
        ShopStats stats = new ShopStats(40, 20, 3, 6, 3, Map.of("minecraft:bread", 3), Map.of("out_of_stock|minecraft:apple", 2));
        Shop shop = new Shop(UUID.randomUUID(), "Riverside Café", new BlockPos(1, 64, 2),
                List.of(new BlockPos(2, 64, 2), new BlockPos(3, 64, 2)), true,
                Map.of("minecraft:bread", 2, "minecraft:poppy", 5), stats);
        ZooFacilities facilities = new ZooFacilities(List.of(shop), List.of(new Toilet(UUID.randomUUID(), new BlockPos(9, 64, 9))));

        Tag encoded = ZooFacilities.CODEC.encodeStart(NbtOps.INSTANCE, facilities).getOrThrow();
        ZooFacilities decoded = ZooFacilities.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        Tag reEncoded = ZooFacilities.CODEC.encodeStart(NbtOps.INSTANCE, decoded).getOrThrow();

        helper.assertTrue(encoded.equals(reEncoded), "facilities changed after a save and load");
        Shop back = decoded.shops().getFirst();
        helper.assertTrue(back.name().equals("Riverside Café"), "name lost");
        helper.assertTrue(back.containers().size() == 2, "containers lost");
        helper.assertTrue(back.price("minecraft:poppy").orElse(0) == 5, "price lost");
        helper.assertTrue(back.stats().revenueTotal() == 40 && back.stats().soldToday().get("minecraft:bread") == 3, "stats lost");
        helper.assertTrue(decoded.toilets().size() == 1, "toilet lost");
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void censusUnlinksBrokenContainerAndRaisesAlert(GameTestHelper helper) {
        TestShops.Setup s = TestShops.setUp(helper, "Census Broken");
        helper.setBlock(TestShops.CHEST, Blocks.AIR);
        FacilityCensus.refresh(s.zoo(), helper.getLevel());
        helper.assertTrue(s.shop().containers().isEmpty(), "broken chest still linked");
        BlockPos at = helper.absolutePos(TestShops.CHEST);
        String expected = Component.translatable("zoocraft.alert.container_removed", at.getX(), at.getY(), at.getZ()).getString();
        boolean found = ZooAlerts.messages(s.zoo(), helper.getLevel().getGameTime()).stream().anyMatch(c -> c.getString().equals(expected));
        helper.assertTrue(found, "missing alert: " + expected);
        TestZoos.cleanUp(helper, s.owner(), s.zoo());
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void censusSnapshotListsStockAndCheapestPrices(GameTestHelper helper) {
        TestShops.Setup s = TestShops.setUp(helper, "Census Stock");
        FacilitySnapshot snapshot = FacilityCensus.refresh(s.zoo(), helper.getLevel());
        FacilitySnapshot.ShopState state = snapshot.shop(s.shop().id()).orElseThrow();
        helper.assertTrue(state.usable(), "open shop inside the zoo should be usable");
        FacilitySnapshot.Product bread = state.products().stream().filter(p -> p.itemId().equals(TestShops.BREAD)).findFirst().orElseThrow();
        helper.assertTrue(bread.count() == 5 && bread.price() == 2 && bread.category() == ProductCategory.FOOD, "bad bread line: " + bread);
        helper.assertTrue(state.cheapest(ProductCategory.FOOD).orElse(-1) == 2, "cheapest food should be 2");
        helper.assertTrue(state.cheapest(ProductCategory.SOUVENIR).isEmpty(), "no souvenirs are stocked");
        helper.assertTrue(FacilityCensus.cached(s.zoo()) == snapshot, "snapshot not cached");
        TestZoos.cleanUp(helper, s.owner(), s.zoo());
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void onlyOwnersManagersAndOperatorsMayTakeStock(GameTestHelper helper) {
        TestShops.Setup s = TestShops.setUp(helper, "Protection");
        ServerPlayer manager = TestZoos.player(helper, "Manager");
        ServerPlayer stranger = TestZoos.player(helper, "Stranger");
        s.zoos().addManager(s.owner(), s.zoo(), manager.getGameProfile());
        helper.assertTrue(StockProtection.mayAccess(s.owner(), s.zoo()), "owner refused");
        helper.assertTrue(StockProtection.mayAccess(manager, s.zoo()), "manager refused");
        helper.assertFalse(StockProtection.mayAccess(stranger, s.zoo()), "stranger allowed");

        BlockPos chest = helper.absolutePos(TestShops.CHEST);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(chest), Direction.UP, chest, false);
        PlayerInteractEvent.RightClickBlock strangerClick = new PlayerInteractEvent.RightClickBlock(stranger, InteractionHand.MAIN_HAND, chest, hit);
        StockProtection.onRightClick(strangerClick);
        helper.assertTrue(strangerClick.isCanceled(), "stranger could open the stock chest");
        PlayerInteractEvent.RightClickBlock managerClick = new PlayerInteractEvent.RightClickBlock(manager, InteractionHand.MAIN_HAND, chest, hit);
        StockProtection.onRightClick(managerClick);
        helper.assertFalse(managerClick.isCanceled(), "manager could not open the stock chest");

        BlockEvent.BreakEvent strangerBreak = new BlockEvent.BreakEvent(helper.getLevel(), chest, helper.getLevel().getBlockState(chest), stranger);
        StockProtection.onBreak(strangerBreak);
        helper.assertTrue(strangerBreak.isCanceled(), "stranger could break the stock chest");
        TestZoos.cleanUp(helper, s.owner(), s.zoo());
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void phaseOneZooLoadsWithNoFacilities(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Old Save", TestZoos.rectangle(helper, 2, 2, 29, 29));
        owner.getInventory().add(new ItemStack(Items.EMERALD, 12));
        TestZoos.manager(helper).deposit(owner, zoo, 12);

        CompoundTag tag = (CompoundTag) Zoo.CODEC.encodeStart(NbtOps.INSTANCE, zoo).getOrThrow();
        tag.remove("facilities");
        tag.getCompound("ledger").remove("totalShopSales");
        Zoo old = Zoo.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();

        helper.assertTrue(old.shops().isEmpty() && old.toilets().isEmpty(), "a Phase 1 zoo should have no facilities");
        helper.assertTrue(old.ledger().totalShopSales() == 0, "shop total should start at 0");
        helper.assertTrue(old.ledger().balance() == 12, "treasury lost: " + old.ledger().balance());
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }
}
