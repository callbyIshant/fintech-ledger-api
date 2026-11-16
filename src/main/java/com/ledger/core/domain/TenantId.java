package com.ledger.core.domain;

import java.util.Objects;
import java.util.UUID;

public record TenantId(String value) {

    public TenantId {
        Objects.requireNonNull(value, "TenantId value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TenantId value cannot be blank");
        }
    }

    public static TenantId of(String value) {
        return new TenantId(value);
    }

    public static TenantId of(UUID value) {
        Objects.requireNonNull(value, "TenantId UUID cannot be null");
        return new TenantId(value.toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
