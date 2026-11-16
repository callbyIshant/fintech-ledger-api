package com.ledger.core.domain;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

public class Account {

    private final AccountId id;
    private final TenantId tenantId;
    private final Currency currency;
    private AccountStatus status;
    private Money balance;
    private final Instant createdAt;
    private Instant updatedAt;

    public Account(AccountId id, TenantId tenantId, Currency currency, Money initialBalance, AccountStatus status, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "AccountId cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "TenantId cannot be null");
        this.currency = Objects.requireNonNull(currency, "Currency cannot be null");
        this.status = Objects.requireNonNull(status, "AccountStatus cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "UpdatedAt cannot be null");

        Money initial = initialBalance != null ? initialBalance : Money.zero(currency);
        if (!initial.currency().equals(currency)) {
            throw new CurrencyMismatchException(currency, initial.currency());
        }
        if (initial.isNegative()) {
            throw new IllegalArgumentException("Initial account balance cannot be negative: " + initial);
        }
        this.balance = initial;
    }

    public static Account create(AccountId id, TenantId tenantId, Currency currency) {
        Instant now = Instant.now();
        return new Account(id, tenantId, currency, Money.zero(currency), AccountStatus.ACTIVE, now, now);
    }

    public static Account create(AccountId id, TenantId tenantId, Currency currency, Money initialBalance) {
        Instant now = Instant.now();
        return new Account(id, tenantId, currency, initialBalance, AccountStatus.ACTIVE, now, now);
    }

    public void debit(Money amount) {
        ensureActive();
        validateCurrency(amount);
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Debit amount must be strictly positive: " + amount);
        }
        if (this.balance.isLessThan(amount)) {
            throw new InsufficientFundsException(this.id, amount, this.balance);
        }
        this.balance = this.balance.subtract(amount);
        this.updatedAt = Instant.now();
    }

    public void credit(Money amount) {
        ensureActive();
        validateCurrency(amount);
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Credit amount must be strictly positive: " + amount);
        }
        this.balance = this.balance.add(amount);
        this.updatedAt = Instant.now();
    }

    public void apply(LedgerEntry entry) {
        Objects.requireNonNull(entry, "LedgerEntry cannot be null");
        if (!entry.accountId().equals(this.id)) {
            throw new IllegalArgumentException(String.format("Entry account ID %s does not match account %s",
                    entry.accountId(), this.id));
        }
        if (entry.type() == EntryType.DEBIT) {
            debit(entry.amount());
        } else if (entry.type() == EntryType.CREDIT) {
            credit(entry.amount());
        } else {
            throw new IllegalArgumentException("Unsupported EntryType: " + entry.type());
        }
    }

    public void freeze() {
        this.status = AccountStatus.FROZEN;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.status = AccountStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return this.status == AccountStatus.ACTIVE;
    }

    public boolean isFrozen() {
        return this.status == AccountStatus.FROZEN;
    }

    public AccountId getId() {
        return id;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public Currency getCurrency() {
        return currency;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Money getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private void ensureActive() {
        if (this.status == AccountStatus.FROZEN) {
            throw new AccountFrozenException(this.id);
        }
    }

    private void validateCurrency(Money money) {
        Objects.requireNonNull(money, "Money operand cannot be null");
        if (!this.currency.equals(money.currency())) {
            throw new CurrencyMismatchException(this.currency, money.currency());
        }
    }
}
