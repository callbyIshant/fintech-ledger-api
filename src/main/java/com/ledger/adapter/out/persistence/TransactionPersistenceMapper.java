package com.ledger.adapter.out.persistence;

import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.LedgerEntry;
import com.ledger.core.domain.Money;
import com.ledger.core.domain.TenantId;
import com.ledger.core.domain.Transaction;
import com.ledger.core.domain.TransactionId;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

@Component
public class TransactionPersistenceMapper {

    public Transaction toDomain(TransactionJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        List<LedgerEntry> domainEntries = new ArrayList<>();
        if (entity.getEntries() != null) {
            for (LedgerEntryJpaEntity entryEntity : entity.getEntries()) {
                domainEntries.add(toDomainEntry(entryEntity));
            }
        }

        return new Transaction(
                TransactionId.of(entity.getId()),
                TenantId.of(entity.getTenantId()),
                domainEntries,
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getFinalizedAt(),
                entity.getDescription(),
                entity.getVersion()
        );
    }

    public LedgerEntry toDomainEntry(LedgerEntryJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        Currency currency = Currency.getInstance(entity.getCurrency());
        Money amount = Money.of(entity.getAmount(), currency);

        return new LedgerEntry(
                entity.getId(),
                AccountId.of(entity.getAccountId()),
                entity.getType(),
                amount,
                entity.getCreatedAt(),
                entity.getDescription()
        );
    }

    public TransactionJpaEntity toJpaEntity(Transaction domain) {
        if (domain == null) {
            return null;
        }
        TransactionJpaEntity entity = new TransactionJpaEntity(
                domain.getId().value(),
                domain.getTenantId().value(),
                domain.getDescription(),
                domain.getStatus(),
                domain.getVersion(),
                domain.getCreatedAt(),
                domain.getFinalizedAt()
        );

        if (domain.getEntries() != null) {
            for (LedgerEntry entry : domain.getEntries()) {
                LedgerEntryJpaEntity entryEntity = toJpaEntry(entry, entity, domain.getTenantId().value());
                entity.addEntry(entryEntity);
            }
        }

        return entity;
    }

    public LedgerEntryJpaEntity toJpaEntry(LedgerEntry entry, TransactionJpaEntity transactionEntity, String tenantId) {
        if (entry == null) {
            return null;
        }
        return new LedgerEntryJpaEntity(
                entry.id(),
                transactionEntity,
                tenantId,
                entry.accountId().value(),
                entry.type(),
                entry.amount().amount(),
                entry.amount().currency().getCurrencyCode(),
                entry.description(),
                entry.entryTimestamp()
        );
    }
}
