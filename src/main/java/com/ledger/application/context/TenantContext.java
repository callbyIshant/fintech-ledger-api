package com.ledger.application.context;

import java.util.Objects;
import java.util.Optional;

public final class TenantContext {

    private static final ThreadLocal<TenantContextData> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(TenantContextData data) {
        Objects.requireNonNull(data, "TenantContextData cannot be null");
        CURRENT.set(data);
    }

    public static Optional<TenantContextData> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static String getRequiredTenantId() {
        return get()
                .map(TenantContextData::tenantId)
                .orElseThrow(() -> new IllegalStateException("No tenantId found in current execution context"));
    }

    public static void clear() {
        CURRENT.remove();
    }
}
