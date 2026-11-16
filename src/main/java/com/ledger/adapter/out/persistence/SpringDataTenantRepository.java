package com.ledger.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataTenantRepository extends JpaRepository<TenantJpaEntity, String> {
}
