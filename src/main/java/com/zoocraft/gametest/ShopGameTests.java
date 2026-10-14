package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
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
