package com.ledger.application.port.out;

import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.LedgerEntry;
import com.ledger.core.domain.TenantId;

import java.util.List;

public interface LedgerEntryRepositoryPort {

    List<LedgerEntry> findByTenantIdAndAccountId(TenantId tenantId, AccountId accountId, int page, int size);
}
