package com.zoocraft.core.ledger;

/**
 * One treasury movement.
 *
 * @param key      unique key; a second transaction with the same key is rejected (admission uses the visit ID)
 * @param amount   always positive; the type decides the direction
 * @param gameTime server game time when it happened
 * @param actor    who caused it, as display text (player name or "Visitor")
 */
public record Transaction(String key, TransactionType type, long amount, long gameTime, String actor) {
    public long signedAmount() {
        return type.sign() * amount;
    }
}
