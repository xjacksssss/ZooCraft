package com.zoocraft.core.shop;

/**
 * What the game knows about an item, gathered by the Minecraft side so the rules stay pure.
 *
 * @param unsellableTag  in {@code zoocraft:unsellable}
 * @param spawnEgg       a spawn egg
 * @param hasContents    holds other items (container or bundle contents), which a sale would lose
 * @param drinkTag       in {@code zoocraft:shop_drinks}
 * @param foodComponent  has the food data component
 * @param foodTag        in {@code zoocraft:shop_food}
 * @param notVisitorFood in {@code zoocraft:not_visitor_food}
 */
public record ProductFacts(boolean unsellableTag, boolean spawnEgg, boolean hasContents,
                           boolean drinkTag, boolean foodComponent, boolean foodTag, boolean notVisitorFood) {
}
