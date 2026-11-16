package com.ledger.adapter.out.persistence;

import com.ledger.core.domain.Account;
import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.AccountStatus;
import com.ledger.core.domain.Money;
import com.ledger.core.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AccountPersistenceMapper Unit Tests")
class AccountPersistenceMapperTest {

    private AccountPersistenceMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new AccountPersistenceMapper();
    }

    @Test
    @DisplayName("Should map domain Account to AccountJpaEntity")
    void shouldMapDomainToEntity() {
        AccountId accountId = AccountId.random();
        TenantId tenantId = TenantId.of("tenant-123");
        Currency currency = Currency.getInstance("USD");
        Money balance = Money.of("500.25", "USD");
        Instant now = Instant.now();

        Account domain = new Account(accountId, tenantId, currency, balance, AccountStatus.ACTIVE, now, now, 2L);

        AccountJpaEntity entity = mapper.toJpaEntity(domain);

        assertNotNull(entity);
        assertEquals(accountId.value(), entity.getId());
        assertEquals("tenant-123", entity.getTenantId());
        assertEquals("USD", entity.getCurrency());
        assertEquals(new BigDecimal("500.25"), entity.getBalance());
        assertEquals(AccountStatus.ACTIVE, entity.getStatus());
        assertEquals(2L, entity.getVersion());
        assertEquals(now, entity.getCreatedAt());
        assertEquals(now, entity.getUpdatedAt());
    }

    @Test
    @DisplayName("Should map AccountJpaEntity to domain Account")
    void shouldMapEntityToDomain() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        AccountJpaEntity entity = new AccountJpaEntity(
                id,
                "tenant-456",
                "EUR",
                new BigDecimal("1250.75"),
                AccountStatus.FROZEN,
                5L,
                now,
                now
        );

        Account domain = mapper.toDomain(entity);

        assertNotNull(domain);
        assertEquals(id, domain.getId().value());
        assertEquals("tenant-456", domain.getTenantId().value());
        assertEquals(Currency.getInstance("EUR"), domain.getCurrency());
        assertEquals(Money.of("1250.75", "EUR"), domain.getBalance());
        assertEquals(AccountStatus.FROZEN, domain.getStatus());
        assertEquals(5L, domain.getVersion());
        assertEquals(now, domain.getCreatedAt());
        assertEquals(now, domain.getUpdatedAt());
    }

    @Test
    @DisplayName("Should return null for null input")
    void shouldReturnNullForNullInput() {
        assertNull(mapper.toDomain(null));
        assertNull(mapper.toJpaEntity(null));
    }
}
