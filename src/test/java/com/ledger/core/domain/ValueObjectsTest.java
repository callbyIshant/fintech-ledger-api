package com.ledger.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Identifier & LedgerEntry Value Object Tests")
class ValueObjectsTest {

    @Test
    @DisplayName("AccountId: valid creation, equality, and null/invalid checks")
    void shouldValidateAccountId() {
        UUID uuid = UUID.randomUUID();
        AccountId id1 = AccountId.of(uuid);
        AccountId id2 = AccountId.of(uuid.toString());

        assertEquals(id1, id2);
        assertEquals(uuid.toString(), id1.toString());
        assertNotNull(AccountId.random());

        assertThrows(NullPointerException.class, () -> AccountId.of((UUID) null));
        assertThrows(NullPointerException.class, () -> AccountId.of((String) null));
        assertThrows(IllegalArgumentException.class, () -> AccountId.of("not-a-valid-uuid"));
    }

    @Test
    @DisplayName("TenantId: valid creation, blank rejection, and null checks")
    void shouldValidateTenantId() {
        TenantId tenant1 = TenantId.of("tenant-corp-1");
        TenantId tenant2 = TenantId.of("tenant-corp-1");

        assertEquals(tenant1, tenant2);
        assertEquals("tenant-corp-1", tenant1.toString());

        UUID uuid = UUID.randomUUID();
        assertEquals(uuid.toString(), TenantId.of(uuid).value());

        assertThrows(NullPointerException.class, () -> TenantId.of((String) null));
        assertThrows(IllegalArgumentException.class, () -> TenantId.of(""));
        assertThrows(IllegalArgumentException.class, () -> TenantId.of("   "));
    }

    @Test
    @DisplayName("TransactionId: valid creation, equality, and null/invalid checks")
    void shouldValidateTransactionId() {
        UUID uuid = UUID.randomUUID();
        TransactionId tx1 = TransactionId.of(uuid);
        TransactionId tx2 = TransactionId.of(uuid.toString());

        assertEquals(tx1, tx2);
        assertEquals(uuid.toString(), tx1.toString());
        assertNotNull(TransactionId.random());

        assertThrows(NullPointerException.class, () -> TransactionId.of((UUID) null));
        assertThrows(NullPointerException.class, () -> TransactionId.of((String) null));
        assertThrows(IllegalArgumentException.class, () -> TransactionId.of("invalid-uuid"));
    }

    @Test
    @DisplayName("LedgerEntry: factory methods, validations, and immutability")
    void shouldValidateLedgerEntry() {
        AccountId accountId = AccountId.random();
        Money amount = Money.of("75.50", "USD");
        Instant now = Instant.now();

        LedgerEntry debitEntry = LedgerEntry.debit(accountId, amount, now, "Payment");
        assertEquals(accountId, debitEntry.accountId());
        assertEquals(EntryType.DEBIT, debitEntry.type());
        assertEquals(amount, debitEntry.amount());
        assertEquals(now, debitEntry.entryTimestamp());
        assertEquals("Payment", debitEntry.description());

        LedgerEntry creditEntry = LedgerEntry.credit(accountId, amount);
        assertEquals(EntryType.CREDIT, creditEntry.type());
        assertNotNull(creditEntry.entryTimestamp());

        // Amount must be positive
        assertThrows(IllegalArgumentException.class, () ->
                LedgerEntry.debit(accountId, Money.zero("USD")));
        assertThrows(IllegalArgumentException.class, () ->
                LedgerEntry.debit(accountId, Money.of("-10.00", "USD")));
    }
}
