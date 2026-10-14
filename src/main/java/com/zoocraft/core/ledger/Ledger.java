package com.zoocraft.core.ledger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;

/**
 * A zoo's treasury. Every change goes through {@link #apply}, which enforces exactly-once:
 * a rejected transaction changes nothing, and a key is never applied twice.
 */
public final class Ledger {
    public static final int RECENT_LIMIT = 200;
    public static final int KEY_LIMIT = 10_000;

    private long balance;
    private long totalAdmission;
    private long totalDeposits;
    private long totalWithdrawals;
    private final Deque<Transaction> recent = new ArrayDeque<>();
    private final SequencedSet<String> keys = new LinkedHashSet<>();

    public Ledger() {
    }

    /** Restores persisted state. {@code recent} is newest first; {@code keys} is oldest first. */
    public Ledger(long balance, long totalAdmission, long totalDeposits, long totalWithdrawals,
                  Collection<Transaction> recent, Collection<String> keys) {
        this.balance = balance;
        this.totalAdmission = totalAdmission;
        this.totalDeposits = totalDeposits;
        this.totalWithdrawals = totalWithdrawals;
        this.recent.addAll(recent);
        this.keys.addAll(keys);
    }

    public LedgerResult apply(Transaction transaction) {
        if (transaction.amount() <= 0) {
            return LedgerResult.INVALID_AMOUNT;
        }
        if (keys.contains(transaction.key())) {
            return LedgerResult.DUPLICATE;
        }
        long newBalance = balance + transaction.signedAmount();
        if (newBalance < 0) {
            return LedgerResult.INSUFFICIENT_FUNDS;
        }
        balance = newBalance;
        switch (transaction.type()) {
            case DEPOSIT -> totalDeposits += transaction.amount();
            case WITHDRAW -> totalWithdrawals += transaction.amount();
            case ADMISSION -> totalAdmission += transaction.amount();
        }
        recent.addFirst(transaction);
        while (recent.size() > RECENT_LIMIT) {
            recent.removeLast();
        }
        keys.add(transaction.key());
        while (keys.size() > KEY_LIMIT) {
            keys.removeFirst();
        }
        return LedgerResult.APPLIED;
    }

    public long balance() {
        return balance;
    }

    public boolean hasKey(String key) {
        return keys.contains(key);
    }

    /** Newest first, at most {@link #RECENT_LIMIT}. */
    public List<Transaction> recent() {
        return List.copyOf(recent);
    }

    /** Remembered keys, oldest first. */
    public List<String> keys() {
        return new ArrayList<>(keys);
    }

    public long totalAdmission() {
        return totalAdmission;
    }

    public long totalDeposits() {
        return totalDeposits;
    }

    public long totalWithdrawals() {
        return totalWithdrawals;
    }
}
