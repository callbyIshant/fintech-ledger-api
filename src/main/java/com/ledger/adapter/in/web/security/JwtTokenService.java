package com.ledger.adapter.in.web.security;

import com.ledger.core.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class JwtTokenService {

    public static final String CLAIM_TENANT_ID = "tenant_id";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_ROLE = "role";

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes = properties.getSecretKey().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            this.secretKey = Keys.hmacShaKeyFor(padded);
        } else {
            this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        }
    }

    public String generateToken(String tenantId, String userId, Set<UserRole> roles) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
        Set<UserRole> effectiveRoles = (roles != null && !roles.isEmpty()) ? roles : Set.of(UserRole.OPERATOR);

        List<String> roleNames = effectiveRoles.stream()
                .map(Enum::name)
                .toList();

        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getExpirationMs());

        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(userId)
                .claim(CLAIM_TENANT_ID, tenantId)
                .claim(CLAIM_ROLES, roleNames)
                .claim(CLAIM_ROLE, roleNames.get(0))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public Claims parseAndValidateClaims(String token) {
        Objects.requireNonNull(token, "Token cannot be null");
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String tenantId = claims.get(CLAIM_TENANT_ID, String.class);
        if (tenantId == null || tenantId.isBlank()) {
            throw new JwtException("Token missing required 'tenant_id' claim");
        }
        return claims;
    }

    public boolean isTokenValid(String token) {
        try {
            parseAndValidateClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String extractTenantId(Claims claims) {
        return claims.get(CLAIM_TENANT_ID, String.class);
    }

    public String extractUserId(Claims claims) {
        return claims.getSubject();
    }

    @SuppressWarnings("unchecked")
    public Set<UserRole> extractRoles(Claims claims) {
        Set<UserRole> result = new HashSet<>();
        Object rolesObj = claims.get(CLAIM_ROLES);
        if (rolesObj instanceof Collection<?> list) {
            for (Object item : list) {
                if (item != null) {
                    try {
                        result.add(UserRole.valueOf(item.toString().trim()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
        if (result.isEmpty()) {
            String singleRole = claims.get(CLAIM_ROLE, String.class);
            if (singleRole != null) {
                try {
                    result.add(UserRole.valueOf(singleRole.trim()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return result.isEmpty() ? Set.of(UserRole.OPERATOR) : result;
    }
}
