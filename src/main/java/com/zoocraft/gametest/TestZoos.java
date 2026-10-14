package com.zoocraft.gametest;

import com.mojang.authlib.GameProfile;
import com.zoocraft.zoo.ActionResult;
import com.zoocraft.zoo.BoundaryVertex;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.List;
import java.util.UUID;

/** Helpers for GameTests that need zoos and players. */
public final class TestZoos {
    private TestZoos() {
    }

    public static ServerPlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    public static ZooManager manager(GameTestHelper helper) {
        return ZooManager.get(helper.getLevel().getServer());
    }

    /** A rectangle from relative (x1, z1) to (x2, z2) standing height (floor is at relative y=1). */
    public static List<BoundaryVertex> rectangle(GameTestHelper helper, int x1, int z1, int x2, int z2) {
        BlockPos a = helper.absolutePos(new BlockPos(x1, 2, z1));
        BlockPos b = helper.absolutePos(new BlockPos(x2, 2, z2));
        int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
        int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());
        return List.of(new BoundaryVertex(minX, a.getY(), minZ), new BoundaryVertex(maxX, a.getY(), minZ),
                new BoundaryVertex(maxX, a.getY(), maxZ), new BoundaryVertex(minX, a.getY(), maxZ));
    }

    /** Creates a zoo owned by {@code owner} or fails the test. Names get a random suffix so tests never clash. */
    public static Zoo create(GameTestHelper helper, ServerPlayer owner, String name, List<BoundaryVertex> boundary) {
        String unique = name + " " + UUID.randomUUID().toString().substring(0, 6);
        ActionResult result = manager(helper).createZoo(owner, unique, helper.getLevel().dimension(), boundary);
        helper.assertTrue(result.success(), "zoo creation failed: " + result.message().getString());
        return manager(helper).byName(unique).orElseThrow();
    }

    /** Empties the treasury and deletes the zoo so later tests start clean. */
    public static void cleanUp(GameTestHelper helper, ServerPlayer owner, Zoo zoo) {
        ZooManager manager = manager(helper);
        if (manager.byId(zoo.id()).isEmpty()) {
            return;
        }
        long balance = zoo.ledger().balance();
        if (balance > 0) {
            manager.withdraw(owner, zoo, (int) balance);
        }
        manager.delete(owner, zoo);
    }
}
