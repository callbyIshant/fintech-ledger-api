package com.ledger.application.service;

import com.ledger.application.port.in.GetLedgerHistoryUseCase;
import com.ledger.application.port.out.AccountRepositoryPort;
import com.ledger.application.port.out.LedgerEntryRepositoryPort;
import com.ledger.core.domain.AccountNotFoundException;
import com.ledger.core.domain.LedgerEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class LedgerHistoryService implements GetLedgerHistoryUseCase {

    private final AccountRepositoryPort accountRepositoryPort;
    private final LedgerEntryRepositoryPort ledgerEntryRepositoryPort;

    public LedgerHistoryService(AccountRepositoryPort accountRepositoryPort,
                                LedgerEntryRepositoryPort ledgerEntryRepositoryPort) {
        this.accountRepositoryPort = accountRepositoryPort;
        this.ledgerEntryRepositoryPort = ledgerEntryRepositoryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntryResult> getLedgerHistory(GetLedgerHistoryQuery query) {
        Objects.requireNonNull(query, "GetLedgerHistoryQuery cannot be null");

        // Verify account exists and belongs to tenant
        accountRepositoryPort.findByTenantIdAndAccountId(query.tenantId(), query.accountId())
                .orElseThrow(() -> new AccountNotFoundException(query.tenantId(), query.accountId()));

        List<LedgerEntry> entries = ledgerEntryRepositoryPort.findByTenantIdAndAccountId(
                query.tenantId(),
                query.accountId(),
                query.page(),
                query.size()
        );

        return entries.stream()
                .map(entry -> new LedgerEntryResult(
                        entry.id(),
                        entry.accountId(),
                        entry.type(),
                        entry.amount(),
                        entry.entryTimestamp(),
                        entry.description()
                ))
                .toList();
    }
}
