package com.ledger.application.port.out;

import com.ledger.core.domain.TenantId;
import com.ledger.core.domain.Transaction;
import com.ledger.core.domain.TransactionId;

import java.util.List;
import java.util.Optional;

public interface TransactionRepositoryPort {

    Transaction save(Transaction transaction);

    Optional<Transaction> findById(TransactionId transactionId);

    Optional<Transaction> findByTenantIdAndTransactionId(TenantId tenantId, TransactionId transactionId);

    List<Transaction> findByTenantId(TenantId tenantId);
}
