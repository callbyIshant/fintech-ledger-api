package com.ledger.application.context;

import com.ledger.core.domain.UserRole;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public record TenantContextData(
        String tenantId,
        String userId,
        Set<UserRole> roles
) {
    public TenantContextData {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
        roles = roles != null ? Collections.unmodifiableSet(roles) : Collections.emptySet();
    }
}
