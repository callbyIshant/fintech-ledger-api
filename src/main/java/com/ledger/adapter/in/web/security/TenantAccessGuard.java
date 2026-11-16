package com.ledger.adapter.in.web.security;

import com.ledger.application.context.TenantContext;
import com.ledger.core.domain.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

@Component
public class TenantAccessGuard {

    public void checkTenantAccess(String targetTenantId) {
        Objects.requireNonNull(targetTenantId, "Target tenantId cannot be null");
        String currentTenantId = TenantContext.getRequiredTenantId();

        if (!currentTenantId.equals(targetTenantId)) {
            throw new AccessDeniedException(String.format(
                    "Cross-tenant access violation: authenticated tenant '%s' cannot access target tenant '%s'",
                    currentTenantId, targetTenantId));
        }
    }

    public void checkRole(UserRole requiredRole) {
        Objects.requireNonNull(requiredRole, "Required role cannot be null");
        Set<UserRole> currentRoles = TenantContext.get()
                .map(data -> data.roles())
                .orElse(Set.of());

        if (!currentRoles.contains(requiredRole) && !currentRoles.contains(UserRole.ADMIN)) {
            throw new AccessDeniedException(String.format(
                    "Insufficient permissions: requires %s but found %s", requiredRole, currentRoles));
        }
    }
}
