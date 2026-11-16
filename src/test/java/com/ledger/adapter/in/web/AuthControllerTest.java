package com.ledger.adapter.in.web;

import com.ledger.adapter.in.web.security.JwtProperties;
import com.ledger.adapter.in.web.security.JwtTokenService;
import com.ledger.core.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuthController Unit Tests")
class AuthControllerTest {

    private AuthController authController;
    private JwtTokenService jwtTokenService;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecretKey("test-secret-key-at-least-256-bits-long-32-bytes-secure!");
        jwtProperties.setIssuer("test-ledger");
        jwtProperties.setExpirationMs(3600000L);

        jwtTokenService = new JwtTokenService(jwtProperties);
        authController = new AuthController(jwtTokenService, jwtProperties);
    }

    @Test
    @DisplayName("Should generate token successfully from request")
    void shouldGenerateToken() {
        AuthController.TokenRequest request = new AuthController.TokenRequest(
                "tenant-demo", "user-test", Set.of(UserRole.OPERATOR)
        );

        ResponseEntity<AuthController.TokenResponse> response = authController.generateDevToken(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Bearer", response.getBody().tokenType());
        assertNotNull(response.getBody().accessToken());
        assertEquals(3600000L, response.getBody().expiresInMs());

        assertTrue(jwtTokenService.isTokenValid(response.getBody().accessToken()));
        var claims = jwtTokenService.parseAndValidateClaims(response.getBody().accessToken());
        assertEquals("tenant-demo", jwtTokenService.extractTenantId(claims));
        assertEquals("user-test", jwtTokenService.extractUserId(claims));
        assertTrue(jwtTokenService.extractRoles(claims).contains(UserRole.OPERATOR));
    }

    @Test
    @DisplayName("Should default to ADMIN role if roles set is empty or null")
    void shouldDefaultToAdminWhenRolesOmitted() {
        AuthController.TokenRequest request = new AuthController.TokenRequest(
                "tenant-demo", "user-admin", null
        );

        ResponseEntity<AuthController.TokenResponse> response = authController.generateDevToken(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var claims = jwtTokenService.parseAndValidateClaims(response.getBody().accessToken());
        assertTrue(jwtTokenService.extractRoles(claims).contains(UserRole.ADMIN));
    }
}
