package com.ledger.adapter.in.web.security;

import com.ledger.application.context.TenantContext;
import com.ledger.application.context.TenantContextData;
import com.ledger.core.domain.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtAuthenticationFilter Unit Tests")
class JwtAuthenticationFilterTest {

    private JwtTokenService tokenService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecretKey("test-filter-secret-key-at-least-32-bytes-long!!");
        properties.setIssuer("test-ledger");
        properties.setExpirationMs(3600000L);

        tokenService = new JwtTokenService(properties);
        filter = new JwtAuthenticationFilter(tokenService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should extract token, populate SecurityContext and TenantContext, then clean up in finally")
    void shouldAuthenticateAndCleanUpContext() throws ServletException, IOException {
        String token = tokenService.generateToken("tenant-xyz", "user-42", Set.of(UserRole.ADMIN));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<TenantContextData> capturedContext = new AtomicReference<>();
        AtomicReference<String> authenticatedUser = new AtomicReference<>();

        FilterChain filterChain = (req, res) -> {
            capturedContext.set(TenantContext.get().orElse(null));
            authenticatedUser.set(SecurityContextHolder.getContext().getAuthentication().getName());
        };

        filter.doFilter(request, response, filterChain);

        // Within filter chain execution:
        assertNotNull(capturedContext.get());
        assertEquals("tenant-xyz", capturedContext.get().tenantId());
        assertEquals("user-42", capturedContext.get().userId());
        assertTrue(capturedContext.get().roles().contains(UserRole.ADMIN));
        assertEquals("user-42", authenticatedUser.get());

        // After filter chain execution (guaranteed finally block):
        assertTrue(TenantContext.get().isEmpty());
    }

    @Test
    @DisplayName("Should guarantee TenantContext.clear() runs even if downstream filter throws exception")
    void shouldCleanUpTenantContextOnException() {
        String token = tokenService.generateToken("tenant-faulty", "user-err", Set.of(UserRole.OPERATOR));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain failingChain = (req, res) -> {
            assertEquals("tenant-faulty", TenantContext.getRequiredTenantId());
            throw new RuntimeException("Simulated controller error");
        };

        assertThrows(RuntimeException.class, () -> filter.doFilter(request, response, failingChain));

        // After exception is thrown, finally block MUST have cleared TenantContext:
        assertTrue(TenantContext.get().isEmpty());
    }

    @Test
    @DisplayName("Should pass through without authentication when no Authorization header present")
    void shouldPassThroughWhenNoAuthHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Boolean> hasContext = new AtomicReference<>(false);
        FilterChain filterChain = (req, res) -> {
            hasContext.set(TenantContext.get().isPresent());
        };

        filter.doFilter(request, response, filterChain);

        assertFalse(hasContext.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(TenantContext.get().isEmpty());
    }
}
