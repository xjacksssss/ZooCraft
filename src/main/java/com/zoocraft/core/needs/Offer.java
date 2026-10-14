package com.zoocraft.core.needs;

import com.zoocraft.core.shop.ProductCategory;

import java.util.UUID;

/**
 * Something a shop could sell to a visitor.
 *
 * @param categoryDefault the category's default price, which anchors what visitors think is reasonable
 * @param distance        from the visitor to the shop's counter, in blocks
 */
public record Offer(UUID shopId, String itemId, ProductCategory category, int price, int categoryDefault, double distance) {
}
