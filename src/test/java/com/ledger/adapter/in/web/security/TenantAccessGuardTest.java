package com.ledger.adapter.in.web.security;

import com.ledger.application.context.TenantContext;
import com.ledger.application.context.TenantContextData;
import com.ledger.core.domain.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TenantAccessGuard Cross-Tenant Isolation Tests")
class TenantAccessGuardTest {

    private TenantAccessGuard guard;

    @BeforeEach
    void setUp() {
        guard = new TenantAccessGuard();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should allow access when target tenant matches context tenant")
    void shouldAllowMatchingTenantAccess() {
        TenantContext.set(new TenantContextData("tenant-alpha", "user-1", Set.of(UserRole.OPERATOR)));

        assertDoesNotThrow(() -> guard.checkTenantAccess("tenant-alpha"));
    }

    @Test
    @DisplayName("Should block cross-tenant access when target tenant does not match context tenant")
    void shouldBlockCrossTenantAccess() {
        TenantContext.set(new TenantContextData("tenant-alpha", "user-1", Set.of(UserRole.OPERATOR)));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                guard.checkTenantAccess("tenant-beta"));

        assertTrue(ex.getMessage().contains("Cross-tenant access violation"));
        assertTrue(ex.getMessage().contains("tenant-alpha"));
        assertTrue(ex.getMessage().contains("tenant-beta"));
    }

    @Test
    @DisplayName("Should throw IllegalStateException when checkTenantAccess called with no context")
    void shouldThrowWhenNoContext() {
        TenantContext.clear();

        assertThrows(IllegalStateException.class, () ->
                guard.checkTenantAccess("any-tenant"));
    }

    @Test
    @DisplayName("Should enforce role permissions: required role present succeeds")
    void shouldAllowWhenRolePresent() {
        TenantContext.set(new TenantContextData("tenant-1", "user-1", Set.of(UserRole.OPERATOR)));
        assertDoesNotThrow(() -> guard.checkRole(UserRole.OPERATOR));
    }

    @Test
    @DisplayName("Should allow ADMIN role to bypass specific role check")
    void shouldAllowAdminRoleToBypass() {
        TenantContext.set(new TenantContextData("tenant-1", "admin-1", Set.of(UserRole.ADMIN)));
        assertDoesNotThrow(() -> guard.checkRole(UserRole.OPERATOR));
        assertDoesNotThrow(() -> guard.checkRole(UserRole.AUDITOR));
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when required role is missing")
    void shouldRejectWhenRoleMissing() {
        TenantContext.set(new TenantContextData("tenant-1", "auditor-1", Set.of(UserRole.AUDITOR)));

        assertThrows(AccessDeniedException.class, () -> guard.checkRole(UserRole.OPERATOR));
    }
}
