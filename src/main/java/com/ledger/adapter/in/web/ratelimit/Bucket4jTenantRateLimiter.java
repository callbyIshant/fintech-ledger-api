package com.ledger.adapter.in.web.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class Bucket4jTenantRateLimiter implements TenantRateLimiter {

    private final RateLimitProperties properties;
    private final RedisOperations<String, String> redisOperations;
    private final Map<String, Bucket> tenantBuckets = new ConcurrentHashMap<>();

    public Bucket4jTenantRateLimiter(RateLimitProperties properties, RedisOperations<String, String> redisOperations) {
        this.properties = properties;
        this.redisOperations = redisOperations;
    }

    @Override
    public RateLimitResult tryConsume(String tenantId) {
        Bucket bucket = tenantBuckets.computeIfAbsent(tenantId, this::createBucket);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            try {
                String redisKey = "ledger:ratelimit:" + tenantId;
                redisOperations.opsForValue().increment(redisKey);
                redisOperations.expire(redisKey, Duration.ofMinutes(1));
            } catch (Exception ignored) {
                // Non-blocking telemetry
            }
            return RateLimitResult.allowed(probe.getRemainingTokens());
        }

        return RateLimitResult.rejected(probe.getNanosToWaitForRefill());
    }

    private Bucket createBucket(String tenantId) {
        Bandwidth limit = Bandwidth.classic(
                properties.getCapacity(),
                Refill.greedy(properties.getRefillTokens(), Duration.ofSeconds(properties.getRefillDurationSeconds()))
        );
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
