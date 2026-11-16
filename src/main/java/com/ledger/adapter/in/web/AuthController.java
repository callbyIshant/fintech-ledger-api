package com.ledger.adapter.in.web;

import com.ledger.adapter.in.web.security.JwtProperties;
import com.ledger.adapter.in.web.security.JwtTokenService;
import com.ledger.core.domain.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;

    public AuthController(JwtTokenService jwtTokenService, JwtProperties jwtProperties) {
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
    }

    public record TokenRequest(
            @NotBlank(message = "tenantId must not be blank")
            String tenantId,

            @NotBlank(message = "userId must not be blank")
            String userId,

            Set<UserRole> roles
    ) {}

    public record TokenResponse(
            String accessToken,
            String tokenType,
            long expiresInMs
    ) {}

    @PostMapping("/token")
    public ResponseEntity<TokenResponse> generateDevToken(@Valid @RequestBody TokenRequest request) {
        Set<UserRole> roles = (request.roles() != null && !request.roles().isEmpty())
                ? request.roles()
                : Set.of(UserRole.ADMIN);

        String token = jwtTokenService.generateToken(request.tenantId(), request.userId(), roles);
        return ResponseEntity.ok(new TokenResponse(token, "Bearer", jwtProperties.getExpirationMs()));
    }
}
