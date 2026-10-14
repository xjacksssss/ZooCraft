package com.zoocraft.core.ledger;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerTest {
    private static Transaction tx(String key, TransactionType type, long amount) {
        return new Transaction(key, type, amount, 0L, "tester");
    }

    @Test
    void depositThenWithdraw() {
        Ledger ledger = new Ledger();
        assertEquals(LedgerResult.APPLIED, ledger.apply(tx("d1", TransactionType.DEPOSIT, 50)));
        assertEquals(LedgerResult.APPLIED, ledger.apply(tx("w1", TransactionType.WITHDRAW, 20)));
        assertEquals(30, ledger.balance());
        assertEquals(50, ledger.totalDeposits());
        assertEquals(20, ledger.totalWithdrawals());
    }

    @Test
    void overdrawIsRejectedAndChangesNothing() {
        Ledger ledger = new Ledger();
        ledger.apply(tx("d1", TransactionType.DEPOSIT, 10));
        assertEquals(LedgerResult.INSUFFICIENT_FUNDS, ledger.apply(tx("w1", TransactionType.WITHDRAW, 11)));
        assertEquals(10, ledger.balance());
        assertEquals(0, ledger.totalWithdrawals());
        assertEquals(1, ledger.recent().size());
        assertFalse(ledger.hasKey("w1"));
    }

    @Test
    void repeatedKeyIsRejectedAndChangesNothing() {
        Ledger ledger = new Ledger();
        assertEquals(LedgerResult.APPLIED, ledger.apply(tx("visit-1", TransactionType.ADMISSION, 5)));
        assertEquals(LedgerResult.DUPLICATE, ledger.apply(tx("visit-1", TransactionType.ADMISSION, 5)));
        assertEquals(5, ledger.balance());
        assertEquals(5, ledger.totalAdmission());
        assertEquals(1, ledger.recent().size());
    }

    @Test
    void nonPositiveAmountsAreRejected() {
        Ledger ledger = new Ledger();
        assertEquals(LedgerResult.INVALID_AMOUNT, ledger.apply(tx("a", TransactionType.DEPOSIT, 0)));
        assertEquals(LedgerResult.INVALID_AMOUNT, ledger.apply(tx("b", TransactionType.DEPOSIT, -5)));
        assertEquals(0, ledger.balance());
        assertTrue(ledger.recent().isEmpty());
    }

    @Test
    void recentListIsCappedNewestFirst() {
        Ledger ledger = new Ledger();
        for (int i = 0; i < 250; i++) {
            ledger.apply(tx("d" + i, TransactionType.DEPOSIT, 1));
        }
        assertEquals(Ledger.RECENT_LIMIT, ledger.recent().size());
        assertEquals("d249", ledger.recent().getFirst().key());
        assertEquals(250, ledger.balance());
    }

    @Test
    void keyMemoryIsCappedOldestFirst() {
        Ledger ledger = new Ledger();
        for (int i = 0; i < Ledger.KEY_LIMIT + 5; i++) {
            ledger.apply(tx("k" + i, TransactionType.DEPOSIT, 1));
        }
        assertFalse(ledger.hasKey("k0"));
        assertFalse(ledger.hasKey("k4"));
        assertTrue(ledger.hasKey("k5"));
        assertTrue(ledger.hasKey("k" + (Ledger.KEY_LIMIT + 4)));
        assertEquals(Ledger.KEY_LIMIT, ledger.keys().size());
    }

    @Test
    void restoredLedgerKeepsKeysAndTotals() {
        Ledger original = new Ledger();
        original.apply(tx("visit-1", TransactionType.ADMISSION, 5));
        original.apply(tx("d1", TransactionType.DEPOSIT, 7));
        Ledger restored = new Ledger(original.balance(), original.totalAdmission(), original.totalDeposits(),
                original.totalWithdrawals(), original.recent(), original.keys());
        assertEquals(12, restored.balance());
        assertEquals(LedgerResult.DUPLICATE, restored.apply(tx("visit-1", TransactionType.ADMISSION, 5)));
        assertEquals(List.copyOf(original.recent()), List.copyOf(restored.recent()));
    }
}
