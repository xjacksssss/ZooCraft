package com.zoocraft.core.shop;

public enum SaleResult {
    SOLD,
    SHOP_CLOSED,
    DUPLICATE,
    CANT_AFFORD,
    OUT_OF_STOCK,
    INVALID_PRICE;

    public boolean sold() {
        return this == SOLD;
    }
}
