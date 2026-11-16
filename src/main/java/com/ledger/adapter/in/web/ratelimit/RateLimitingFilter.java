package com.ledger.adapter.in.web.ratelimit;

import com.ledger.adapter.in.web.security.JwtTokenService;
import com.ledger.application.context.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final TenantRateLimiter tenantRateLimiter;
    private final JwtTokenService jwtTokenService;

    public RateLimitingFilter(TenantRateLimiter tenantRateLimiter, JwtTokenService jwtTokenService) {
        this.tenantRateLimiter = tenantRateLimiter;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Only rate limit API endpoints, exclude docs & dev token endpoints
        if (path.startsWith("/api/v1/") && !path.startsWith("/api/v1/auth/")) {
            String tenantId = resolveTenantId(request);

            if (tenantId != null && !tenantId.isBlank()) {
                RateLimitResult result = tenantRateLimiter.tryConsume(tenantId);

                if (!result.consumed()) {
                    long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(result.nanosToWaitForRefill()));
                    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                    response.setContentType("application/problem+json");
                    response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

                    String errorJson = String.format(
                            "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429," +
                            "\"detail\":\"Rate limit exceeded for tenant '%s'. Please retry after %d seconds.\"," +
                            "\"errorCode\":\"RATE_LIMIT_EXCEEDED\"}",
                            tenantId, retryAfterSeconds);
                    response.getWriter().write(errorJson);
                    return;
                }

                response.setHeader("X-Rate-Limit-Remaining", String.valueOf(result.remainingTokens()));
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveTenantId(HttpServletRequest request) {
        // 1. From active TenantContext if already set
        if (TenantContext.get().isPresent()) {
            return TenantContext.get().get().tenantId();
        }

        // 2. From Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            try {
                Claims claims = jwtTokenService.parseAndValidateClaims(token);
                return jwtTokenService.extractTenantId(claims);
            } catch (Exception ignored) {
            }
        }

        // 3. Fallback header
        return request.getHeader("X-Tenant-ID");
    }
}
