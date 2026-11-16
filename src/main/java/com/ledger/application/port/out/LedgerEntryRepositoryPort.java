package com.ledger.application.port.out;

import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.LedgerEntry;
import com.ledger.core.domain.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepositoryPort {

    List<LedgerEntry> findByTenantIdAndAccountId(TenantId tenantId, AccountId accountId, int page, int size);

    List<LedgerEntry> findByTenantIdAndAccountIdWithCursor(
            TenantId tenantId,
            AccountId accountId,
            Instant cursorCreatedAt,
            UUID cursorId,
            int limit
    );
}
