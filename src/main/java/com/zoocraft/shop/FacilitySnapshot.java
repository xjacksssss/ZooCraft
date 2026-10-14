package com.zoocraft.shop;

import com.zoocraft.core.shop.ProductCategory;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * A zoo's facilities as of the last facility census (Phase 2 spec section 4.4). Visitors choose errands from this
 * only; the real stock is checked again at the moment of a sale.
 */
public record FacilitySnapshot(long takenAt, List<ShopState> shops) {
    public static final FacilitySnapshot EMPTY = new FacilitySnapshot(-1, List.of());

    /** @param usable open and its counter still inside the boundary */
    public record ShopState(UUID shopId, BlockPos counter, boolean usable, List<Product> products) {
        /** The cheapest in-stock price in a category, if the shop has any. */
        public OptionalInt cheapest(ProductCategory category) {
            return products.stream().filter(p -> p.category() == category && p.count() > 0).mapToInt(Product::price).min();
        }
    }

    /** One sellable item in stock, with its current price. */
    public record Product(String itemId, ProductCategory category, int count, int price) {
    }

    public Optional<ShopState> shop(UUID shopId) {
        return shops.stream().filter(s -> s.shopId().equals(shopId)).findFirst();
    }
}
