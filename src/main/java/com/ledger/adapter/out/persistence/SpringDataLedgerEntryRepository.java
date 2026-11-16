package com.ledger.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataLedgerEntryRepository extends JpaRepository<LedgerEntryJpaEntity, UUID> {

    List<LedgerEntryJpaEntity> findByTenantIdAndAccountIdOrderByCreatedAtDesc(
            String tenantId,
            UUID accountId,
            Pageable pageable
    );

    @Query("SELECT e FROM LedgerEntryJpaEntity e WHERE e.tenantId = :tenantId AND e.accountId = :accountId " +
           "AND (:cursorCreatedAt IS NULL OR e.createdAt < :cursorCreatedAt OR (e.createdAt = :cursorCreatedAt AND e.id < :cursorId)) " +
           "ORDER BY e.createdAt DESC, e.id DESC")
    List<LedgerEntryJpaEntity> findByTenantIdAndAccountIdWithCursor(
            @Param("tenantId") String tenantId,
            @Param("accountId") UUID accountId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );
}
