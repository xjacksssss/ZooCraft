package com.zoocraft.core.ledger;

public enum LedgerResult {
    APPLIED,
    DUPLICATE,
    INSUFFICIENT_FUNDS,
    INVALID_AMOUNT;

    public boolean applied() {
        return this == APPLIED;
    }
}
