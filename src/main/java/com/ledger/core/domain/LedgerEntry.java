package com.ledger.core.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LedgerEntry(
        UUID id,
        AccountId accountId,
        EntryType type,
        Money amount,
        Instant entryTimestamp,
        String description
) {

    public LedgerEntry {
        Objects.requireNonNull(id, "Entry id cannot be null");
        Objects.requireNonNull(accountId, "AccountId cannot be null");
        Objects.requireNonNull(type, "EntryType cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(entryTimestamp, "EntryTimestamp cannot be null");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("LedgerEntry amount must be strictly positive: " + amount);
        }
    }

    public static LedgerEntry debit(AccountId accountId, Money amount) {
        return debit(accountId, amount, Instant.now(), null);
    }

    public static LedgerEntry debit(AccountId accountId, Money amount, Instant timestamp) {
        return debit(accountId, amount, timestamp, null);
    }

    public static LedgerEntry debit(AccountId accountId, Money amount, Instant timestamp, String description) {
        return new LedgerEntry(UUID.randomUUID(), accountId, EntryType.DEBIT, amount, timestamp, description);
    }

    public static LedgerEntry credit(AccountId accountId, Money amount) {
        return credit(accountId, amount, Instant.now(), null);
    }

    public static LedgerEntry credit(AccountId accountId, Money amount, Instant timestamp) {
        return credit(accountId, amount, timestamp, null);
    }

    public static LedgerEntry credit(AccountId accountId, Money amount, Instant timestamp, String description) {
        return new LedgerEntry(UUID.randomUUID(), accountId, EntryType.CREDIT, amount, timestamp, description);
    }
}
