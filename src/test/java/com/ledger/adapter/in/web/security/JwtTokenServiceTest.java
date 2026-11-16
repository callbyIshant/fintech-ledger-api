package com.ledger.adapter.in.web.security;

import com.ledger.core.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtTokenService Unit Tests")
class JwtTokenServiceTest {

    private JwtTokenService tokenService;
    private JwtProperties properties;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecretKey("test-secret-key-at-least-256-bits-long-32-bytes-secure!");
        properties.setIssuer("test-ledger");
        properties.setExpirationMs(3600000L); // 1 hour

        tokenService = new JwtTokenService(properties);
    }

    @Test
    @DisplayName("Should generate and validate JWT token with correct claims")
    void shouldGenerateAndValidateToken() {
        String token = tokenService.generateToken("tenant-alpha", "usr-100", Set.of(UserRole.ADMIN, UserRole.OPERATOR));
        assertNotNull(token);
        assertTrue(tokenService.isTokenValid(token));

        Claims claims = tokenService.parseAndValidateClaims(token);
        assertEquals("tenant-alpha", tokenService.extractTenantId(claims));
        assertEquals("usr-100", tokenService.extractUserId(claims));

        Set<UserRole> roles = tokenService.extractRoles(claims);
        assertEquals(2, roles.size());
        assertTrue(roles.contains(UserRole.ADMIN));
        assertTrue(roles.contains(UserRole.OPERATOR));
    }

    @Test
    @DisplayName("Should reject tampered token")
    void shouldRejectTamperedToken() {
        String token = tokenService.generateToken("tenant-alpha", "usr-100", Set.of(UserRole.OPERATOR));
        String tampered = token + "corrupted";

        assertFalse(tokenService.isTokenValid(tampered));
        assertThrows(JwtException.class, () -> tokenService.parseAndValidateClaims(tampered));
    }

    @Test
    @DisplayName("Should reject token signed with different key")
    void shouldRejectWrongKeyToken() {
        JwtProperties otherProperties = new JwtProperties();
        otherProperties.setSecretKey("completely-different-signing-key-32-bytes-long!!");
        JwtTokenService otherService = new JwtTokenService(otherProperties);

        String foreignToken = otherService.generateToken("tenant-beta", "usr-200", Set.of(UserRole.AUDITOR));

        assertFalse(tokenService.isTokenValid(foreignToken));
        assertThrows(JwtException.class, () -> tokenService.parseAndValidateClaims(foreignToken));
    }

    @Test
    @DisplayName("Should reject expired token")
    void shouldRejectExpiredToken() {
        properties.setExpirationMs(-1000L); // expired immediately
        JwtTokenService expiredService = new JwtTokenService(properties);

        String expiredToken = expiredService.generateToken("tenant-gamma", "usr-300", Set.of(UserRole.ADMIN));

        assertFalse(expiredService.isTokenValid(expiredToken));
        assertThrows(JwtException.class, () -> expiredService.parseAndValidateClaims(expiredToken));
    }
}
