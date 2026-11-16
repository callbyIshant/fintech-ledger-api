package com.ledger.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataAccountRepository extends JpaRepository<AccountJpaEntity, UUID> {

    Optional<AccountJpaEntity> findByTenantIdAndId(String tenantId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountJpaEntity a WHERE a.tenantId = :tenantId AND a.id IN :accountIds ORDER BY a.id ASC")
    List<AccountJpaEntity> findByTenantIdAndIdInOrderByIdAscForUpdate(
            @Param("tenantId") String tenantId,
            @Param("accountIds") Collection<UUID> accountIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountJpaEntity a WHERE a.tenantId = :tenantId AND a.id = :accountId")
    Optional<AccountJpaEntity> findByTenantIdAndIdForUpdate(
            @Param("tenantId") String tenantId,
            @Param("accountId") UUID accountId);
}
