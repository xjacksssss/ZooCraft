package com.zoocraft.shop;

import com.zoocraft.config.ZooConfig;
import com.zoocraft.core.shop.ProductCategory;
import com.zoocraft.core.shop.ProductFacts;
import com.zoocraft.core.shop.ProductRules;
import com.zoocraft.registry.ZooTags;
import com.zoocraft.zoo.Shop;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.Optional;

/** Turns Minecraft items into shop facts, categories, IDs and default prices (Phase 2 spec section 7.2). */
public final class ProductCatalog {
    private ProductCatalog() {
    }

    public static ProductFacts facts(ItemStack stack) {
        ItemContainerContents container = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        BundleContents bundle = stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        boolean hasContents = container.nonEmptyItems().iterator().hasNext() || !bundle.isEmpty();
        return new ProductFacts(stack.is(ZooTags.UNSELLABLE), stack.getItem() instanceof SpawnEggItem, hasContents,
                stack.is(ZooTags.SHOP_DRINKS), stack.has(DataComponents.FOOD), stack.is(ZooTags.SHOP_FOOD),
                stack.is(ZooTags.NOT_VISITOR_FOOD));
    }

    public static ProductCategory category(ItemStack stack) {
        return ProductRules.classify(facts(stack));
    }

    public static String itemId(ItemStack stack) {
        return itemId(stack.getItem());
    }

    public static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /** The item with this ID, if it exists. */
    public static Optional<Item> item(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.ITEM.getOptional(id).filter(item -> item != Items.AIR);
    }

    public static int defaultPrice(ProductCategory category) {
        return switch (category) {
            case FOOD -> ZooConfig.DEFAULT_FOOD_PRICE.get();
            case DRINK -> ZooConfig.DEFAULT_DRINK_PRICE.get();
            case SOUVENIR, UNSELLABLE -> ZooConfig.DEFAULT_SOUVENIR_PRICE.get();
        };
    }

    /** The shop's price for an item: the one set for it, or its category default. */
    public static int priceFor(Shop shop, String itemId) {
        return shop.price(itemId).orElseGet(() -> defaultPrice(item(itemId).map(i -> category(new ItemStack(i))).orElse(ProductCategory.SOUVENIR)));
    }
}
