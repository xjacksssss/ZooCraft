package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import com.zoocraft.animal.RecognitionReport;
import com.zoocraft.config.ZooConfig;
import com.zoocraft.core.perf.PerfSlot;
import com.zoocraft.core.perf.TimerSlots;
import com.zoocraft.core.visitor.VisitorProfile;
import com.zoocraft.perf.MissedTargets;
import com.zoocraft.perf.PerfLog;
import com.zoocraft.perf.PerfReport;
import com.zoocraft.perf.SimulationTimers;
import com.zoocraft.registry.ZooEntities;
import com.zoocraft.visitor.Visitor;
import com.zoocraft.visitor.ShortcutMode;
import com.zoocraft.visitor.VisitorNavigation;
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
        double[] before = new double[1];
        // Scheduled up front: adding a delayed task from inside one can break the GameTest scheduler's iteration.
        helper.runAfterDelay(100, () -> {
            before[0] = visitor.needs().hunger();
            ZooConfig.VISITOR_STEP_TICKS.set(3);
        });
        helper.runAfterDelay(200, () -> {
            double after = visitor.needs().hunger();
            ZooConfig.VISITOR_STEP_TICKS.set(original);
            // 5 seconds at either step: the rise must be about the same (needs rise 0.14 to 0.25 per second).
            double first = before[0];
            double second = after - before[0];
            helper.assertTrue(first > 0.4 && second > 0.4, "needs didn't rise: " + first + ", " + second);
            helper.assertTrue(Math.abs(first - second) < 0.35, "rate changed with the step: " + first + " then " + second);
            visitor.discard();
            TestZoos.cleanUp(helper, setup.owner(), setup.zoo());
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 200)
    public static void realPathPlansAreCountedAndShortOnesFlagged(GameTestHelper helper) {
        Visitor visitor = ZooEntities.VISITOR.get().create(helper.getLevel());
        BlockPos from = helper.absolutePos(new BlockPos(2, 2, 2));
        visitor.moveTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, 0, 0);
        helper.getLevel().addFreshEntity(visitor);
        boolean[] reached = new boolean[1];
        BlockPos target = helper.absolutePos(new BlockPos(20, 2, 2));
        helper.runAfterDelay(3, () -> {
            PathNavigation nav = visitor.getNavigation();
            net.minecraft.world.level.pathfinder.Path first = nav.createPath(target, 0);
            reached[0] = first != null && first.canReach();
            nav.moveTo(first, 0.6);
            nav.createPath(target, 0); // same target, path not done: reused, not a plan
        });
        helper.runAfterDelay(4, () -> {
            // Plans in the tick that just ended (other tests' visitors may add to it, never take away).
            long plans = SimulationTimers.global().calls(PerfSlot.VISITOR_PATHFINDING.ordinal(), 1);
            helper.assertTrue(reached[0], "the 18-block path should reach");
            helper.assertTrue(plans >= 1, "the plan wasn't counted");
            helper.assertTrue(((VisitorNavigation) visitor.getNavigation()).missesInARow(target) == 0, "a reachable target was counted as missed");
            visitor.discard();
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 100)
    public static void followRangeComesFromConfig(GameTestHelper helper) {
        Visitor visitor = ZooEntities.VISITOR.get().create(helper.getLevel());
        BlockPos from = helper.absolutePos(new BlockPos(2, 2, 2));
        visitor.moveTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, 0, 0);
        helper.getLevel().addFreshEntity(visitor);
        BlockPos target = helper.absolutePos(new BlockPos(44, 2, 2)); // 42 blocks away
        int original = ZooConfig.VISITOR_FOLLOW_RANGE.get();
        helper.runAfterDelay(2, () -> {
            ZooConfig.VISITOR_FOLLOW_RANGE.set(16);
            net.minecraft.world.level.pathfinder.Path tooShort = visitor.getNavigation().createPath(target, 0);
            visitor.getNavigation().stop();
            ZooConfig.VISITOR_FOLLOW_RANGE.set(48);
            net.minecraft.world.level.pathfinder.Path farEnough = visitor.getNavigation().createPath(target, 0);
            ZooConfig.VISITOR_FOLLOW_RANGE.set(original);
            helper.assertTrue(tooShort == null || !tooShort.canReach(), "a 16-block range reached 42 blocks away");
            helper.assertTrue(farEnough != null && farEnough.canReach(), "a 48-block range didn't reach 42 blocks away");
            visitor.discard();
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 100)
    public static void visitorsStillPreferAPathAlongside(GameTestHelper helper) {
        // A stone brick strip (a path block that doesn't fall) two blocks to the side of a 30-block walk across grass.
        for (int x = 2; x <= 36; x++) {
            helper.setBlock(new BlockPos(x, 1, 6), net.minecraft.world.level.block.Blocks.STONE_BRICKS);
        }
        Visitor visitor = ZooEntities.VISITOR.get().create(helper.getLevel());
        BlockPos from = helper.absolutePos(new BlockPos(4, 2, 4));
        visitor.moveTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, 0, 0);
        helper.getLevel().addFreshEntity(visitor);
        helper.runAfterDelay(2, () -> {
            net.minecraft.world.level.pathfinder.Path path = visitor.getNavigation().createPath(helper.absolutePos(new BlockPos(34, 2, 4)), 0);
            helper.assertTrue(path != null && path.canReach(), "no path along the strip");
            int onPath = 0;
            for (int i = 0; i < path.getNodeCount(); i++) {
                if (helper.getLevel().getBlockState(path.getNodePos(i).below()).is(net.minecraft.world.level.block.Blocks.STONE_BRICKS)) {
                    onPath++;
                }
            }
            helper.assertTrue(onPath * 2 > path.getNodeCount(), "only " + onPath + " of " + path.getNodeCount() + " nodes on the path");
            visitor.discard();
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1200)
    public static void throttledShortcutsWalkAsFastAsEveryTick(GameTestHelper helper) {
        ShortcutMode[] modes = {ShortcutMode.EVERY_TICK, ShortcutMode.EVERY_NODE, ShortcutMode.OFF};
        ShortcutMode original = ZooConfig.PATH_SHORTCUT_MODE.get();
        long[] arrival = new long[modes.length];
        // One per-tick state machine, registered once: scheduling tasks from inside scheduled tasks can break the
        // GameTest scheduler's iteration.
        int[] walk = {-1};
        long[] started = new long[1];
        Visitor[] walker = new Visitor[1];
        BlockPos[] goal = new BlockPos[1];
        helper.onEachTick(() -> {
            if (walk[0] >= modes.length) {
                return;
            }
            long now = helper.getTick();
            if (walk[0] < 0 || (walker[0] != null && walker[0].blockPosition().distSqr(goal[0]) <= 4)) {
                if (walk[0] >= 0) {
                    arrival[walk[0]] = now - started[0];
                    walker[0].discard();
                }
                walk[0]++;
                if (walk[0] == modes.length) {
                    ZooConfig.PATH_SHORTCUT_MODE.set(original);
                    helper.assertTrue(arrival[1] <= arrival[0] * 1.1 + 5, "EVERY_NODE took " + arrival[1] + " ticks, EVERY_TICK " + arrival[0]);
                    helper.assertTrue(arrival[2] > 0, "OFF never arrived");
                    helper.succeed();
                    return;
                }
                ZooConfig.PATH_SHORTCUT_MODE.set(modes[walk[0]]);
                Visitor visitor = ZooEntities.VISITOR.get().create(helper.getLevel());
                BlockPos from = helper.absolutePos(new BlockPos(4, 2, 4 + walk[0] * 12));
                goal[0] = helper.absolutePos(new BlockPos(34, 2, 4 + walk[0] * 12));
                visitor.moveTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, 0, 0);
                helper.getLevel().addFreshEntity(visitor);
                walker[0] = visitor;
                started[0] = now;
                return;
            }
            // Ground navigation only plans once the visitor stands on the ground.
            if (now - started[0] == 3) {
                helper.assertTrue(walker[0].getNavigation().moveTo(goal[0].getX() + 0.5, goal[0].getY(), goal[0].getZ() + 0.5, 0.6),
                        "no path in mode " + modes[walk[0]]);
            }
            if (now - started[0] > 350) {
                ZooConfig.PATH_SHORTCUT_MODE.set(original);
                helper.fail("mode " + modes[walk[0]] + " didn't arrive; arrivals so far " + java.util.Arrays.toString(arrival));
            }
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 200)
    public static void aNewPathGetsAShortcutAttemptStraightAway(GameTestHelper helper) {
        ShortcutMode original = ZooConfig.PATH_SHORTCUT_MODE.get();
        ZooConfig.PATH_SHORTCUT_MODE.set(ShortcutMode.EVERY_NODE);
        Visitor visitor = ZooEntities.VISITOR.get().create(helper.getLevel());
        BlockPos from = helper.absolutePos(new BlockPos(4, 2, 4));
        visitor.moveTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, 0, 0);
        helper.getLevel().addFreshEntity(visitor);
        VisitorNavigation nav = (VisitorNavigation) visitor.getNavigation();
        int[] before = new int[1];
        helper.runAfterDelay(3, () -> nav.moveTo(nav.createPath(helper.absolutePos(new BlockPos(40, 2, 4)), 0), 0.6));
        helper.runAfterDelay(8, () -> {
            before[0] = nav.shortcutAttempts();
            nav.moveTo(nav.createPath(helper.absolutePos(new BlockPos(4, 2, 40)), 0), 0.6); // a different path
        });
        helper.runAfterDelay(9, () -> {
            helper.assertTrue(nav.shortcutAttempts() > before[0], "no shortcut attempt on the new path");
            nav.stop();
            // A finished path must not throw.
            nav.attemptShortcut(0, visitor.position());
            ZooConfig.PATH_SHORTCUT_MODE.set(original);
            visitor.discard();
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 400)
    public static void arrivedVisitorsStopPlanning(GameTestHelper helper) {
        // An empty zoo: the visitor wanders between random spots and lingers at each. While lingering it should not
        // keep planning paths to the exact block it already stands beside.
        Setup setup = openZoo(helper, "Linger");
        Visitor visitor = exploringVisitor(helper, setup, 30, 24);
        helper.runAfterDelay(300, () -> {
            long plans = SimulationTimers.forZoo(setup.zoo().id()).map(t -> t.calls(PerfSlot.VISITOR_PATHFINDING.ordinal())).orElse(0L);
            helper.assertTrue(plans <= 12, plans + " path plans in 15 seconds for one wandering visitor");
            visitor.discard();
            TestZoos.cleanUp(helper, setup.owner(), setup.zoo());
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 100)
    public static void missedPathTargetsAreListed(GameTestHelper helper) {
        // A target walled in on all sides can't be reached.
        BlockPos inside = new BlockPos(20, 2, 20);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    helper.setBlock(inside.offset(dx, 0, dz), net.minecraft.world.level.block.Blocks.STONE);
                    helper.setBlock(inside.offset(dx, 1, dz), net.minecraft.world.level.block.Blocks.STONE);
                }
            }
        }
        Visitor visitor = ZooEntities.VISITOR.get().create(helper.getLevel());
        BlockPos from = helper.absolutePos(new BlockPos(4, 2, 4));
        visitor.moveTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, 0, 0);
        helper.getLevel().addFreshEntity(visitor);
        helper.runAfterDelay(3, () -> {
            BlockPos target = helper.absolutePos(inside);
            visitor.getNavigation().createPath(target, 0);
            helper.assertTrue(MissedTargets.top(50).stream().anyMatch(m -> m.pos().equals(target)), "the walled-in target isn't listed");
            visitor.discard();
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 500)
    public static void visitorsGiveUpQuicklyOnSpotsTheyCantReach(GameTestHelper helper) {
        // A cow in a sealed stone room: every viewing spot (up to 5.5 blocks from the cow) is at least 2 blocks inside the
        // walls, so no path can end close enough to count as arriving.
        for (int x = 23; x <= 42; x++) {
            for (int z = 23; z <= 42; z++) {
                if (x == 23 || x == 42 || z == 23 || z == 42) {
                    for (int y = 2; y <= 4; y++) {
                        helper.setBlock(new BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.STONE);
                    }
                }
            }
        }
        helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(32, 2, 32));
        Setup setup = openZoo(helper, "Sealed");
        Visitor visitor = exploringVisitor(helper, setup, 20, 24);
        helper.runAfterDelay(300, () -> {
            long now = helper.getLevel().getGameTime();
            List<com.zoocraft.animal.Exhibit> exhibits = com.zoocraft.animal.AnimalCensus.cached(setup.zoo()).exhibits();
            helper.assertTrue(!exhibits.isEmpty(), "the cow wasn't counted");
            helper.assertTrue(exhibits.getFirst().isUnreachable(now), "after 15 seconds the visitor still hasn't given up on the sealed room");
            visitor.discard();
            TestZoos.cleanUp(helper, setup.owner(), setup.zoo());
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 100)
    public static void aPathEndingBesideTheTargetIsNotAMiss(GameTestHelper helper) {
        // The target is just inside a fence; the path can end right beside it, close enough to arrive.
        for (int x = 18; x <= 22; x++) {
            for (int z = 18; z <= 22; z++) {
                if (x == 18 || x == 22 || z == 18 || z == 22) {
                    helper.setBlock(new BlockPos(x, 2, z), net.minecraft.world.level.block.Blocks.OAK_FENCE);
                }
            }
        }
        Visitor visitor = ZooEntities.VISITOR.get().create(helper.getLevel());
        BlockPos from = helper.absolutePos(new BlockPos(20, 2, 8));
        visitor.moveTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, 0, 0);
        helper.getLevel().addFreshEntity(visitor);
        BlockPos target = helper.absolutePos(new BlockPos(20, 2, 19));
        helper.runAfterDelay(3, () -> {
            VisitorNavigation nav = (VisitorNavigation) visitor.getNavigation();
            nav.createPath(target, 0);
            nav.stop();
            nav.createPath(target, 0);
            helper.assertTrue(nav.missesInARow(target) == 0, "a path ending beside the target counted " + nav.missesInARow(target) + " misses");
            visitor.discard();
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 100)
    public static void gettingCloserRestartsTheMissCount(GameTestHelper helper) {
        // A sealed stone room: its centre can't be reached, but a visitor walking towards it is making progress.
        for (int x = 26; x <= 39; x++) {
            for (int z = 26; z <= 39; z++) {
                if (x == 26 || x == 39 || z == 26 || z == 39) {
                    for (int y = 2; y <= 4; y++) {
                        helper.setBlock(new BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.STONE);
                    }
                }
            }
        }
        Visitor visitor = ZooEntities.VISITOR.get().create(helper.getLevel());
        BlockPos far = helper.absolutePos(new BlockPos(32, 2, 6));
        BlockPos nearer = helper.absolutePos(new BlockPos(32, 2, 14));
        visitor.moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5, 0, 0);
        helper.getLevel().addFreshEntity(visitor);
        BlockPos target = helper.absolutePos(new BlockPos(32, 2, 32));
        int[] counts = new int[3];
        helper.runAfterDelay(3, () -> {
            VisitorNavigation nav = (VisitorNavigation) visitor.getNavigation();
            nav.createPath(target, 0);
            counts[0] = nav.missesInARow(target);
            nav.stop();
            visitor.moveTo(nearer.getX() + 0.5, nearer.getY(), nearer.getZ() + 0.5, 0, 0);
        });
        helper.runAfterDelay(6, () -> {
            VisitorNavigation nav = (VisitorNavigation) visitor.getNavigation();
            nav.createPath(target, 0);
            counts[1] = nav.missesInARow(target);
            nav.stop();
            nav.createPath(target, 0);
            counts[2] = nav.missesInARow(target);
            helper.assertTrue(counts[0] == 1 && counts[1] == 1 && counts[2] == 2,
                    "miss counts " + java.util.Arrays.toString(counts) + ", expected [1, 1, 2]: progress restarts the count");
            visitor.discard();
            helper.succeed();
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
