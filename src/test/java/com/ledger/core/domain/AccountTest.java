package com.ledger.core.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Account Domain Model Tests")
class AccountTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");

    private AccountId accountId;
    private TenantId tenantId;
    private Account account;

    @BeforeEach
    void setUp() {
        accountId = AccountId.random();
        tenantId = TenantId.of("tenant-alpha");
        account = Account.create(accountId, tenantId, USD, Money.of("100.00", "USD"));
    }

    @Test
    @DisplayName("Should create account with active status and positive balance")
    void shouldCreateAccountSuccessfully() {
        assertEquals(accountId, account.getId());
        assertEquals(tenantId, account.getTenantId());
        assertEquals(USD, account.getCurrency());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals(Money.of("100.00", "USD"), account.getBalance());
        assertTrue(account.isActive());
        assertFalse(account.isFrozen());
        assertNotNull(account.getCreatedAt());
        assertNotNull(account.getUpdatedAt());
    }

    @Test
    @DisplayName("Should reject negative initial balance")
    void shouldRejectNegativeInitialBalance() {
        Money negativeMoney = Money.of("-50.00", "USD");
        assertThrows(IllegalArgumentException.class, () ->
                Account.create(AccountId.random(), tenantId, USD, negativeMoney));
    }

    @Test
    @DisplayName("Should reject initial balance with mismatched currency")
    void shouldRejectMismatchedInitialCurrency() {
        Money eurMoney = Money.of("50.00", "EUR");
        assertThrows(CurrencyMismatchException.class, () ->
                Account.create(AccountId.random(), tenantId, USD, eurMoney));
    }

    @Test
    @DisplayName("Should credit account and increase balance")
    void shouldCreditAccount() {
        account.credit(Money.of("50.25", "USD"));
        assertEquals(Money.of("150.25", "USD"), account.getBalance());
    }

    @Test
    @DisplayName("Should reject credit of zero or negative amount")
    void shouldRejectNonPositiveCredit() {
        assertThrows(IllegalArgumentException.class, () -> account.credit(Money.zero(USD)));
        assertThrows(IllegalArgumentException.class, () -> account.credit(Money.of("-10.00", "USD")));
    }

    @Test
    @DisplayName("Should debit account and decrease balance")
    void shouldDebitAccount() {
        account.debit(Money.of("40.00", "USD"));
        assertEquals(Money.of("60.00", "USD"), account.getBalance());
    }

    @Test
    @DisplayName("Should reject debit of zero or negative amount")
    void shouldRejectNonPositiveDebit() {
        assertThrows(IllegalArgumentException.class, () -> account.debit(Money.zero(USD)));
        assertThrows(IllegalArgumentException.class, () -> account.debit(Money.of("-10.00", "USD")));
    }

    @Test
    @DisplayName("Should reject debit when balance is insufficient (negative balance prevention)")
    void shouldRejectDebitExceedingBalance() {
        Money excessAmount = Money.of("100.01", "USD");
        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class, () ->
                account.debit(excessAmount));

        assertEquals(accountId, ex.getAccountId());
        assertEquals(excessAmount, ex.getRequestedAmount());
        assertEquals(Money.of("100.00", "USD"), ex.getCurrentBalance());
        assertEquals(Money.of("100.00", "USD"), account.getBalance());
    }

    @Test
    @DisplayName("Should throw CurrencyMismatchException on debit or credit with wrong currency")
    void shouldRejectWrongCurrencyOperations() {
        Money eurAmount = Money.of("20.00", "EUR");

        assertThrows(CurrencyMismatchException.class, () -> account.credit(eurAmount));
        assertThrows(CurrencyMismatchException.class, () -> account.debit(eurAmount));
    }

    @Test
    @DisplayName("Should reject operations when account is FROZEN")
    void shouldRejectOperationsWhenFrozen() {
        account.freeze();
        assertTrue(account.isFrozen());
        assertFalse(account.isActive());

        assertThrows(AccountFrozenException.class, () -> account.credit(Money.of("10.00", "USD")));
        assertThrows(AccountFrozenException.class, () -> account.debit(Money.of("10.00", "USD")));

        LedgerEntry entry = LedgerEntry.credit(accountId, Money.of("10.00", "USD"));
        assertThrows(AccountFrozenException.class, () -> account.apply(entry));
    }

    @Test
    @DisplayName("Should allow operations again after unfreezing (activating)")
    void shouldAllowOperationsAfterReactivating() {
        account.freeze();
        assertTrue(account.isFrozen());

        account.activate();
        assertTrue(account.isActive());

        assertDoesNotThrow(() -> account.credit(Money.of("25.00", "USD")));
        assertEquals(Money.of("125.00", "USD"), account.getBalance());
    }

    @Test
    @DisplayName("Should apply LedgerEntry DEBIT and CREDIT correctly")
    void shouldApplyLedgerEntries() {
        LedgerEntry creditEntry = LedgerEntry.credit(accountId, Money.of("30.00", "USD"));
        account.apply(creditEntry);
        assertEquals(Money.of("130.00", "USD"), account.getBalance());

        LedgerEntry debitEntry = LedgerEntry.debit(accountId, Money.of("50.00", "USD"));
        account.apply(debitEntry);
        assertEquals(Money.of("80.00", "USD"), account.getBalance());
    }

    @Test
    @DisplayName("Should reject LedgerEntry belonging to a different account")
    void shouldRejectLedgerEntryForDifferentAccount() {
        AccountId otherAccountId = AccountId.random();
        LedgerEntry entry = LedgerEntry.credit(otherAccountId, Money.of("10.00", "USD"));

        assertThrows(IllegalArgumentException.class, () -> account.apply(entry));
    }
}
