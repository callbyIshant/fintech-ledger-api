package com.ledger.adapter.out.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.application.port.out.IdempotencyPort;
import com.ledger.core.domain.IdempotencyConflictException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

@Component
public class RedisIdempotencyAdapter implements IdempotencyPort {

    private static final String KEY_PREFIX = "ledger:idempotency:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final RedisOperations<String, String> redisOperations;
    private final ObjectMapper objectMapper;

    public RedisIdempotencyAdapter(RedisOperations<String, String> redisOperations, ObjectMapper objectMapper) {
        this.redisOperations = redisOperations;
        this.objectMapper = objectMapper.copy()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public record IdempotencyRecord(
            String requestHash,
            String responseJson,
            Instant createdAt
    ) {}

    public static String computeHash(String payload) {
        if (payload == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }

    @Override
    public <T> Optional<T> find(String tenantId, String idempotencyKey, String payloadHash, Class<T> responseType) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        String key = buildKey(tenantId, idempotencyKey);
        String rawJson = redisOperations.opsForValue().get(key);

        if (rawJson == null) {
            return Optional.empty();
        }

        try {
            IdempotencyRecord record = objectMapper.readValue(rawJson, IdempotencyRecord.class);

            if (!Objects.equals(record.requestHash(), payloadHash)) {
                throw new IdempotencyConflictException(idempotencyKey,
                        "Payload hash mismatch. The key was previously used with a different request payload.");
            }

            T result = objectMapper.readValue(record.responseJson(), responseType);
            return Optional.of(result);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize idempotency record for key: " + key, e);
        }
    }

    @Override
    public void save(String tenantId, String idempotencyKey, String payloadHash, Object response, Duration ttl) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || response == null) {
            return;
        }
        String key = buildKey(tenantId, idempotencyKey);
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            IdempotencyRecord record = new IdempotencyRecord(payloadHash, responseJson, Instant.now());
            String recordJson = objectMapper.writeValueAsString(record);

            Duration effectiveTtl = (ttl != null) ? ttl : DEFAULT_TTL;
            redisOperations.opsForValue().set(key, recordJson, effectiveTtl);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize idempotency record for key: " + key, e);
        }
    }

    private String buildKey(String tenantId, String idempotencyKey) {
        return KEY_PREFIX + tenantId + ":" + idempotencyKey;
    }
}
