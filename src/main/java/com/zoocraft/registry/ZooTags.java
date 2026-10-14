package com.zoocraft.registry;

import com.zoocraft.ZooCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ZooTags {
    /** Blocks visitors prefer to walk on. Modpacks can extend this tag. */
    public static final TagKey<Block> VISITOR_PATHS = TagKey.create(Registries.BLOCK, ZooCraft.id("visitor_paths"));

    /** Items shops never sell (Phase 2 spec section 7.2). */
    public static final TagKey<Item> UNSELLABLE = item("unsellable");
    /** Items that count as drinks for visitors. */
    public static final TagKey<Item> SHOP_DRINKS = item("shop_drinks");
    /** Items that count as food even without the food component. */
    public static final TagKey<Item> SHOP_FOOD = item("shop_food");
    /** Edible items visitors won't buy as food or drink; they can still sell as souvenirs. */
    public static final TagKey<Item> NOT_VISITOR_FOOD = item("not_visitor_food");

    private ZooTags() {
    }

    private static TagKey<Item> item(String name) {
        return TagKey.create(Registries.ITEM, ZooCraft.id(name));
    }
}
