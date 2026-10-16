package com.zoocraft.gametest;

import com.zoocraft.ZooCraft;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The survival recipes load: both zoo tools and the rubbish bin. */
@GameTestHolder(ZooCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeGameTests {
    @GameTest(template = GameTestTemplates.FLOOR)
    public static void toolAndBinRecipesLoad(GameTestHelper helper) {
        var recipes = helper.getLevel().getServer().getRecipeManager();
        for (String id : new String[]{"rubbish_bin", "boundary_tool", "designation_tool"}) {
            helper.assertTrue(recipes.byKey(ZooCraft.id(id)).isPresent(), "recipe zoocraft:" + id + " didn't load");
        }
        helper.succeed();
    }
}
