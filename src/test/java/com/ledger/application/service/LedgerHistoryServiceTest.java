package com.ledger.application.service;

import com.ledger.application.port.in.GetLedgerHistoryUseCase.GetLedgerHistoryQuery;
import com.ledger.application.port.in.GetLedgerHistoryUseCase.LedgerEntryResult;
import com.ledger.application.port.out.AccountRepositoryPort;
import com.ledger.application.port.out.LedgerEntryRepositoryPort;
import com.ledger.core.domain.Account;
import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.AccountNotFoundException;
import com.ledger.core.domain.AccountStatus;
import com.ledger.core.domain.EntryType;
import com.ledger.core.domain.LedgerEntry;
import com.ledger.core.domain.Money;
import com.ledger.core.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("LedgerHistoryService Unit Tests")
class LedgerHistoryServiceTest {

    private AccountRepositoryPort accountRepositoryPort;
    private LedgerEntryRepositoryPort ledgerEntryRepositoryPort;
    private LedgerHistoryService ledgerHistoryService;

    private TenantId tenantId;
    private AccountId accountId;
    private Account account;

    @BeforeEach
    void setUp() {
        accountRepositoryPort = mock(AccountRepositoryPort.class);
        ledgerEntryRepositoryPort = mock(LedgerEntryRepositoryPort.class);
        ledgerHistoryService = new LedgerHistoryService(accountRepositoryPort, ledgerEntryRepositoryPort);

        tenantId = TenantId.of("tenant-hist");
        accountId = AccountId.random();
        account = new Account(accountId, tenantId, Currency.getInstance("USD"), Money.of("500.00", "USD"), AccountStatus.ACTIVE, Instant.now(), Instant.now(), 0L);
    }

    @Test
    @DisplayName("Should retrieve ledger history for existing account and tenant")
    void shouldRetrieveLedgerHistory() {
        when(accountRepositoryPort.findByTenantIdAndAccountId(tenantId, accountId))
                .thenReturn(Optional.of(account));

        LedgerEntry entry1 = LedgerEntry.credit(accountId, Money.of("200.00", "USD"), Instant.now(), "Deposit");
        LedgerEntry entry2 = LedgerEntry.debit(accountId, Money.of("50.00", "USD"), Instant.now(), "Transfer out");

        when(ledgerEntryRepositoryPort.findByTenantIdAndAccountId(tenantId, accountId, 0, 10))
                .thenReturn(List.of(entry1, entry2));

        GetLedgerHistoryQuery query = new GetLedgerHistoryQuery(tenantId, accountId, 0, 10);
        List<LedgerEntryResult> history = ledgerHistoryService.getLedgerHistory(query);

        assertEquals(2, history.size());
        assertEquals(EntryType.CREDIT, history.get(0).type());
        assertEquals(Money.of("200.00", "USD"), history.get(0).amount());
        assertEquals(EntryType.DEBIT, history.get(1).type());
        assertEquals(Money.of("50.00", "USD"), history.get(1).amount());
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException when account does not exist for tenant")
    void shouldThrowWhenAccountNotFound() {
        when(accountRepositoryPort.findByTenantIdAndAccountId(tenantId, accountId))
                .thenReturn(Optional.empty());

        GetLedgerHistoryQuery query = new GetLedgerHistoryQuery(tenantId, accountId, 0, 10);

        assertThrows(AccountNotFoundException.class, () -> ledgerHistoryService.getLedgerHistory(query));
        verifyNoInteractions(ledgerEntryRepositoryPort);
    }
}
