package com.ledger.core.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Transaction Domain Model Tests")
class TransactionTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");

    private TransactionId transactionId;
    private TenantId tenantId;
    private AccountId sourceAccount;
    private AccountId destinationAccount;
    private AccountId feeAccount;

    @BeforeEach
    void setUp() {
        transactionId = TransactionId.random();
        tenantId = TenantId.of("tenant-xyz");
        sourceAccount = AccountId.random();
        destinationAccount = AccountId.random();
        feeAccount = AccountId.random();
    }

    @Test
    @DisplayName("Should successfully finalize a balanced transaction (Total Debits == Total Credits)")
    void shouldFinalizeBalancedTransaction() {
        Transaction tx = Transaction.create(transactionId, tenantId, "P2P Transfer");

        tx.addDebit(sourceAccount, Money.of("100.00", "USD"));
        tx.addCredit(destinationAccount, Money.of("100.00", "USD"));

        assertTrue(tx.isBalanced());
        assertEquals(Money.of("100.00", "USD"), tx.getTotalDebits());
        assertEquals(Money.of("100.00", "USD"), tx.getTotalCredits());
        assertEquals(TransactionStatus.DRAFT, tx.getStatus());

        tx.finalizeTransaction();

        assertEquals(TransactionStatus.POSTED, tx.getStatus());
        assertTrue(tx.isFinalized());
        assertNotNull(tx.getFinalizedAt());
    }

    @Test
    @DisplayName("Should finalize a multi-legged balanced transaction")
    void shouldFinalizeMultiLeggedTransaction() {
        Transaction tx = Transaction.create(transactionId, tenantId, "Transfer with fee");

        // Debit $100 from sender
        tx.addDebit(sourceAccount, Money.of("100.00", "USD"));

        // Credit $98 to recipient, Credit $2 to platform fee account
        tx.addCredit(destinationAccount, Money.of("98.00", "USD"));
        tx.addCredit(feeAccount, Money.of("2.00", "USD"));

        assertTrue(tx.isBalanced());
        assertEquals(Money.of("100.00", "USD"), tx.getTotalDebits());
        assertEquals(Money.of("100.00", "USD"), tx.getTotalCredits());

        assertDoesNotThrow(tx::finalizeTransaction);
        assertTrue(tx.isFinalized());
    }

    @Test
    @DisplayName("Should throw UnbalancedTransactionException when debits do not equal credits")
    void shouldRejectUnbalancedTransaction() {
        Transaction tx = Transaction.create(transactionId, tenantId, "Unbalanced transfer");

        tx.addDebit(sourceAccount, Money.of("100.00", "USD"));
        tx.addCredit(destinationAccount, Money.of("95.00", "USD"));

        assertFalse(tx.isBalanced());

        UnbalancedTransactionException ex = assertThrows(UnbalancedTransactionException.class,
                tx::finalizeTransaction);

        assertEquals(transactionId, ex.getTransactionId());
        assertEquals(Money.of("100.00", "USD"), ex.getTotalDebits());
        assertEquals(Money.of("95.00", "USD"), ex.getTotalCredits());
        assertEquals(TransactionStatus.DRAFT, tx.getStatus());
    }

    @Test
    @DisplayName("Should throw UnbalancedTransactionException when transaction has no entries")
    void shouldRejectEmptyTransaction() {
        Transaction tx = Transaction.create(transactionId, tenantId, "Empty tx");

        assertFalse(tx.isBalanced());
        assertThrows(UnbalancedTransactionException.class, tx::finalizeTransaction);
    }

    @Test
    @DisplayName("Should throw UnbalancedTransactionException when transaction has only debits or only credits")
    void shouldRejectOneSidedTransaction() {
        Transaction txDebitsOnly = Transaction.create(TransactionId.random(), tenantId, "Debits only");
        txDebitsOnly.addDebit(sourceAccount, Money.of("50.00", "USD"));
        assertThrows(UnbalancedTransactionException.class, txDebitsOnly::finalizeTransaction);

        Transaction txCreditsOnly = Transaction.create(TransactionId.random(), tenantId, "Credits only");
        txCreditsOnly.addCredit(destinationAccount, Money.of("50.00", "USD"));
        assertThrows(UnbalancedTransactionException.class, txCreditsOnly::finalizeTransaction);
    }

    @Test
    @DisplayName("Should prevent modifying an already finalized transaction")
    void shouldPreventModifyingFinalizedTransaction() {
        Transaction tx = Transaction.create(transactionId, tenantId, "Valid transfer");
        tx.addDebit(sourceAccount, Money.of("50.00", "USD"));
        tx.addCredit(destinationAccount, Money.of("50.00", "USD"));
        tx.finalizeTransaction();

        // Adding entry must fail
        assertThrows(IllegalStateException.class, () ->
                tx.addCredit(feeAccount, Money.of("5.00", "USD")));

        // Re-finalizing must fail
        assertThrows(IllegalStateException.class, tx::finalizeTransaction);
        assertThrows(IllegalStateException.class, tx::post);
    }

    @Test
    @DisplayName("Should throw CurrencyMismatchException if entry currency differs from transaction currency")
    void shouldRejectMismatchedCurrencyEntry() {
        Transaction tx = Transaction.create(transactionId, tenantId, "Multi-currency attempt");
        tx.addDebit(sourceAccount, Money.of("100.00", "USD"));

        assertThrows(CurrencyMismatchException.class, () ->
                tx.addCredit(destinationAccount, Money.of("100.00", "EUR")));
    }

    @Test
    @DisplayName("Should protect entries list from external modification")
    void shouldProvideUnmodifiableEntriesList() {
        Transaction tx = Transaction.create(transactionId, tenantId, "Tx");
        tx.addDebit(sourceAccount, Money.of("100.00", "USD"));

        List<LedgerEntry> entries = tx.getEntries();
        assertThrows(UnsupportedOperationException.class, () ->
                entries.add(LedgerEntry.credit(destinationAccount, Money.of("100.00", "USD"))));
    }
}
