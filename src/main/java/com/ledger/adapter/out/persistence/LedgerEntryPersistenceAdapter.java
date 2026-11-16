package com.ledger.adapter.out.persistence;

import com.ledger.application.port.out.LedgerEntryRepositoryPort;
import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.LedgerEntry;
import com.ledger.core.domain.TenantId;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class LedgerEntryPersistenceAdapter implements LedgerEntryRepositoryPort {

    private final SpringDataLedgerEntryRepository repository;
    private final TransactionPersistenceMapper mapper;

    public LedgerEntryPersistenceAdapter(SpringDataLedgerEntryRepository repository,
                                         TransactionPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<LedgerEntry> findByTenantIdAndAccountId(TenantId tenantId, AccountId accountId, int page, int size) {
        Objects.requireNonNull(tenantId, "TenantId cannot be null");
        Objects.requireNonNull(accountId, "AccountId cannot be null");

        PageRequest pageRequest = PageRequest.of(page, size);
        List<LedgerEntryJpaEntity> entities = repository
                .findByTenantIdAndAccountIdOrderByCreatedAtDesc(tenantId.value(), accountId.value(), pageRequest);

        return entities.stream()
                .map(mapper::toDomainEntry)
                .toList();
    }

    @Override
    public List<LedgerEntry> findByTenantIdAndAccountIdWithCursor(
            TenantId tenantId,
            AccountId accountId,
            Instant cursorCreatedAt,
            UUID cursorId,
            int limit
    ) {
        Objects.requireNonNull(tenantId, "TenantId cannot be null");
        Objects.requireNonNull(accountId, "AccountId cannot be null");

        PageRequest pageRequest = PageRequest.of(0, limit);
        List<LedgerEntryJpaEntity> entities;
        if (cursorCreatedAt == null || cursorId == null) {
            entities = repository.findByTenantIdAndAccountIdOrderByCreatedAtDesc(
                    tenantId.value(),
                    accountId.value(),
                    pageRequest
            );
        } else {
            entities = repository.findByTenantIdAndAccountIdWithCursor(
                    tenantId.value(),
                    accountId.value(),
                    cursorCreatedAt,
                    cursorId,
                    pageRequest
            );
        }

        return entities.stream()
                .map(mapper::toDomainEntry)
                .toList();
    }
}
