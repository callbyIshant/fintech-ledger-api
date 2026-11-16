package com.ledger.adapter.in.web.ratelimit;

public record RateLimitResult(
        boolean consumed,
        long remainingTokens,
        long nanosToWaitForRefill
) {
    public static RateLimitResult allowed(long remainingTokens) {
        return new RateLimitResult(true, remainingTokens, 0L);
    }

    public static RateLimitResult rejected(long nanosToWaitForRefill) {
        return new RateLimitResult(false, 0L, nanosToWaitForRefill);
    }
}
