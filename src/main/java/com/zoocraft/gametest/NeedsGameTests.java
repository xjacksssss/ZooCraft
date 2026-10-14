package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import com.zoocraft.core.needs.Need;
import com.zoocraft.registry.ZooEntities;
import com.zoocraft.visitor.Visitor;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Visitor needs, errands and toilets (Phase 2 spec section 8). */
@GameTestHolder(ZooCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeedsGameTests {
    @GameTest(template = GameTestTemplates.FLOOR)
    public static void needsAndShoppingStateSurviveSaveAndLoad(GameTestHelper helper) {
        Visitor visitor = ZooEntities.VISITOR.get().create(helper.getLevel());
        visitor.testSetNeeds(70, 40);
        visitor.testSetSouvenirRoll(0.2);
        visitor.testSetShopping(3, true, 9);

        CompoundTag saved = new CompoundTag();
        visitor.saveWithoutId(saved);
        Visitor copy = ZooEntities.VISITOR.get().create(helper.getLevel());
        copy.load(saved);

        helper.assertTrue(Math.abs(copy.needs().level(Need.HUNGER) - 70) < 1e-9, "hunger lost: " + copy.needs().hunger());
        helper.assertTrue(Math.abs(copy.needs().level(Need.TOILET) - 40) < 1e-9, "toilet need lost");
        helper.assertTrue(Math.abs(copy.needs().hungerRate() - visitor.needs().hungerRate()) < 1e-9, "hunger rate lost");
        helper.assertTrue(copy.saleIndex() == 3, "sale index lost: " + copy.saleIndex());
        helper.assertTrue(copy.lastStopDone(), "last stop flag lost");
        helper.assertTrue(copy.spentInShops() == 9, "spending lost");
        helper.assertTrue(Math.abs(copy.souvenirRoll() - 0.2) < 1e-9, "souvenir roll lost");
        visitor.discard();
        copy.discard();
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.FLOOR)
    public static void phaseOneVisitorLoadsWithFreshNeeds(GameTestHelper helper) {
        Visitor visitor = ZooEntities.VISITOR.get().create(helper.getLevel());
        CompoundTag saved = new CompoundTag();
        visitor.saveWithoutId(saved);
        CompoundTag visit = saved.getCompound("ZooVisit");
        for (String key : new String[]{"needs", "saleIndex", "souvenirRoll", "lastStopDone", "spentInShops"}) {
            visit.remove(key);
        }
        Visitor old = ZooEntities.VISITOR.get().create(helper.getLevel());
        old.load(saved);
        helper.assertTrue(old.saleIndex() == 0 && !old.lastStopDone() && old.spentInShops() == 0, "shopping state should start empty");
        helper.assertTrue(old.needs().hungerRate() >= 0.14 && old.needs().hungerRate() <= 0.25, "needs should be rolled: " + old.needs().hungerRate());
        helper.assertTrue(old.souvenirRoll() >= 0 && old.souvenirRoll() < 1, "souvenir roll should be rolled");
        visitor.discard();
        old.discard();
        helper.succeed();
    }
}
