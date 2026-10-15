package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import com.zoocraft.animal.RecognitionReport;
import com.zoocraft.config.ZooConfig;
import com.zoocraft.core.perf.PerfSlot;
import com.zoocraft.core.perf.TimerSlots;
import com.zoocraft.core.visitor.VisitorProfile;
import com.zoocraft.perf.PerfLog;
import com.zoocraft.perf.PerfReport;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Cow;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 300)
    public static void perfReportDescribesTheZoo(GameTestHelper helper) {
        Setup setup = openZoo(helper, "Reported");
        Visitor visitor = exploringVisitor(helper, setup, 30, 24);
        helper.runAfterDelay(60, () -> {
            String server = String.join("\n", PerfReport.server(helper.getLevel().getServer()).stream().map(Component::getString).toList());
            helper.assertTrue(server.contains("Server tick"), "no server tick line:\n" + server);
            helper.assertTrue(server.contains("Reported"), "zoo missing from the server report:\n" + server);
            String zoo = String.join("\n", PerfReport.zoo(helper.getLevel().getServer(), setup.zoo()).stream().map(Component::getString).toList());
            helper.assertTrue(zoo.contains("Stuck visitors: 0"), "stuck line missing:\n" + zoo);
            helper.assertTrue(zoo.contains("Exhibits"), "coverage line missing:\n" + zoo);
            visitor.discard();
            TestZoos.cleanUp(helper, setup.owner(), setup.zoo());
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR, timeoutTicks = 200)
    public static void perfLogWritesALinePerSecondAndRestartsCleanly(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path first;
        try {
            PerfLog.start(server, 10);
            first = PerfLog.start(server, 3); // starting again replaces the first log
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        helper.runAfterDelay(90, () -> {
            helper.assertFalse(PerfLog.running(), "log still running after its time");
            List<String> lines;
            try {
                lines = Files.readAllLines(first);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            helper.assertTrue(lines.getFirst().startsWith("seconds,server_tick_ms"), "bad header: " + lines.getFirst());
            helper.assertTrue(lines.size() >= 3 && lines.size() <= 5, "expected about 3 data lines, got " + (lines.size() - 1));
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void stuckAndCrowdingCountsComeFromVisitors(GameTestHelper helper) {
        helper.assertTrue(PerfReport.stuckVisitors(List.of()) == 0, "stuck in an empty list");
        helper.assertTrue(PerfReport.mostOnOneTarget(List.of()) == 0, "crowding in an empty list");
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 400)
    public static void changingTheStepKeepsNeedsRisingAtTheSameRate(GameTestHelper helper) {
        Setup setup = openZoo(helper, "Stepped");
        Visitor visitor = exploringVisitor(helper, setup, 30, 24);
        visitor.testSetNeeds(0, 0);
        int original = ZooConfig.VISITOR_STEP_TICKS.get();
        helper.runAfterDelay(100, () -> {
            double before = visitor.needs().hunger();
            ZooConfig.VISITOR_STEP_TICKS.set(3);
            helper.runAfterDelay(100, () -> {
                double after = visitor.needs().hunger();
                ZooConfig.VISITOR_STEP_TICKS.set(original);
                // 5 seconds at either step: the rise must be about the same (needs rise 0.14 to 0.25 per second).
                double first = before;
                double second = after - before;
                helper.assertTrue(first > 0.4 && second > 0.4, "needs didn't rise: " + first + ", " + second);
                helper.assertTrue(Math.abs(first - second) < 0.35, "rate changed with the step: " + first + " then " + second);
                visitor.discard();
                TestZoos.cleanUp(helper, setup.owner(), setup.zoo());
                helper.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 200)
    public static void realPathPlansAreCountedAndShortOnesFlagged(GameTestHelper helper) {
        Visitor visitor = ZooEntities.VISITOR.get().create(helper.getLevel());
        BlockPos from = helper.absolutePos(new BlockPos(2, 2, 2));
        visitor.moveTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, 0, 0);
        helper.getLevel().addFreshEntity(visitor);
        helper.runAfterDelay(2, () -> {
            long plans = SimulationTimers.global().calls(PerfSlot.VISITOR_PATHFINDING.ordinal(), 1000);
            long shortOnes = SimulationTimers.global().calls(PerfSlot.VISITOR_PATH_SHORT.ordinal(), 1000);
            PathNavigation nav = visitor.getNavigation();
            BlockPos target = helper.absolutePos(new BlockPos(20, 2, 2));
            net.minecraft.world.level.pathfinder.Path first = nav.createPath(target, 0);
            nav.moveTo(first, 0.6);
            nav.createPath(target, 0); // same target, path not done: reused, not a plan
            helper.runAfterDelay(1, () -> {
                long newPlans = SimulationTimers.global().calls(PerfSlot.VISITOR_PATHFINDING.ordinal(), 1000) - plans;
                long newShort = SimulationTimers.global().calls(PerfSlot.VISITOR_PATH_SHORT.ordinal(), 1000) - shortOnes;
                helper.assertTrue(first != null && first.canReach(), "the 18-block path should reach");
                helper.assertTrue(newPlans >= 1, "the plan wasn't counted");
                helper.assertTrue(newShort == 0 || newPlans > newShort, "a reachable plan was counted as short");
                visitor.discard();
                helper.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void recognitionReportNamesTheRule(GameTestHelper helper) {
        List<RecognitionReport.Entry> entries = RecognitionReport.run(helper.getLevel(), "minecraft");
        RecognitionReport.Entry cow = find(entries, "minecraft:cow");
        helper.assertTrue(cow.recognised() && cow.checked() && cow.reason().equals("extends Animal"), "cow: " + cow);
        RecognitionReport.Entry zombie = find(entries, "minecraft:zombie");
        helper.assertTrue(!zombie.recognised() && zombie.checked(), "zombie: " + zombie);
        RecognitionReport.Entry villager = find(entries, "minecraft:villager");
        helper.assertTrue(!villager.recognised() && villager.checked(), "villager: " + villager);
        helper.assertTrue(entries.stream().allMatch(e -> e.id().getNamespace().equals("minecraft")), "other namespaces included");
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void recognitionReportSurvivesBrokenEntities(GameTestHelper helper) {
        List<RecognitionReport.Entry> entries = RecognitionReport.run(helper.getLevel(), "minecraft", type -> {
            if (type == EntityType.COW) {
                throw new IllegalStateException("broken mod entity");
            }
            if (type == EntityType.PIG) {
                return null;
            }
            return type.create(helper.getLevel());
        });
        RecognitionReport.Entry cow = find(entries, "minecraft:cow");
        helper.assertTrue(!cow.checked() && cow.recognised() && cow.reason().equals("animal spawn category"),
                "a cow that can't be built still gets its category verdict: " + cow);
        RecognitionReport.Entry pig = find(entries, "minecraft:pig");
        helper.assertTrue(!pig.checked(), "a pig that came back null is unchecked: " + pig);
        RecognitionReport.Entry sheep = find(entries, "minecraft:sheep");
        helper.assertTrue(sheep.checked() && sheep.recognised(), "the report carried on after the failures: " + sheep);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void recognitionReportSurvivesEntitiesThatFailToDiscard(GameTestHelper helper) {
        List<RecognitionReport.Entry> entries = RecognitionReport.run(helper.getLevel(), "minecraft", type -> {
            if (type == EntityType.COW) {
                return new Cow(EntityType.COW, helper.getLevel()) {
                    @Override
                    public void remove(RemovalReason reason) {
                        throw new IllegalStateException("multipart teardown in a mod");
                    }
                };
            }
            return type.create(helper.getLevel());
        });
        RecognitionReport.Entry cow = find(entries, "minecraft:cow");
        helper.assertTrue(cow.checked() && cow.recognised(), "the cow's verdict was kept: " + cow);
        RecognitionReport.Entry sheep = find(entries, "minecraft:sheep");
        helper.assertTrue(sheep.checked() && sheep.recognised(), "the report carried on: " + sheep);
        helper.succeed();
    }

    private static RecognitionReport.Entry find(List<RecognitionReport.Entry> entries, String id) {
        return entries.stream().filter(e -> e.id().toString().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException(id + " missing from the report"));
    }
}
