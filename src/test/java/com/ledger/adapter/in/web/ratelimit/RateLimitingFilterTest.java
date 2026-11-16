package com.ledger.adapter.in.web.ratelimit;

import com.ledger.adapter.in.web.security.JwtProperties;
import com.ledger.adapter.in.web.security.JwtTokenService;
import com.ledger.application.context.TenantContext;
import com.ledger.application.context.TenantContextData;
import com.ledger.core.domain.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("RateLimitingFilter Unit Tests")
class RateLimitingFilterTest {

    private TenantRateLimiter tenantRateLimiter;
    private JwtTokenService jwtTokenService;
    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        tenantRateLimiter = mock(TenantRateLimiter.class);

        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecretKey("test-secret-key-at-least-32-bytes-long-for-hmac-sha");
        jwtProperties.setExpirationMs(3600000L);
        jwtProperties.setIssuer("test-issuer");
        jwtTokenService = new JwtTokenService(jwtProperties);

        filter = new RateLimitingFilter(tenantRateLimiter, jwtTokenService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Request with available tokens should pass through and set X-Rate-Limit-Remaining header")
    void shouldAllowRequestWhenTokensAvailable() throws Exception {
        TenantContext.set(new TenantContextData("tenant-t1", "user-1", Set.of(UserRole.OPERATOR)));

        when(tenantRateLimiter.tryConsume("tenant-t1")).thenReturn(RateLimitResult.allowed(49L));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts/123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        assertEquals("49", response.getHeader("X-Rate-Limit-Remaining"));
        assertNull(response.getHeader("Retry-After"));
    }

    @Test
    @DisplayName("Request with exhausted tokens should return 429 Too Many Requests and Retry-After header")
    void shouldBlockRequestWhenTokensExhausted() throws Exception {
        TenantContext.set(new TenantContextData("tenant-t1", "user-1", Set.of(UserRole.OPERATOR)));

        when(tenantRateLimiter.tryConsume("tenant-t1")).thenReturn(RateLimitResult.rejected(5_000_000_000L)); // 5 seconds

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transfers");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(429, response.getStatus());
        assertEquals("5", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("RATE_LIMIT_EXCEEDED"));
        assertTrue(response.getContentAsString().contains("tenant-t1"));
    }

    @Test
    @DisplayName("Request resolving tenant from Authorization Bearer token should be rate limited")
    void shouldResolveTenantFromBearerToken() throws Exception {
        String token = jwtTokenService.generateToken("tenant-jwt", "user-jwt", Set.of(UserRole.OPERATOR));
        when(tenantRateLimiter.tryConsume("tenant-jwt")).thenReturn(RateLimitResult.allowed(19L));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts/xyz");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        assertEquals("19", response.getHeader("X-Rate-Limit-Remaining"));
        verify(tenantRateLimiter).tryConsume("tenant-jwt");
    }

    @Test
    @DisplayName("Public or auth endpoints (/api/v1/auth/token) should bypass rate limiting")
    void shouldBypassExcludedEndpoints() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verifyNoInteractions(tenantRateLimiter);
    }
}
