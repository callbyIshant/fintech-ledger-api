package com.ledger.adapter.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ledger.application.port.in.TransferFundsUseCase.TransferResult;
import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.IdempotencyConflictException;
import com.ledger.core.domain.Money;
import com.ledger.core.domain.TenantId;
import com.ledger.core.domain.TransactionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("RedisIdempotencyAdapter Unit Tests")
class RedisIdempotencyAdapterTest {

    private RedisOperations<String, String> redisOperations;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;
    private RedisIdempotencyAdapter adapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisOperations = mock(RedisOperations.class);
        valueOperations = mock(ValueOperations.class);
        when(redisOperations.opsForValue()).thenReturn(valueOperations);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        adapter = new RedisIdempotencyAdapter(redisOperations, objectMapper);
    }

    @Test
    @DisplayName("Should return empty when idempotency key not found in Redis (cache miss)")
    void shouldReturnEmptyOnCacheMiss() {
        when(valueOperations.get("ledger:idempotency:tenant-1:key-123")).thenReturn(null);

        Optional<TransferResult> result = adapter.find("tenant-1", "key-123", "hash-abc", TransferResult.class);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return cached response when key exists and payload hash matches exactly (replay)")
    void shouldReturnCachedOnMatchingHash() throws Exception {
        TransferResult originalResult = new TransferResult(
                TransactionId.random(),
                TenantId.of("tenant-1"),
                AccountId.random(),
                AccountId.random(),
                Money.of("150.00", "USD"),
                "key-123",
                Instant.now()
        );

        String resultJson = objectMapper.writeValueAsString(originalResult);
        RedisIdempotencyAdapter.IdempotencyRecord record =
                new RedisIdempotencyAdapter.IdempotencyRecord("hash-correct", resultJson, Instant.now());
        String storedJson = objectMapper.writeValueAsString(record);

        when(valueOperations.get("ledger:idempotency:tenant-1:key-123")).thenReturn(storedJson);

        Optional<TransferResult> result = adapter.find("tenant-1", "key-123", "hash-correct", TransferResult.class);

        assertTrue(result.isPresent());
        assertEquals(originalResult.transactionId(), result.get().transactionId());
        assertEquals(originalResult.amount(), result.get().amount());
    }

    @Test
    @DisplayName("Should throw IdempotencyConflictException when key exists but payload hash differs (mismatch)")
    void shouldThrowOnHashMismatch() throws Exception {
        RedisIdempotencyAdapter.IdempotencyRecord record =
                new RedisIdempotencyAdapter.IdempotencyRecord("hash-original", "{}", Instant.now());
        String storedJson = objectMapper.writeValueAsString(record);

        when(valueOperations.get("ledger:idempotency:tenant-1:key-123")).thenReturn(storedJson);

        IdempotencyConflictException ex = assertThrows(IdempotencyConflictException.class, () ->
                adapter.find("tenant-1", "key-123", "hash-different", TransferResult.class));

        assertEquals("key-123", ex.getIdempotencyKey());
        assertTrue(ex.getMessage().contains("Payload hash mismatch"));
    }

    @Test
    @DisplayName("Should save idempotency record with 24-hour TTL")
    void shouldSaveRecord() {
        TransferResult result = new TransferResult(
                TransactionId.random(),
                TenantId.of("tenant-1"),
                AccountId.random(),
                AccountId.random(),
                Money.of("100.00", "USD"),
                "key-save",
                Instant.now()
        );

        adapter.save("tenant-1", "key-save", "hash-val", result, Duration.ofHours(24));

        verify(valueOperations, times(1)).set(
                eq("ledger:idempotency:tenant-1:key-save"),
                contains("hash-val"),
                eq(Duration.ofHours(24))
        );
    }

    @Test
    @DisplayName("Should compute consistent SHA-256 hash")
    void shouldComputeHashConsistently() {
        String payload = "source:dest:100.00:USD";
        String hash1 = RedisIdempotencyAdapter.computeHash(payload);
        String hash2 = RedisIdempotencyAdapter.computeHash(payload);

        assertNotNull(hash1);
        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2);
    }
}
