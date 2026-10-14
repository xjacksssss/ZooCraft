package com.zoocraft.core.shop;

import com.zoocraft.core.ledger.Ledger;
import com.zoocraft.core.ledger.Transaction;
import com.zoocraft.core.ledger.TransactionType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SaleTest {
    /** Counts units per item. {@code takeFails} makes take() remove nothing even though canTake() said yes. */
    private static final class FakeStock implements StockSource {
        final Map<String, Integer> counts = new HashMap<>();
        boolean takeFails;
        Runnable duringTake = () -> { };
        private String lastTaken;

        FakeStock with(String item, int count) {
            counts.put(item, count);
            return this;
        }

        @Override
        public boolean canTake(String itemId) {
            return counts.getOrDefault(itemId, 0) > 0;
        }

        @Override
        public boolean take(String itemId) {
            duringTake.run();
            if (takeFails || !canTake(itemId)) {
                return false;
            }
            counts.merge(itemId, -1, Integer::sum);
            lastTaken = itemId;
            return true;
        }

        @Override
        public void undoTake() {
            counts.merge(lastTaken, 1, Integer::sum);
        }
    }

    private static SaleRequest request(String key, int price, int budget, boolean usable) {
        return new SaleRequest(key, "minecraft:bread", price, budget, usable, 100L, "Visitor");
    }

    private static void assertUnchanged(Ledger ledger, FakeStock stock, String key) {
        assertEquals(0, ledger.balance());
        assertEquals(List.of(), ledger.keys().stream().filter(k -> k.equals(key)).toList());
        assertEquals(5, stock.counts.get("minecraft:bread"));
    }

    @Test
    void successTakesOneUnitAndPaysOnce() {
        Ledger ledger = new Ledger();
        FakeStock stock = new FakeStock().with("minecraft:bread", 5);
        assertEquals(SaleResult.SOLD, Sale.attempt(ledger, stock, request("sale:v:0", 2, 10, true)));
        assertEquals(4, stock.counts.get("minecraft:bread"));
        assertEquals(2, ledger.balance());
        assertEquals(2, ledger.totalShopSales());
        assertEquals(1, ledger.recent().size());
    }

    @Test
    void repeatedKeyChangesNothing() {
        Ledger ledger = new Ledger();
        FakeStock stock = new FakeStock().with("minecraft:bread", 5);
        Sale.attempt(ledger, stock, request("sale:v:0", 2, 10, true));
        assertEquals(SaleResult.DUPLICATE, Sale.attempt(ledger, stock, request("sale:v:0", 2, 10, true)));
        assertEquals(4, stock.counts.get("minecraft:bread"));
        assertEquals(2, ledger.balance());
    }

    @Test
    void closedShopChangesNothing() {
        Ledger ledger = new Ledger();
        FakeStock stock = new FakeStock().with("minecraft:bread", 5);
        assertEquals(SaleResult.SHOP_CLOSED, Sale.attempt(ledger, stock, request("k", 2, 10, false)));
        assertUnchanged(ledger, stock, "k");
    }

    @Test
    void invalidPriceChangesNothing() {
        Ledger ledger = new Ledger();
        FakeStock stock = new FakeStock().with("minecraft:bread", 5);
        assertEquals(SaleResult.INVALID_PRICE, Sale.attempt(ledger, stock, request("k", 0, 10, true)));
        assertUnchanged(ledger, stock, "k");
    }

    @Test
    void unaffordablePriceChangesNothing() {
        Ledger ledger = new Ledger();
        FakeStock stock = new FakeStock().with("minecraft:bread", 5);
        assertEquals(SaleResult.CANT_AFFORD, Sale.attempt(ledger, stock, request("k", 2, 1, true)));
        assertUnchanged(ledger, stock, "k");
    }

    @Test
    void emptyStockChangesNothing() {
        Ledger ledger = new Ledger();
        FakeStock stock = new FakeStock().with("minecraft:bread", 0);
        assertEquals(SaleResult.OUT_OF_STOCK, Sale.attempt(ledger, stock, request("k", 2, 10, true)));
        assertEquals(0, ledger.balance());
        assertEquals(0, stock.counts.get("minecraft:bread"));
    }

    @Test
    void failedTakeChangesNothing() {
        Ledger ledger = new Ledger();
        FakeStock stock = new FakeStock().with("minecraft:bread", 5);
        stock.takeFails = true;
        assertEquals(SaleResult.OUT_OF_STOCK, Sale.attempt(ledger, stock, request("k", 2, 10, true)));
        assertUnchanged(ledger, stock, "k");
    }

    @Test
    void rejectedPaymentPutsTheItemBack() {
        Ledger ledger = new Ledger();
        FakeStock stock = new FakeStock().with("minecraft:bread", 5);
        // Something applies the same key while the item is being taken, so the payment is a duplicate.
        stock.duringTake = () -> ledger.apply(new Transaction("k", TransactionType.DEPOSIT, 1, 0, "x"));
        assertThrows(IllegalStateException.class, () -> Sale.attempt(ledger, stock, request("k", 2, 10, true)));
        assertEquals(5, stock.counts.get("minecraft:bread"));
        assertEquals(1, ledger.balance());
    }
}
