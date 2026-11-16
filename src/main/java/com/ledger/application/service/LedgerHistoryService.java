package com.ledger.application.service;

import com.ledger.application.port.in.GetLedgerHistoryUseCase;
import com.ledger.application.port.out.AccountRepositoryPort;
import com.ledger.application.port.out.LedgerEntryRepositoryPort;
import com.ledger.core.domain.AccountNotFoundException;
import com.ledger.core.domain.LedgerEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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

        accountRepositoryPort.findByTenantIdAndAccountId(query.tenantId(), query.accountId())
                .orElseThrow(() -> new AccountNotFoundException(query.tenantId(), query.accountId()));

        List<LedgerEntry> entries = ledgerEntryRepositoryPort.findByTenantIdAndAccountId(
                query.tenantId(),
                query.accountId(),
                query.page(),
                query.size()
        );

        return entries.stream()
                .map(this::toResult)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResult<LedgerEntryResult> getLedgerHistoryWithCursor(GetLedgerHistoryCursorQuery query) {
        Objects.requireNonNull(query, "GetLedgerHistoryCursorQuery cannot be null");

        accountRepositoryPort.findByTenantIdAndAccountId(query.tenantId(), query.accountId())
                .orElseThrow(() -> new AccountNotFoundException(query.tenantId(), query.accountId()));

        Instant cursorCreatedAt = null;
        UUID cursorId = null;

        if (query.cursor() != null && !query.cursor().isBlank()) {
            try {
                String decoded = new String(Base64.getUrlDecoder().decode(query.cursor()), StandardCharsets.UTF_8);
                String[] parts = decoded.split(":");
                cursorCreatedAt = Instant.ofEpochMilli(Long.parseLong(parts[0]));
                cursorId = UUID.fromString(parts[1]);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid cursor format: " + query.cursor(), e);
            }
        }

        // Fetch limit + 1 to determine if more items exist
        int fetchSize = query.limit() + 1;
        List<LedgerEntry> entries = ledgerEntryRepositoryPort.findByTenantIdAndAccountIdWithCursor(
                query.tenantId(),
                query.accountId(),
                cursorCreatedAt,
                cursorId,
                fetchSize
        );

        boolean hasMore = entries.size() > query.limit();
        List<LedgerEntry> pageItems = hasMore ? entries.subList(0, query.limit()) : entries;

        String nextCursor = null;
        if (hasMore && !pageItems.isEmpty()) {
            LedgerEntry lastItem = pageItems.get(pageItems.size() - 1);
            String rawCursor = lastItem.entryTimestamp().toEpochMilli() + ":" + lastItem.id();
            nextCursor = Base64.getUrlEncoder().withoutPadding().encodeToString(rawCursor.getBytes(StandardCharsets.UTF_8));
        }

        List<LedgerEntryResult> resultItems = pageItems.stream()
                .map(this::toResult)
                .toList();

        return new CursorPageResult<>(resultItems, nextCursor, hasMore);
    }

    private LedgerEntryResult toResult(LedgerEntry entry) {
        return new LedgerEntryResult(
                entry.id(),
                entry.accountId(),
                entry.type(),
                entry.amount(),
                entry.entryTimestamp(),
                entry.description()
        );
    }
}
