package com.zoocraft.core.shop;

/**
 * One attempt to buy one unit.
 *
 * @param key        unique per purchase ({@code sale:<visitId>:<saleIndex>}); a repeated key never sells twice
 * @param budget     what the buyer can still spend
 * @param shopUsable the shop is open and its counter is still inside the boundary
 */
public record SaleRequest(String key, String itemId, int price, int budget, boolean shopUsable, long gameTime, String actor) {
}
