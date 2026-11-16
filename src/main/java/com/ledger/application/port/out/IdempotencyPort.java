package com.ledger.application.port.out;

import java.time.Duration;
import java.util.Optional;

public interface IdempotencyPort {

    <T> Optional<T> find(String tenantId, String idempotencyKey, String payloadHash, Class<T> responseType);

    void save(String tenantId, String idempotencyKey, String payloadHash, Object response, Duration ttl);
}
