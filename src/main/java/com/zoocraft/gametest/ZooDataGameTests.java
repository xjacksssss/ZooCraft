package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ZooCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZooDataGameTests {
    @GameTest(template = GameTestTemplates.FLOOR)
    public static void zooSurvivesCodecRoundTrip(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        ServerPlayer manager = TestZoos.player(helper, "Helper");
        ZooManager zoos = TestZoos.manager(helper);
        Zoo zoo = TestZoos.create(helper, owner, "Round Trip", TestZoos.rectangle(helper, 2, 2, 29, 29));
        zoos.addManager(owner, zoo, manager.getGameProfile());
        zoos.addGate(owner, helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 10)));
        zoos.addGate(owner, helper.getLevel(), helper.absolutePos(new BlockPos(29, 1, 20)));
        zoos.setPrice(owner, zoo, 7);
        owner.getInventory().add(new ItemStack(Items.EMERALD, 30));
        zoos.deposit(owner, zoo, 30);
        zoos.withdraw(owner, zoo, 4);
        zoos.chargeAdmission(zoo, java.util.UUID.randomUUID(), 7);

        Tag encoded = Zoo.CODEC.encodeStart(NbtOps.INSTANCE, zoo).getOrThrow();
        Zoo decoded = Zoo.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        Tag reEncoded = Zoo.CODEC.encodeStart(NbtOps.INSTANCE, decoded).getOrThrow();

        helper.assertTrue(encoded.equals(reEncoded), "zoo changed after a save and load");
        helper.assertTrue(decoded.gates().size() == 2, "gates lost");
        helper.assertTrue(decoded.managers().contains(manager.getUUID()), "manager lost");
        helper.assertTrue(decoded.ledger().balance() == 33, "balance wrong: " + decoded.ledger().balance());
        helper.assertTrue(decoded.ledger().recent().size() == 3, "ledger history lost");
        helper.assertTrue(decoded.stats().totalAttendance() == 1, "attendance lost");
        helper.assertTrue(decoded.admissionPrice() == 7, "price lost");

        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }
}
