package com.ledger.application.context;

import com.ledger.core.domain.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TenantContext Unit Tests")
class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should set and retrieve TenantContextData correctly")
    void shouldSetAndGetTenantContextData() {
        TenantContextData data = new TenantContextData("tenant-1", "user-1", Set.of(UserRole.ADMIN));
        TenantContext.set(data);

        Optional<TenantContextData> retrieved = TenantContext.get();
        assertTrue(retrieved.isPresent());
        assertEquals("tenant-1", retrieved.get().tenantId());
        assertEquals("user-1", retrieved.get().userId());
        assertTrue(retrieved.get().roles().contains(UserRole.ADMIN));
        assertEquals("tenant-1", TenantContext.getRequiredTenantId());
    }

    @Test
    @DisplayName("Should throw IllegalStateException when getRequiredTenantId called on empty context")
    void shouldThrowWhenNoTenantInContext() {
        TenantContext.clear();
        assertThrows(IllegalStateException.class, TenantContext::getRequiredTenantId);
    }

    @Test
    @DisplayName("Should clear context cleanly")
    void shouldClearContext() {
        TenantContext.set(new TenantContextData("tenant-x", "user-x", Set.of(UserRole.OPERATOR)));
        TenantContext.clear();

        assertTrue(TenantContext.get().isEmpty());
    }

    @Test
    @DisplayName("Should maintain complete thread isolation across virtual or platform threads")
    void shouldMaintainThreadIsolation() throws InterruptedException {
        TenantContext.set(new TenantContextData("main-tenant", "main-user", Set.of(UserRole.AUDITOR)));

        AtomicReference<String> threadTenant = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread thread = Thread.ofVirtual().start(() -> {
            try {
                TenantContext.set(new TenantContextData("other-tenant", "other-user", Set.of(UserRole.OPERATOR)));
                threadTenant.set(TenantContext.getRequiredTenantId());
            } finally {
                TenantContext.clear();
                latch.countDown();
            }
        });

        latch.await();
        assertEquals("other-tenant", threadTenant.get());
        assertEquals("main-tenant", TenantContext.getRequiredTenantId());
    }
}
