package com.ledger.core.domain;

public class InsufficientFundsException extends RuntimeException {

    private final AccountId accountId;
    private final Money requestedAmount;
    private final Money currentBalance;

    public InsufficientFundsException(String message) {
        super(message);
        this.accountId = null;
        this.requestedAmount = null;
        this.currentBalance = null;
    }

    public InsufficientFundsException(AccountId accountId, Money requestedAmount, Money currentBalance) {
        super(String.format("Account %s has insufficient funds: requested %s with available balance %s",
                accountId != null ? accountId.value() : "unknown",
                requestedAmount,
                currentBalance));
        this.accountId = accountId;
        this.requestedAmount = requestedAmount;
        this.currentBalance = currentBalance;
    }

    public AccountId getAccountId() {
        return accountId;
    }

    public Money getRequestedAmount() {
        return requestedAmount;
    }

    public Money getCurrentBalance() {
        return currentBalance;
    }
}
