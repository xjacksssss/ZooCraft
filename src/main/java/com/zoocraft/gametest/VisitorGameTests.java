package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import com.zoocraft.core.visitor.VisitorProfile;
import com.zoocraft.registry.ZooEntities;
import com.zoocraft.visitor.Admission;
import com.zoocraft.visitor.Visitor;
import com.zoocraft.visitor.VisitorPhase;
import com.zoocraft.visitor.VisitorSpawner;
import com.zoocraft.zoo.Gate;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Visitor behaviour in a real world. Most tests use the large floor with a zoo covering x 16..44 and a gate on its
 * west edge at x=16, so visitors leaving westwards stay on the floor.
 */
@GameTestHolder(ZooCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VisitorGameTests {
    private static final BlockPos GATE = new BlockPos(16, 2, 24);

    private record Setup(ServerPlayer owner, Zoo zoo, Gate gate) {
    }

    private static Setup openZoo(GameTestHelper helper, int price) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Visitors", TestZoos.rectangle(helper, 16, 4, 44, 44));
        ZooManager zoos = TestZoos.manager(helper);
        helper.assertTrue(zoos.addGate(owner, helper.getLevel(), helper.absolutePos(GATE)).success(), "gate not added");
        zoos.setPrice(owner, zoo, price);
        zoos.setOpen(owner, zoo, true);
        return new Setup(owner, zoo, zoo.gates().getFirst());
    }

    private static Visitor visitor(GameTestHelper helper, Setup setup, UUID group, boolean leader, int x, int z, int budget) {
        Visitor visitor = ZooEntities.VISITOR.get().create(helper.getLevel());
        BlockPos at = helper.absolutePos(new BlockPos(x, 2, z));
        visitor.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        visitor.begin(setup.zoo(), setup.gate(), group, leader);
        visitor.testSetBudget(VisitorProfile.GENERAL, budget, 1.0);
        helper.getLevel().addFreshEntity(visitor);
        return visitor;
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE)
    public static void admissionIsChargedOnceEvenAfterSaveAndLoad(GameTestHelper helper) {
        Setup setup = openZoo(helper, 3);
        ZooManager zoos = TestZoos.manager(helper);
        Visitor visitor = visitor(helper, setup, UUID.randomUUID(), true, 10, 24, 10);

        helper.assertTrue(Admission.tryAdmitGroup(zoos, setup.zoo(), List.of(visitor)), "admission refused");
        helper.assertTrue(Admission.tryAdmitGroup(zoos, setup.zoo(), List.of(visitor)), "second admission refused");
        helper.assertTrue(setup.zoo().ledger().balance() == 3, "charged more than once: " + setup.zoo().ledger().balance());
        helper.assertTrue(visitor.budget() == 7, "budget wrong: " + visitor.budget());

        // A saved and reloaded copy of the same visit must not pay again either.
        CompoundTag saved = new CompoundTag();
        visitor.saveWithoutId(saved);
        Visitor copy = ZooEntities.VISITOR.get().create(helper.getLevel());
        copy.load(saved);
        helper.assertTrue(copy.admitted(), "admitted flag lost on save");
        helper.assertTrue(Admission.tryAdmitGroup(zoos, setup.zoo(), List.of(copy)), "reloaded admission refused");
        helper.assertTrue(setup.zoo().ledger().balance() == 3, "reloaded visitor paid again");
        helper.assertTrue(setup.zoo().stats().totalAttendance() == 1, "attendance counted twice");

        visitor.discard();
        TestZoos.cleanUp(helper, setup.owner(), setup.zoo());
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE)
    public static void groupRefusesTogetherWithoutPaying(GameTestHelper helper) {
        Setup setup = openZoo(helper, 3);
        UUID group = UUID.randomUUID();
        Visitor rich = visitor(helper, setup, group, true, 10, 24, 12);
        Visitor poor = visitor(helper, setup, group, false, 10, 25, 1);

        helper.assertFalse(Admission.tryAdmitGroup(TestZoos.manager(helper), setup.zoo(), List.of(rich, poor)), "group admitted");
        helper.assertTrue(setup.zoo().ledger().balance() == 0, "money moved");
        helper.assertTrue(rich.budget() == 12 && poor.budget() == 1, "budgets changed");
        helper.assertFalse(rich.admitted() || poor.admitted(), "someone was admitted");
        helper.assertTrue(setup.zoo().stats().refusedAdmissions() == 2, "refusal not recorded");

        rich.discard();
        poor.discard();
        TestZoos.cleanUp(helper, setup.owner(), setup.zoo());
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1600)
    public static void closingTheZooMakesVisitorsLeave(GameTestHelper helper) {
        Setup setup = openZoo(helper, 0);
        Visitor visitor = visitor(helper, setup, UUID.randomUUID(), true, 30, 24, 10);
        visitor.testSetPhase(VisitorPhase.EXPLORING);
        helper.runAfterDelay(40, () -> TestZoos.manager(helper).setOpen(setup.owner(), setup.zoo(), false));
        helper.succeedWhen(() -> {
            helper.assertTrue(visitor.isRemoved(), "visitor still here, phase " + visitor.phase());
            TestZoos.cleanUp(helper, setup.owner(), setup.zoo());
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1800)
    public static void walledInVisitorIsReleasedWithComplaint(GameTestHelper helper) {
        Setup setup = openZoo(helper, 0);
        for (int x = 27; x <= 33; x++) {
            for (int z = 21; z <= 27; z++) {
                if (x == 27 || x == 33 || z == 21 || z == 27) {
                    for (int y = 2; y <= 4; y++) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    }
                }
            }
        }
        Visitor visitor = visitor(helper, setup, UUID.randomUUID(), true, 30, 24, 10);
        visitor.testSetPhase(VisitorPhase.EXPLORING);
        helper.runAfterDelay(20, () -> TestZoos.manager(helper).setOpen(setup.owner(), setup.zoo(), false));
        helper.succeedWhen(() -> {
            helper.assertTrue(visitor.isRemoved(), "visitor still trapped, phase " + visitor.phase());
            helper.assertTrue(setup.zoo().stats().commentTally().getOrDefault("could_not_leave", 0) >= 1, "complaint not recorded");
            TestZoos.cleanUp(helper, setup.owner(), setup.zoo());
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE)
    public static void boundaryEditReleasesVisitorsLeftOutside(GameTestHelper helper) {
        Setup setup = openZoo(helper, 0);
        Visitor visitor = visitor(helper, setup, UUID.randomUUID(), true, 40, 40, 10);
        visitor.testSetPhase(VisitorPhase.EXPLORING);
        helper.assertTrue(TestZoos.manager(helper).editBoundary(setup.owner(), setup.zoo(),
                TestZoos.rectangle(helper, 16, 4, 30, 30)).success(), "edit failed");
        helper.assertTrue(visitor.phase() == VisitorPhase.EXITING, "visitor not released, phase " + visitor.phase());
        visitor.discard();
        TestZoos.cleanUp(helper, setup.owner(), setup.zoo());
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 2400)
    public static void spawnedVisitorsArrivePayAndExplore(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Arrivals", TestZoos.rectangle(helper, 34, 2, 46, 46));
        ZooManager zoos = TestZoos.manager(helper);
        helper.assertTrue(zoos.addGate(owner, helper.getLevel(), helper.absolutePos(new BlockPos(34, 2, 24))).success(), "gate not added");
        zoos.setPrice(owner, zoo, 1);
        zoos.setOpen(owner, zoo, true);
        List<Visitor> group = List.of();
        for (int i = 0; i < 10 && group.isEmpty(); i++) {
            group = VisitorSpawner.trySpawnGroup(helper.getLevel(), zoo);
        }
        helper.assertFalse(group.isEmpty(), "no visitors could be spawned");
        List<Visitor> spawned = group;
        helper.succeedWhen(() -> {
            helper.assertTrue(zoo.stats().totalAttendance() >= spawned.size(), "attendance " + zoo.stats().totalAttendance()
                    + ", phases " + spawned.stream().map(Visitor::phase).toList());
            helper.assertTrue(zoo.ledger().balance() == zoo.stats().totalAttendance(), "balance does not match attendance");
            helper.assertTrue(spawned.stream().allMatch(v -> v.phase() == VisitorPhase.EXPLORING), "not all exploring");
            spawned.forEach(Visitor::discard);
            TestZoos.cleanUp(helper, owner, zoo);
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1200)
    public static void aGroupOfFourQueuesOnSeparateSlots(GameTestHelper helper) {
        Setup setup = openZoo(helper, 0);
        UUID group = UUID.randomUUID();
        Visitor[] members = new Visitor[4];
        for (int i = 0; i < 4; i++) {
            members[i] = visitor(helper, setup, group, i == 0, 4, 20 + i * 2, 10);
        }
        java.util.Map<Visitor, BlockPos> slots = new java.util.HashMap<>();
        helper.onEachTick(() -> {
            for (Visitor v : members) {
                BlockPos slot = v.controller().queueSlot();
                if (slot != null) {
                    slots.put(v, slot);
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(java.util.Arrays.stream(members).allMatch(v -> v.phase() == VisitorPhase.EXPLORING), "not all admitted yet");
            helper.assertTrue(slots.size() == 4 && new java.util.HashSet<>(slots.values()).size() == 4, "slots shared: " + slots.values());
            helper.assertTrue(java.util.Arrays.stream(members).allMatch(v -> v.controller().queueSlot() == null), "slots kept after entering");
            for (Visitor v : members) {
                v.discard();
            }
            TestZoos.cleanUp(helper, setup.owner(), setup.zoo());
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 2400)
    public static void closingSendsVisitorsToBothGates(GameTestHelper helper) {
        // Two gates on the west edge, 20 blocks apart; all 30 visitors start near the northern one.
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Exits", TestZoos.rectangle(helper, 16, 4, 44, 44));
        ZooManager zoos = TestZoos.manager(helper);
        TestShops.assertOk(helper, zoos.addGate(owner, helper.getLevel(), helper.absolutePos(new BlockPos(16, 2, 12))));
        TestShops.assertOk(helper, zoos.addGate(owner, helper.getLevel(), helper.absolutePos(new BlockPos(16, 2, 32))));
        zoos.setPrice(owner, zoo, 0);
        zoos.setOpen(owner, zoo, true);
        Setup setup = new Setup(owner, zoo, zoo.gates().getFirst());
        List<Visitor> visitors = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            Visitor v = visitor(helper, setup, UUID.randomUUID(), true, 20 + i % 6, 8 + i / 6, 10);
            v.testSetPhase(VisitorPhase.EXPLORING);
            visitors.add(v);
        }
        java.util.Set<UUID> gatesUsed = new java.util.HashSet<>();
        helper.runAfterDelay(20, () -> zoos.setOpen(owner, zoo, false));
        helper.onEachTick(() -> visitors.forEach(v -> {
            if (v.controller().exitGateId() != null) {
                gatesUsed.add(v.controller().exitGateId());
            }
        }));
        helper.succeedWhen(() -> {
            helper.assertTrue(visitors.stream().allMatch(Visitor::isRemoved), "visitors still inside");
            helper.assertTrue(gatesUsed.size() == 2, "only one gate used");
            TestZoos.cleanUp(helper, owner, zoo);
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1600)
    public static void lingeringVisitorsLookAtAnimalsAndOnlyGlanceAtPlayers(GameTestHelper helper) {
        Setup setup = openZoo(helper, 0);
        helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG, new BlockPos(30, 2, 30));
        helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG, new BlockPos(31, 2, 31));
        com.zoocraft.animal.AnimalCensus.refresh(setup.zoo(), helper.getLevel());
        Visitor visitor = visitor(helper, setup, UUID.randomUUID(), true, 28, 28, 10);
        visitor.testSetPhase(VisitorPhase.EXPLORING);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos p = helper.absolutePos(new BlockPos(28, 2, 25));
        player.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        int[] ticks = new int[3]; // measured, at the player, at an animal
        helper.onEachTick(() -> {
            if (helper.getTick() < 100 || ticks[0] >= 1200) {
                return;
            }
            ticks[0]++;
            var look = net.tslat.smartbrainlib.util.BrainUtils.getMemory(visitor,
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);
            if (look instanceof net.minecraft.world.entity.ai.behavior.EntityTracker tracker) {
                if (tracker.getEntity() == player) {
                    ticks[1]++;
                } else if (tracker.getEntity() instanceof net.minecraft.world.entity.animal.Pig) {
                    ticks[2]++;
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ticks[0] >= 1200, "still measuring");
            helper.assertTrue(ticks[1] < 0.15 * ticks[0], "looked at the player " + ticks[1] + " of " + ticks[0] + " ticks");
            helper.assertTrue(ticks[2] > 0.5 * ticks[0], "looked at the animals only " + ticks[2] + " of " + ticks[0] + " ticks");
            visitor.discard();
            player.discard();
            TestZoos.cleanUp(helper, setup.owner(), setup.zoo());
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE)
    public static void visitorsAdmittedFromAnyQueueSlotMayWalkIn(GameTestHelper helper) {
        // Admitted visitors keep to the zoo except around gates; the gate area must cover the whole queue.
        Setup setup = openZoo(helper, 0);
        com.zoocraft.visitor.PathRules exploring = com.zoocraft.visitor.VisitorController.rulesFor(VisitorPhase.EXPLORING, setup.zoo());
        List<BlockPos> slots = com.zoocraft.visitor.GateQueues.slots(helper.getLevel(), setup.zoo(), setup.gate());
        helper.assertTrue(slots.size() >= 4, "only " + slots.size() + " queue slots");
        for (BlockPos slot : slots) {
            helper.assertTrue(exploring.allows(slot.getX(), slot.getZ()), "an admitted visitor can't walk from the queue slot at "
                    + helper.relativePos(slot));
        }
        TestZoos.cleanUp(helper, setup.owner(), setup.zoo());
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 400)
    public static void aVisitorAdmittedFromBeyondTheQueueWalksIn(GameTestHelper helper) {
        // Visitors who waited beyond a full queue are admitted from about 7 blocks out; they must walk in, not leave.
        Setup setup = openZoo(helper, 0);
        Visitor visitor = visitor(helper, setup, UUID.randomUUID(), true, 9, 24, 10);
        visitor.testSetPhase(VisitorPhase.EXPLORING);
        helper.succeedWhen(() -> {
            helper.assertTrue(visitor.phase() == VisitorPhase.EXPLORING, "sent away after paying: " + visitor.phase());
            helper.assertTrue(setup.zoo().contains(visitor.blockPosition()), "not inside yet");
            visitor.discard();
            TestZoos.cleanUp(helper, setup.owner(), setup.zoo());
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 100)
    public static void aVisitorUnloadedWithItsChunkFreesItsPlaces(GameTestHelper helper) {
        Setup setup = openZoo(helper, 0);
        Visitor visitor = visitor(helper, setup, UUID.randomUUID(), true, 30, 24, 10);
        UUID facility = UUID.randomUUID();
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(com.zoocraft.shop.FacilityOccupancy.tryOccupy(facility, visitor.getUUID(), helper.getLevel().getGameTime(), 1),
                    "claim refused");
            visitor.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        });
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(com.zoocraft.shop.FacilityOccupancy.count(facility, helper.getLevel().getGameTime()) == 0,
                    "place still held after the visitor was unloaded");
            TestZoos.cleanUp(helper, setup.owner(), setup.zoo());
            helper.succeed();
        });
    }
}
