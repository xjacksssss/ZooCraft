package com.zoocraft.core.shop;

import com.zoocraft.core.ledger.Ledger;
import com.zoocraft.core.ledger.LedgerResult;
import com.zoocraft.core.ledger.Transaction;
import com.zoocraft.core.ledger.TransactionType;

/**
 * The exactly-once purchase (spec section 7.5): every check first, then take the item, then pay, all in one call
 * on the server thread. Once the item is taken, payment cannot fail. The caller deducts the buyer's budget only
 * when the result is {@link SaleResult#SOLD}.
 */
public final class Sale {
    private Sale() {
    }

    public static SaleResult attempt(Ledger ledger, StockSource stock, SaleRequest r) {
        if (!r.shopUsable()) {
            return SaleResult.SHOP_CLOSED;
        }
        if (!PriceRules.valid(r.price())) {
            return SaleResult.INVALID_PRICE;
        }
        if (ledger.hasKey(r.key())) {
            return SaleResult.DUPLICATE;
        }
        if (r.price() > r.budget()) {
            return SaleResult.CANT_AFFORD;
        }
        if (!stock.canTake(r.itemId()) || !stock.take(r.itemId())) {
            return SaleResult.OUT_OF_STOCK;
        }
        LedgerResult paid = ledger.apply(new Transaction(r.key(), TransactionType.SHOP_SALE, r.price(), r.gameTime(), r.actor()));
        if (!paid.applied()) {
            // Unreachable unless something changed the ledger mid-sale: undo so neither side moves.
            stock.undoTake();
            throw new IllegalStateException("sale payment failed after its checks passed: " + paid);
        }
        return SaleResult.SOLD;
    }
}
