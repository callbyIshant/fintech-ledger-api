package com.ledger.adapter.out.persistence;

import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.EntryType;
import com.ledger.core.domain.LedgerEntry;
import com.ledger.core.domain.Money;
import com.ledger.core.domain.TenantId;
import com.ledger.core.domain.Transaction;
import com.ledger.core.domain.TransactionId;
import com.ledger.core.domain.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TransactionPersistenceMapper Unit Tests")
class TransactionPersistenceMapperTest {

    private TransactionPersistenceMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TransactionPersistenceMapper();
    }

    @Test
    @DisplayName("Should map domain Transaction to TransactionJpaEntity with cascading entries")
    void shouldMapDomainToEntity() {
        TransactionId txId = TransactionId.random();
        TenantId tenantId = TenantId.of("tenant-tx");
        AccountId acc1 = AccountId.random();
        AccountId acc2 = AccountId.random();

        Transaction tx = Transaction.create(txId, tenantId, "Transfer");
        tx.addDebit(acc1, Money.of("100.00", "USD"));
        tx.addCredit(acc2, Money.of("100.00", "USD"));
        tx.finalizeTransaction();

        TransactionJpaEntity entity = mapper.toJpaEntity(tx);

        assertNotNull(entity);
        assertEquals(txId.value(), entity.getId());
        assertEquals("tenant-tx", entity.getTenantId());
        assertEquals("Transfer", entity.getDescription());
        assertEquals(TransactionStatus.POSTED, entity.getStatus());
        assertEquals(2, entity.getEntries().size());

        LedgerEntryJpaEntity entry1 = entity.getEntries().get(0);
        assertEquals(acc1.value(), entry1.getAccountId());
        assertEquals(EntryType.DEBIT, entry1.getType());
        assertEquals(new BigDecimal("100.00"), entry1.getAmount());
        assertEquals(entity, entry1.getTransaction());
    }

    @Test
    @DisplayName("Should map TransactionJpaEntity to domain Transaction")
    void shouldMapEntityToDomain() {
        UUID txId = UUID.randomUUID();
        UUID entry1Id = UUID.randomUUID();
        UUID entry2Id = UUID.randomUUID();
        UUID acc1 = UUID.randomUUID();
        UUID acc2 = UUID.randomUUID();
        Instant now = Instant.now();

        TransactionJpaEntity entity = new TransactionJpaEntity(
                txId,
                "tenant-tx",
                "Payment",
                TransactionStatus.POSTED,
                1L,
                now,
                now
        );

        LedgerEntryJpaEntity entry1 = new LedgerEntryJpaEntity(
                entry1Id, entity, "tenant-tx", acc1, EntryType.DEBIT,
                new BigDecimal("50.00"), "USD", "Debit side", now
        );
        LedgerEntryJpaEntity entry2 = new LedgerEntryJpaEntity(
                entry2Id, entity, "tenant-tx", acc2, EntryType.CREDIT,
                new BigDecimal("50.00"), "USD", "Credit side", now
        );
        entity.setEntries(List.of(entry1, entry2));

        Transaction domain = mapper.toDomain(entity);

        assertNotNull(domain);
        assertEquals(txId, domain.getId().value());
        assertEquals("tenant-tx", domain.getTenantId().value());
        assertEquals(TransactionStatus.POSTED, domain.getStatus());
        assertEquals(1L, domain.getVersion());
        assertEquals(2, domain.getEntries().size());
        assertTrue(domain.isBalanced());
        assertEquals(Money.of("50.00", "USD"), domain.getTotalDebits());
        assertEquals(Money.of("50.00", "USD"), domain.getTotalCredits());
    }

    @Test
    @DisplayName("Should return null for null input")
    void shouldReturnNullForNullInput() {
        assertNull(mapper.toDomain(null));
        assertNull(mapper.toJpaEntity(null));
        assertNull(mapper.toDomainEntry(null));
        assertNull(mapper.toJpaEntry(null, null, "tenant"));
    }
}
