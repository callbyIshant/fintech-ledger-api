package com.ledger.core.domain;

import java.util.Objects;
import java.util.UUID;

public record TransactionId(UUID value) {

    public TransactionId {
        Objects.requireNonNull(value, "TransactionId value cannot be null");
    }

    public static TransactionId of(UUID value) {
        return new TransactionId(value);
    }

    public static TransactionId of(String value) {
        Objects.requireNonNull(value, "TransactionId string cannot be null");
        return new TransactionId(UUID.fromString(value));
    }

    public static TransactionId random() {
        return new TransactionId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
