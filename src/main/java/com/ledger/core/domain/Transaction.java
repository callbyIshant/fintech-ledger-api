package com.ledger.core.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

public class Transaction {

    private final TransactionId id;
    private final TenantId tenantId;
    private final List<LedgerEntry> entries;
    private TransactionStatus status;
    private final Instant createdAt;
    private Instant finalizedAt;
    private final String description;
    private final Long version;

    public Transaction(TransactionId id, TenantId tenantId, String description) {
        this(id, tenantId, new ArrayList<>(), TransactionStatus.DRAFT, Instant.now(), null, description, 0L);
    }

    public Transaction(TransactionId id, TenantId tenantId, List<LedgerEntry> entries, TransactionStatus status, Instant createdAt, Instant finalizedAt, String description) {
        this(id, tenantId, entries, status, createdAt, finalizedAt, description, 0L);
    }

    public Transaction(TransactionId id, TenantId tenantId, List<LedgerEntry> entries, TransactionStatus status, Instant createdAt, Instant finalizedAt, String description, Long version) {
        this.id = Objects.requireNonNull(id, "TransactionId cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "TenantId cannot be null");
        this.entries = new ArrayList<>(Objects.requireNonNull(entries, "Entries cannot be null"));
        this.status = Objects.requireNonNull(status, "TransactionStatus cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt cannot be null");
        this.finalizedAt = finalizedAt;
        this.description = description;
        this.version = version != null ? version : 0L;

        validateEntriesCurrency();
    }

    public static Transaction create(TransactionId id, TenantId tenantId, String description) {
        return new Transaction(id, tenantId, description);
    }

    public static Transaction create(TransactionId id, TenantId tenantId, List<LedgerEntry> entries, String description) {
        return new Transaction(id, tenantId, entries, TransactionStatus.DRAFT, Instant.now(), null, description);
    }

    public void addEntry(LedgerEntry entry) {
        Objects.requireNonNull(entry, "LedgerEntry cannot be null");
        if (this.status == TransactionStatus.POSTED) {
            throw new IllegalStateException("Cannot add entry to a finalized transaction: " + id);
        }
        if (!this.entries.isEmpty()) {
            Currency expectedCurrency = getCurrency();
            if (!entry.amount().currency().equals(expectedCurrency)) {
                throw new CurrencyMismatchException(expectedCurrency, entry.amount().currency());
            }
        }
        this.entries.add(entry);
    }

    public void addDebit(AccountId accountId, Money amount) {
        addEntry(LedgerEntry.debit(accountId, amount));
    }

    public void addCredit(AccountId accountId, Money amount) {
        addEntry(LedgerEntry.credit(accountId, amount));
    }

    public void finalizeTransaction() {
        if (this.status == TransactionStatus.POSTED) {
            throw new IllegalStateException("Transaction is already finalized: " + id);
        }
        if (this.entries.isEmpty()) {
            throw new UnbalancedTransactionException("Transaction has no entries to finalize: " + id);
        }

        Money totalDebits = getTotalDebits();
        Money totalCredits = getTotalCredits();

        if (!totalDebits.isPositive() || !totalCredits.isPositive() || !totalDebits.equals(totalCredits)) {
            throw new UnbalancedTransactionException(this.id, totalDebits, totalCredits);
        }

        this.status = TransactionStatus.POSTED;
        this.finalizedAt = Instant.now();
    }

    public void post() {
        finalizeTransaction();
    }

    public boolean isBalanced() {
        if (entries.isEmpty()) {
            return false;
        }
        Money debits = getTotalDebits();
        Money credits = getTotalCredits();
        return debits.isPositive() && credits.isPositive() && debits.equals(credits);
    }

    public Money getTotalDebits() {
        Currency currency = getCurrency();
        Money total = Money.zero(currency);
        for (LedgerEntry entry : entries) {
            if (entry.type() == EntryType.DEBIT) {
                total = total.add(entry.amount());
            }
        }
        return total;
    }

    public Money getTotalCredits() {
        Currency currency = getCurrency();
        Money total = Money.zero(currency);
        for (LedgerEntry entry : entries) {
            if (entry.type() == EntryType.CREDIT) {
                total = total.add(entry.amount());
            }
        }
        return total;
    }

    public Currency getCurrency() {
        if (entries.isEmpty()) {
            return Currency.getInstance("USD");
        }
        return entries.get(0).amount().currency();
    }

    public boolean isFinalized() {
        return this.status == TransactionStatus.POSTED;
    }

    public TransactionId getId() {
        return id;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public List<LedgerEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public String getDescription() {
        return description;
    }

    public Long getVersion() {
        return version;
    }

    private void validateEntriesCurrency() {
        if (entries.isEmpty()) {
            return;
        }
        Currency base = entries.get(0).amount().currency();
        for (LedgerEntry entry : entries) {
            if (!entry.amount().currency().equals(base)) {
                throw new CurrencyMismatchException(base, entry.amount().currency());
            }
        }
    }
}
