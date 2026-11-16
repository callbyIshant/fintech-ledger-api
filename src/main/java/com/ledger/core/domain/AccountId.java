package com.ledger.core.domain;

import java.util.Objects;
import java.util.UUID;

public record AccountId(UUID value) {

    public AccountId {
        Objects.requireNonNull(value, "AccountId value cannot be null");
    }

    public static AccountId of(UUID value) {
        return new AccountId(value);
    }

    public static AccountId of(String value) {
        Objects.requireNonNull(value, "AccountId string cannot be null");
        return new AccountId(UUID.fromString(value));
    }

    public static AccountId random() {
        return new AccountId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
