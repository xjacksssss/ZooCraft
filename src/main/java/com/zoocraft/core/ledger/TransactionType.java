package com.zoocraft.core.ledger;

public enum TransactionType {
    DEPOSIT(1),
    WITHDRAW(-1),
    ADMISSION(1),
    SHOP_SALE(1);

    private final int sign;

    TransactionType(int sign) {
        this.sign = sign;
    }

    /** +1 when the transaction adds to the treasury, -1 when it takes from it. */
    public int sign() {
        return sign;
    }
}
