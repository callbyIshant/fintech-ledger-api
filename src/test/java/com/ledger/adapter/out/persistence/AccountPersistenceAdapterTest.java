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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("AccountPersistenceAdapter Unit Tests")
class AccountPersistenceAdapterTest {

    private SpringDataAccountRepository accountRepository;
    private AccountPersistenceMapper mapper;
    private AccountPersistenceAdapter adapter;

    private AccountId accountId;
    private TenantId tenantId;
    private Currency usd;
    private Account account;
    private AccountJpaEntity entity;

    @BeforeEach
    void setUp() {
        accountRepository = mock(SpringDataAccountRepository.class);
        mapper = new AccountPersistenceMapper();
        adapter = new AccountPersistenceAdapter(accountRepository, mapper);

        accountId = AccountId.random();
        tenantId = TenantId.of("tenant-abc");
        usd = Currency.getInstance("USD");
        Instant now = Instant.now();

        account = new Account(accountId, tenantId, usd, Money.of("200.00", "USD"), AccountStatus.ACTIVE, now, now, 0L);
        entity = new AccountJpaEntity(accountId.value(), "tenant-abc", "USD", new BigDecimal("200.00"), AccountStatus.ACTIVE, 0L, now, now);
    }

    @Test
    @DisplayName("Should save account entity and return domain Account")
    void shouldSaveAccount() {
        when(accountRepository.save(any(AccountJpaEntity.class))).thenReturn(entity);

        Account saved = adapter.save(account);

        assertNotNull(saved);
        assertEquals(accountId, saved.getId());
        assertEquals(Money.of("200.00", "USD"), saved.getBalance());
        verify(accountRepository, times(1)).save(any(AccountJpaEntity.class));
    }

    @Test
    @DisplayName("Should find account by ID")
    void shouldFindById() {
        when(accountRepository.findById(accountId.value())).thenReturn(Optional.of(entity));

        Optional<Account> found = adapter.findById(accountId);

        assertTrue(found.isPresent());
        assertEquals(accountId, found.get().getId());
    }

    @Test
    @DisplayName("Should find account by tenant ID and account ID")
    void shouldFindByTenantIdAndAccountId() {
        when(accountRepository.findByTenantIdAndId("tenant-abc", accountId.value())).thenReturn(Optional.of(entity));

        Optional<Account> found = adapter.findByTenantIdAndAccountId(tenantId, accountId);

        assertTrue(found.isPresent());
        assertEquals(accountId, found.get().getId());
    }

    @Test
    @DisplayName("Should fetch accounts for update ordered deterministically by ID ascending")
    void shouldFetchAccountsForUpdateOrdered() {
        AccountId accountId2 = AccountId.random();
        AccountJpaEntity entity2 = new AccountJpaEntity(accountId2.value(), "tenant-abc", "USD", new BigDecimal("300.00"), AccountStatus.ACTIVE, 0L, Instant.now(), Instant.now());

        when(accountRepository.findByTenantIdAndIdInOrderByIdAscForUpdate(eq("tenant-abc"), anyCollection()))
                .thenReturn(List.of(entity, entity2));

        List<Account> accounts = adapter.findByTenantIdAndAccountIdsForUpdate(tenantId, List.of(accountId, accountId2));

        assertEquals(2, accounts.size());
        verify(accountRepository, times(1))
                .findByTenantIdAndIdInOrderByIdAscForUpdate(eq("tenant-abc"), anyCollection());
    }

    @Test
    @DisplayName("Should return empty list when account IDs collection is empty")
    void shouldReturnEmptyListForEmptyAccountIds() {
        List<Account> accounts = adapter.findByTenantIdAndAccountIdsForUpdate(tenantId, List.of());
        assertTrue(accounts.isEmpty());
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("Should check if account exists by ID")
    void shouldCheckExistsById() {
        when(accountRepository.existsById(accountId.value())).thenReturn(true);
        assertTrue(adapter.existsById(accountId));
    }
}
