package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import com.zoocraft.animal.AnimalCensus;
import com.zoocraft.animal.AnimalRecognition;
import com.zoocraft.animal.Viewing;
import com.zoocraft.animal.ZooCensus;
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
}
