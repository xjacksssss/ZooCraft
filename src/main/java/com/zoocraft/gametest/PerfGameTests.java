package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import com.zoocraft.core.perf.PerfSlot;
import com.zoocraft.core.perf.TimerSlots;
import com.zoocraft.core.visitor.VisitorProfile;
import com.zoocraft.perf.SimulationTimers;
import com.zoocraft.registry.ZooEntities;
import com.zoocraft.visitor.Visitor;
import com.zoocraft.visitor.VisitorPhase;
import com.zoocraft.zoo.Gate;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.UUID;

/** M9: timers, the perf report and log, and the recognition report. */
@GameTestHolder(ZooCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PerfGameTests {
    private static final BlockPos GATE = new BlockPos(16, 2, 24);

    record Setup(ServerPlayer owner, Zoo zoo, Gate gate) {
    }

    static Setup openZoo(GameTestHelper helper, String name) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, name, TestZoos.rectangle(helper, 16, 4, 44, 44));
        ZooManager zoos = TestZoos.manager(helper);
        helper.assertTrue(zoos.addGate(owner, helper.getLevel(), helper.absolutePos(GATE)).success(), "gate not added");
        zoos.setPrice(owner, zoo, 0);
        zoos.setOpen(owner, zoo, true);
        return new Setup(owner, zoo, zoo.gates().getFirst());
    }

    static Visitor exploringVisitor(GameTestHelper helper, Setup setup, int x, int z) {
        Visitor visitor = ZooEntities.VISITOR.get().create(helper.getLevel());
        BlockPos at = helper.absolutePos(new BlockPos(x, 2, z));
        visitor.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        visitor.begin(setup.zoo(), setup.gate(), UUID.randomUUID(), true);
        visitor.testSetBudget(VisitorProfile.GENERAL, 10, 1.0);
        visitor.testSetPhase(VisitorPhase.EXPLORING);
        helper.getLevel().addFreshEntity(visitor);
        return visitor;
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 400)
    public static void visitorTimeIsRecordedPerZoo(GameTestHelper helper) {
        Setup setup = openZoo(helper, "Timed");
        Visitor visitor = exploringVisitor(helper, setup, 30, 24);
        helper.runAfterDelay(100, () -> {
            TimerSlots zoo = SimulationTimers.forZoo(setup.zoo().id()).orElse(null);
            helper.assertTrue(zoo != null, "no timers for the zoo");
            for (PerfSlot slot : new PerfSlot[]{PerfSlot.VISITOR_CONTROLLER, PerfSlot.VISITOR_BRAIN, PerfSlot.VISITOR_NAVIGATION}) {
                helper.assertTrue(zoo.calls(slot.ordinal()) > 0, slot + " has no calls");
                helper.assertTrue(zoo.calls(slot.ordinal()) <= SimulationTimers.global().calls(slot.ordinal()),
                        slot + ": the zoo has more calls than the whole server");
            }
            helper.assertTrue(SimulationTimers.global().calls(PerfSlot.SERVER_TICK.ordinal()) > 0, "server tick not timed");
            helper.assertTrue(SimulationTimers.global().depth() == 0, "timer nesting unbalanced: " + SimulationTimers.global().depth());
            visitor.discard();
            TestZoos.cleanUp(helper, setup.owner(), setup.zoo());
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void deletedZoosLoseTheirTimers(GameTestHelper helper) {
        UUID gone = UUID.randomUUID();
        long start = SimulationTimers.start();
        SimulationTimers.stop(PerfSlot.ANIMAL_CENSUS, gone, start);
        helper.assertTrue(SimulationTimers.forZoo(gone).isPresent(), "timers not created for the zoo");
        // Prune against the real zoos: other tests run in parallel and keep theirs.
        Set<UUID> live = new java.util.HashSet<>();
        TestZoos.manager(helper).all().forEach(z -> live.add(z.id()));
        SimulationTimers.prune(live);
        helper.assertTrue(SimulationTimers.forZoo(gone).isEmpty(), "timers kept for a deleted zoo");
        // Late stops for a pruned zoo are fine.
        long again = SimulationTimers.start();
        SimulationTimers.stop(PerfSlot.ANIMAL_CENSUS, gone, again);
        helper.succeed();
    }
}
