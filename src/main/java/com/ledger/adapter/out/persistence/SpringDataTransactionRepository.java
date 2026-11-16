package com.ledger.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataTransactionRepository extends JpaRepository<TransactionJpaEntity, UUID> {

    Optional<TransactionJpaEntity> findByTenantIdAndId(String tenantId, UUID id);

    List<TransactionJpaEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
