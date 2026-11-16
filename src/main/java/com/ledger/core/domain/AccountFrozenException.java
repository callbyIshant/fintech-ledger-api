package com.ledger.core.domain;

public class AccountFrozenException extends RuntimeException {

    private final AccountId accountId;

    public AccountFrozenException(String message) {
        super(message);
        this.accountId = null;
    }

    public AccountFrozenException(AccountId accountId) {
        super(String.format("Account %s is FROZEN. Operations are disallowed.",
                accountId != null ? accountId.value() : "unknown"));
        this.accountId = accountId;
    }

    public AccountId getAccountId() {
        return accountId;
    }
}
