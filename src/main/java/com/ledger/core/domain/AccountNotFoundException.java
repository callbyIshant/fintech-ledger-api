package com.ledger.core.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class AccountNotFoundException extends RuntimeException {

    private final AccountId accountId;

    public AccountNotFoundException(String message) {
        super(message);
        this.accountId = null;
    }

    public AccountNotFoundException(AccountId accountId) {
        super(String.format("Account '%s' not found", accountId != null ? accountId.value() : "unknown"));
        this.accountId = accountId;
    }

    public AccountNotFoundException(TenantId tenantId, AccountId accountId) {
        super(String.format("Account '%s' not found for tenant '%s'",
                accountId != null ? accountId.value() : "unknown",
                tenantId != null ? tenantId.value() : "unknown"));
        this.accountId = accountId;
    }

    public AccountId getAccountId() {
        return accountId;
    }
}
