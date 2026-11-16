package com.ledger.adapter.in.web.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "security.rate-limit")
public class RateLimitProperties {

    private long capacity = 100;
    private long refillTokens = 100;
    private long refillDurationSeconds = 60;

    public long getCapacity() {
        return capacity;
    }

    public void setCapacity(long capacity) {
        this.capacity = capacity;
    }

    public long getRefillTokens() {
        return refillTokens;
    }

    public void setRefillTokens(long refillTokens) {
        this.refillTokens = refillTokens;
    }

    public long getRefillDurationSeconds() {
        return refillDurationSeconds;
    }

    public void setRefillDurationSeconds(long refillDurationSeconds) {
        this.refillDurationSeconds = refillDurationSeconds;
    }
}
