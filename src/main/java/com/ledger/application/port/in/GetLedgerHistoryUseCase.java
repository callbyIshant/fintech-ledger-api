package com.ledger.application.port.in;

import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.EntryType;
import com.ledger.core.domain.Money;
import com.ledger.core.domain.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public interface GetLedgerHistoryUseCase {

    record GetLedgerHistoryQuery(
            TenantId tenantId,
            AccountId accountId,
            int page,
            int size
    ) {
        public GetLedgerHistoryQuery {
            Objects.requireNonNull(tenantId, "tenantId cannot be null");
            Objects.requireNonNull(accountId, "accountId cannot be null");
            if (page < 0) throw new IllegalArgumentException("page cannot be negative");
            if (size <= 0) throw new IllegalArgumentException("size must be positive");
        }
    }

    record GetLedgerHistoryCursorQuery(
            TenantId tenantId,
            AccountId accountId,
            String cursor,
            int limit
    ) {
        public GetLedgerHistoryCursorQuery {
            Objects.requireNonNull(tenantId, "tenantId cannot be null");
            Objects.requireNonNull(accountId, "accountId cannot be null");
            if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        }
    }

    record LedgerEntryResult(
            UUID id,
            AccountId accountId,
            EntryType type,
            Money amount,
            Instant entryTimestamp,
            String description
    ) {}

    record CursorPageResult<T>(
            List<T> items,
            String nextCursor,
            boolean hasMore
    ) {}

    List<LedgerEntryResult> getLedgerHistory(GetLedgerHistoryQuery query);

    CursorPageResult<LedgerEntryResult> getLedgerHistoryWithCursor(GetLedgerHistoryCursorQuery query);
}
