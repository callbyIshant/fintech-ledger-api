package com.ledger.core.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class IdempotencyConflictException extends RuntimeException {

    private final String idempotencyKey;

    public IdempotencyConflictException(String message) {
        super(message);
        this.idempotencyKey = null;
    }

    public IdempotencyConflictException(String idempotencyKey, String message) {
        super(String.format("Idempotency conflict for key '%s': %s", idempotencyKey, message));
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
