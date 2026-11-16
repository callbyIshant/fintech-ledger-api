package com.ledger.adapter.out.persistence;

import com.ledger.application.port.out.TransactionRepositoryPort;
import com.ledger.core.domain.TenantId;
import com.ledger.core.domain.Transaction;
import com.ledger.core.domain.TransactionId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class TransactionPersistenceAdapter implements TransactionRepositoryPort {

    private final SpringDataTransactionRepository springDataTransactionRepository;
    private final TransactionPersistenceMapper transactionPersistenceMapper;

    public TransactionPersistenceAdapter(SpringDataTransactionRepository springDataTransactionRepository,
                                         TransactionPersistenceMapper transactionPersistenceMapper) {
        this.springDataTransactionRepository = springDataTransactionRepository;
        this.transactionPersistenceMapper = transactionPersistenceMapper;
    }

    @Override
    public Transaction save(Transaction transaction) {
        Objects.requireNonNull(transaction, "Transaction cannot be null");
        TransactionJpaEntity entity = transactionPersistenceMapper.toJpaEntity(transaction);
        if (transaction.getVersion() == null) {
            entity.setNew(true);
        } else {
            entity.setNew(false);
        }
        TransactionJpaEntity saved = springDataTransactionRepository.save(entity);
        return transactionPersistenceMapper.toDomain(saved);
    }

    @Override
    public Optional<Transaction> findById(TransactionId transactionId) {
        Objects.requireNonNull(transactionId, "TransactionId cannot be null");
        return springDataTransactionRepository.findById(transactionId.value())
                .map(transactionPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Transaction> findByTenantIdAndTransactionId(TenantId tenantId, TransactionId transactionId) {
        Objects.requireNonNull(tenantId, "TenantId cannot be null");
        Objects.requireNonNull(transactionId, "TransactionId cannot be null");
        return springDataTransactionRepository.findByTenantIdAndId(tenantId.value(), transactionId.value())
                .map(transactionPersistenceMapper::toDomain);
    }

    @Override
    public List<Transaction> findByTenantId(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "TenantId cannot be null");
        return springDataTransactionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId.value())
                .stream()
                .map(transactionPersistenceMapper::toDomain)
                .toList();
    }
}
