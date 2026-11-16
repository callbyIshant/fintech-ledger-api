package com.ledger.adapter.in.web.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("Bucket4jTenantRateLimiter Unit Tests")
class Bucket4jTenantRateLimiterTest {

    private RateLimitProperties properties;
    private RedisOperations<String, String> redisOperations;
    private ValueOperations<String, String> valueOperations;
    private Bucket4jTenantRateLimiter rateLimiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        properties = new RateLimitProperties();
        properties.setCapacity(2);
        properties.setRefillTokens(1);
        properties.setRefillDurationSeconds(60);

        redisOperations = mock(RedisOperations.class);
        valueOperations = mock(ValueOperations.class);
        when(redisOperations.opsForValue()).thenReturn(valueOperations);

        rateLimiter = new Bucket4jTenantRateLimiter(properties, redisOperations);
    }

    @Test
    @DisplayName("Should consume tokens until capacity is exhausted")
    void shouldConsumeTokensUntilExhausted() {
        String tenantId = "tenant-bucket-test";

        RateLimitResult result1 = rateLimiter.tryConsume(tenantId);
        assertTrue(result1.consumed());
        assertEquals(1L, result1.remainingTokens());

        RateLimitResult result2 = rateLimiter.tryConsume(tenantId);
        assertTrue(result2.consumed());
        assertEquals(0L, result2.remainingTokens());

        RateLimitResult result3 = rateLimiter.tryConsume(tenantId);
        assertFalse(result3.consumed());
        assertTrue(result3.nanosToWaitForRefill() > 0);
    }
}
