package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import com.zoocraft.animal.AnimalCensus;
import com.zoocraft.animal.AnimalRecognition;
import com.zoocraft.animal.Exhibit;
import com.zoocraft.animal.Viewing;
import com.zoocraft.animal.ZooCensus;
import com.zoocraft.config.ZooConfig;
import com.zoocraft.core.visitor.VisitorProfile;
import com.zoocraft.registry.ZooEntities;
import com.zoocraft.visitor.Visitor;
import com.zoocraft.visitor.VisitorPhase;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(ZooCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AnimalGameTests {
    private static BlockPos at(int x, int z) {
        return new BlockPos(x, 2, z);
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void animalsRecognisedOthersNot(GameTestHelper helper) {
        Mob pig = helper.spawnWithNoFreeWill(EntityType.PIG, at(5, 5));
        Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, at(8, 5));
        Mob cod = helper.spawnWithNoFreeWill(EntityType.COD, at(11, 5));
        Mob villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, at(14, 5));
        Mob golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, at(17, 5));
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, at(20, 5));
        Mob hoglin = helper.spawnWithNoFreeWill(EntityType.HOGLIN, at(23, 5));
        Visitor visitor = helper.spawnWithNoFreeWill(ZooEntities.VISITOR.get(), at(26, 5));
        helper.assertTrue(AnimalRecognition.isExhibitAnimal(pig), "pig not recognised");
        helper.assertTrue(AnimalRecognition.isExhibitAnimal(cow), "cow not recognised");
        helper.assertTrue(AnimalRecognition.isExhibitAnimal(cod), "cod not recognised");
        helper.assertFalse(AnimalRecognition.isExhibitAnimal(villager), "villager recognised");
        helper.assertFalse(AnimalRecognition.isExhibitAnimal(golem), "iron golem recognised");
        helper.assertFalse(AnimalRecognition.isExhibitAnimal(zombie), "zombie recognised");
        helper.assertFalse(AnimalRecognition.isExhibitAnimal(hoglin), "hoglin (monster category) recognised");
        helper.assertFalse(AnimalRecognition.isExhibitAnimal(visitor), "visitor recognised");
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE)
    public static void censusCountsSpeciesAndExhibits(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Census", TestZoos.rectangle(helper, 2, 2, 45, 45));
        Pig adult = helper.spawnWithNoFreeWill(EntityType.PIG, at(8, 8));
        Pig baby = helper.spawnWithNoFreeWill(EntityType.PIG, at(10, 8));
        baby.setAge(-24000);
        helper.spawnWithNoFreeWill(EntityType.COW, at(38, 38));
        helper.spawnWithNoFreeWill(EntityType.COW, at(40, 38));
        helper.spawnWithNoFreeWill(EntityType.PIG, at(60, 60)); // outside the structure and the zoo

        ZooCensus census = AnimalCensus.refresh(zoo, helper.getLevel());
        helper.assertTrue(census.animals().size() == 4, "expected 4 animals, got " + census.animals().size());
        helper.assertTrue(census.speciesCounts().size() == 2, "baby pig counted as its own species: " + census.speciesCounts());
        helper.assertTrue(census.speciesCounts().get("minecraft:pig") == 2, "pig count wrong");
        helper.assertTrue(census.exhibits().size() == 2, "expected 2 exhibits, got " + census.exhibits().size());
        helper.assertTrue(census.exhibits().stream().allMatch(e -> !e.viewingSpots().isEmpty()), "exhibit without viewing spots");

        UUID firstId = census.exhibits().getFirst().id();
        ZooCensus again = AnimalCensus.refresh(zoo, helper.getLevel());
        helper.assertTrue(again.exhibit(firstId).isPresent(), "exhibit identity not kept between censuses");

        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void wallsBlockViewingButGlassDoesNot(GameTestHelper helper) {
        Mob behindStone = helper.spawnWithNoFreeWill(EntityType.PIG, at(10, 5));
        Mob behindGlass = helper.spawnWithNoFreeWill(EntityType.PIG, at(10, 15));
        Mob tooFar = helper.spawnWithNoFreeWill(EntityType.PIG, at(25, 25));
        for (int y = 2; y <= 4; y++) {
            for (int z = 3; z <= 7; z++) {
                helper.setBlock(new BlockPos(8, y, z), Blocks.STONE);
            }
            for (int z = 13; z <= 17; z++) {
                helper.setBlock(new BlockPos(8, y, z), Blocks.GLASS);
            }
        }
        Vec3 viewerAt5 = Vec3.atBottomCenterOf(helper.absolutePos(at(5, 5)));
        Vec3 viewerAt15 = Vec3.atBottomCenterOf(helper.absolutePos(at(5, 15)));
        helper.assertFalse(Viewing.canSee(helper.getLevel(), viewerAt5.add(0, 1.62, 0), viewerAt5, behindStone, 6), "seen through stone");
        helper.assertTrue(Viewing.canSee(helper.getLevel(), viewerAt15.add(0, 1.62, 0), viewerAt15, behindGlass, 6), "not seen through glass");
        helper.assertFalse(Viewing.canSee(helper.getLevel(), viewerAt15.add(0, 1.62, 0), viewerAt15, tooFar, 6), "seen from too far away");
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1600)
    public static void visitorGoesToSeeTheAnimals(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Viewing", TestZoos.rectangle(helper, 2, 2, 45, 45));
        ZooManager zoos = TestZoos.manager(helper);
        zoos.addGate(owner, helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 24)));
        zoos.setPrice(owner, zoo, 0);
        zoos.setOpen(owner, zoo, true);
        helper.spawnWithNoFreeWill(EntityType.PIG, at(38, 38));
        helper.spawnWithNoFreeWill(EntityType.PIG, at(39, 39));
        AnimalCensus.refresh(zoo, helper.getLevel());

        Visitor visitor = ZooEntities.VISITOR.get().create(helper.getLevel());
        BlockPos start = helper.absolutePos(at(8, 8));
        visitor.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0, 0);
        visitor.begin(zoo, zoo.gates().getFirst(), UUID.randomUUID(), true);
        visitor.testSetBudget(VisitorProfile.ANIMAL_ENTHUSIAST, 10, 1.0);
        visitor.testSetPhase(VisitorPhase.EXPLORING);
        helper.getLevel().addFreshEntity(visitor);

        helper.succeedWhen(() -> {
            helper.assertTrue(visitor.seenSpecies().contains("minecraft:pig"), "visitor has not seen the pigs yet");
            helper.assertTrue(zoo.stats().commentTally().getOrDefault("amazing_exhibit", 0) >= 1, "no reaction recorded");
            visitor.discard();
            TestZoos.cleanUp(helper, owner, zoo);
        });
    }

    /** Fences the square ring from (min, min) to (max, max) at standing height. */
    static void fenceRing(GameTestHelper helper, int min, int max) {
        for (int i = min; i <= max; i++) {
            helper.setBlock(new BlockPos(i, 2, min), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(i, 2, max), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(min, 2, i), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(max, 2, i), Blocks.OAK_FENCE);
        }
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1600)
    public static void aTwentyByTwentyPenGetsSpotsOutsideTheFenceAndIsViewed(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Big pen", TestZoos.rectangle(helper, 2, 2, 45, 45));
        ZooManager zoos = TestZoos.manager(helper);
        zoos.addGate(owner, helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 24)));
        zoos.setPrice(owner, zoo, 0);
        zoos.setOpen(owner, zoo, true);
        fenceRing(helper, 13, 34); // a 20 by 20 floor inside, x and z 14..33
        helper.spawnWithNoFreeWill(EntityType.COW, at(16, 16));
        helper.spawnWithNoFreeWill(EntityType.COW, at(17, 15));
        helper.runAfterDelay(2, () -> {
            ZooCensus census = AnimalCensus.refresh(zoo, helper.getLevel());
            AnimalCensus.runPendingFills(helper.getLevel().getServer(), 100);
            Exhibit exhibit = census.exhibits().getFirst();
            helper.assertTrue(exhibit.enclosure().bounded() && exhibit.enclosure().area() == 400,
                    "enclosure " + exhibit.enclosure().bounded() + " " + exhibit.enclosure().area());
            helper.assertFalse(exhibit.viewingSpots().isEmpty(), "no spots");
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            for (BlockPos spot : exhibit.viewingSpots()) {
                int x = spot.getX() - origin.getX(), z = spot.getZ() - origin.getZ();
                helper.assertFalse(x >= 13 && x <= 34 && z >= 13 && z <= 34, "spot inside the fence at " + x + "," + z);
            }
            Visitor visitor = PerfGameTests.exploringVisitor(helper, new PerfGameTests.Setup(owner, zoo, zoo.gates().getFirst()), 8, 8);
            visitor.testSetBudget(VisitorProfile.ANIMAL_ENTHUSIAST, 10, 1.0);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(zoo.stats().commentTally().getOrDefault("amazing_exhibit", 0) >= 1, "the cows haven't been viewed");
            TestZoos.cleanUp(helper, owner, zoo);
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void animalsAreFoundAtAnyHeight(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Deep", TestZoos.rectangle(helper, 2, 2, 29, 29));
        BlockPos floor = helper.absolutePos(at(10, 10));
        // The test floor sits near the bottom of the world, so the animal goes 70 blocks up (the old search reached 64).
        int y = floor.getY() - 60 >= helper.getLevel().getMinBuildHeight() + 2 ? floor.getY() - 60 : floor.getY() + 70;
        Mob cow = EntityType.COW.create(helper.getLevel());
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.moveTo(floor.getX() + 0.5, y, floor.getZ() + 0.5, 0, 0);
        helper.getLevel().addFreshEntity(cow);
        ZooCensus census = AnimalCensus.refresh(zoo, helper.getLevel());
        helper.assertTrue(census.animals().contains(cow), "the cow " + (y - floor.getY()) + " blocks away vertically wasn't counted");
        cow.discard();
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void unfencedAnimalsStillGetSpots(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Open", TestZoos.rectangle(helper, 2, 2, 29, 29));
        helper.spawnWithNoFreeWill(EntityType.SHEEP, at(15, 15));
        ZooCensus census = AnimalCensus.refresh(zoo, helper.getLevel());
        AnimalCensus.runPendingFills(helper.getLevel().getServer(), 100);
        Exhibit exhibit = census.exhibits().getFirst();
        helper.assertFalse(exhibit.enclosure().bounded(), "an unfenced sheep has a bounded enclosure");
        helper.assertFalse(AnimalCensus.refresh(zoo, helper.getLevel()).exhibits().getFirst().viewingSpots().isEmpty(), "no spots");
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }

    /** An open zoo over the large floor with a 4 by 4 pen of two pigs at x and z 20..23, its enclosure already found. */
    private static Zoo crowdScene(GameTestHelper helper, ServerPlayer owner) {
        Zoo zoo = TestZoos.create(helper, owner, "Crowd", TestZoos.rectangle(helper, 2, 2, 45, 45));
        ZooManager zoos = TestZoos.manager(helper);
        zoos.addGate(owner, helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 24)));
        zoos.setPrice(owner, zoo, 0);
        zoos.setOpen(owner, zoo, true);
        fenceRing(helper, 19, 24);
        helper.spawnWithNoFreeWill(EntityType.PIG, at(21, 21));
        helper.spawnWithNoFreeWill(EntityType.PIG, at(22, 22));
        AnimalCensus.refresh(zoo, helper.getLevel());
        AnimalCensus.runPendingFills(helper.getLevel().getServer(), 100);
        return zoo;
    }

    private static PerfGameTests.Setup setupOf(ServerPlayer owner, Zoo zoo) {
        return new PerfGameTests.Setup(owner, zoo, zoo.gates().getFirst());
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1600, batch = "config")
    public static void eightVisitorsFillFourSpotsTwoEach(GameTestHelper helper) {
        int original = ZooConfig.MAX_VIEWING_SPOTS.get();
        ZooConfig.MAX_VIEWING_SPOTS.set(4);
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = crowdScene(helper, owner);
        List<Visitor> visitors = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            visitors.add(PerfGameTests.exploringVisitor(helper, setupOf(owner, zoo), 6 + i, 6));
        }
        helper.succeedWhen(() -> {
            Exhibit exhibit = AnimalCensus.cached(zoo).exhibits().getFirst();
            long now = helper.getLevel().getGameTime();
            helper.assertTrue(exhibit.viewingSpots().size() == 4, "spots " + exhibit.viewingSpots().size());
            helper.assertTrue(exhibit.viewingSpots().stream().allMatch(s -> exhibit.claimCount(s, now) == 2), "not two on every spot");
            ZooConfig.MAX_VIEWING_SPOTS.set(original);
            visitors.forEach(Visitor::discard);
            TestZoos.cleanUp(helper, owner, zoo);
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 1200)
    public static void aVisitorFindingEverySpotFullSaysItIsTooCrowded(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = crowdScene(helper, owner);
        Exhibit exhibit = AnimalCensus.cached(zoo).exhibits().getFirst();
        List<UUID> fakes = new ArrayList<>();
        for (int i = 0; i < 2 * exhibit.viewingSpots().size(); i++) {
            fakes.add(UUID.randomUUID());
        }
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            for (int i = 0; i < fakes.size(); i++) {
                exhibit.claims().claim(exhibit.viewingSpots().get(i / 2), fakes.get(i), now, 2);
            }
        });
        Visitor visitor = PerfGameTests.exploringVisitor(helper, setupOf(owner, zoo), 8, 8);
        helper.succeedWhen(() -> {
            helper.assertTrue(zoo.stats().commentTally().getOrDefault("crowded", 0) == 1, "no crowded comment");
            helper.assertTrue(zoo.reports().subjectsToday().containsKey("crowded|" + exhibit.id()), "complaint doesn't name the exhibit");
            visitor.discard();
            TestZoos.cleanUp(helper, owner, zoo);
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 400)
    public static void removingAVisitorFreesItsViewingSpot(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = crowdScene(helper, owner);
        Visitor visitor = PerfGameTests.exploringVisitor(helper, setupOf(owner, zoo), 8, 8);
        Exhibit exhibit = AnimalCensus.cached(zoo).exhibits().getFirst();
        long[] claimedAt = {-1};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (claimedAt[0] < 0 && visitor.claimedSpot() != null) {
                claimedAt[0] = now;
                helper.assertTrue(exhibit.claimCount(visitor.claimedSpot(), now) == 1, "claim not counted");
                visitor.discard();
            } else if (claimedAt[0] >= 0 && now > claimedAt[0]) {
                helper.assertTrue(exhibit.viewingSpots().stream().allMatch(s -> exhibit.claimCount(s, now) == 0), "spot still held");
                TestZoos.cleanUp(helper, owner, zoo);
                helper.succeed();
            }
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 100)
    public static void aCrampedPenRaisesTheCrampedWarning(GameTestHelper helper) {
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = TestZoos.create(helper, owner, "Cramped", TestZoos.rectangle(helper, 2, 2, 45, 45));
        fenceRing(helper, 19, 23); // a 3 by 3 floor at x and z 20..22
        for (int i = 0; i < 4; i++) {
            helper.spawnWithNoFreeWill(EntityType.COW, at(20 + i % 3, 20 + i / 3));
        }
        helper.runAfterDelay(2, () -> {
            AnimalCensus.refresh(zoo, helper.getLevel());
            AnimalCensus.runPendingFills(helper.getLevel().getServer(), 100);
            Exhibit exhibit = AnimalCensus.refresh(zoo, helper.getLevel()).exhibits().getFirst();
            helper.assertTrue(exhibit.enclosure().bounded() && exhibit.enclosure().area() == 9, "area " + exhibit.enclosure().area());
            helper.assertTrue(exhibit.rating().warnings().contains(com.zoocraft.core.exhibit.ExhibitRating.Warning.CRAMPED),
                    "no cramped warning: " + exhibit.rating());

            // The Animals tab shows the rating and the warning, and both survive the trip to the client.
            com.zoocraft.network.DashboardSnapshot snapshot = com.zoocraft.dashboard.DashboardService.snapshot(owner, zoo,
                    com.zoocraft.dashboard.DashboardTab.ANIMALS);
            net.minecraft.network.RegistryFriendlyByteBuf buf = new net.minecraft.network.RegistryFriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
            com.zoocraft.network.DashboardSnapshot.STREAM_CODEC.encode(buf, snapshot);
            com.zoocraft.network.DashboardSnapshot.ExhibitLine line = com.zoocraft.network.DashboardSnapshot.STREAM_CODEC.decode(buf)
                    .exhibits().getFirst();
            helper.assertTrue(line.grade() == exhibit.rating().grade().ordinal() && line.score() == exhibit.rating().score(), "rating lost");
            helper.assertTrue(line.warnings().stream().anyMatch(w -> w.getString().contains("cramped")), "warnings " + line.warnings());
            TestZoos.cleanUp(helper, owner, zoo);
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.FLOOR_LARGE, timeoutTicks = 100)
    public static void wanderSpotsBesideAPenStayOutOfIt(GameTestHelper helper) {
        // A group follower picks a spot within 3 blocks of its leader; a leader at a pen-side spot mustn't send it into the pen.
        ServerPlayer owner = TestZoos.player(helper, "Owner");
        Zoo zoo = crowdScene(helper, owner); // pen floor at x and z 20..23
        Visitor visitor = PerfGameTests.exploringVisitor(helper, setupOf(owner, zoo), 17, 21);
        helper.assertTrue(AnimalCensus.cached(zoo).exhibits().getFirst().enclosure().bounded(), "pen not found");
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        for (int i = 0; i < 200; i++) {
            visitor.testRandomSpotNear(helper.absolutePos(new BlockPos(18, 2, 21)), 3).ifPresent(spot -> {
                int x = spot.getX() - origin.getX(), z = spot.getZ() - origin.getZ();
                helper.assertFalse(x >= 20 && x <= 23 && z >= 20 && z <= 23, "a wander spot inside the pen at " + x + "," + z);
            });
        }
        visitor.discard();
        TestZoos.cleanUp(helper, owner, zoo);
        helper.succeed();
    }
}
