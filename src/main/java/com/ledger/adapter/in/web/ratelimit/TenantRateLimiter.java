package com.ledger.adapter.in.web.ratelimit;

public interface TenantRateLimiter {
    RateLimitResult tryConsume(String tenantId);
}
