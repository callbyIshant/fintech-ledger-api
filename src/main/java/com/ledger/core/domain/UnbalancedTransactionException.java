package com.ledger.core.domain;

public class UnbalancedTransactionException extends RuntimeException {

    private final TransactionId transactionId;
    private final Money totalDebits;
    private final Money totalCredits;

    public UnbalancedTransactionException(String message) {
        super(message);
        this.transactionId = null;
        this.totalDebits = null;
        this.totalCredits = null;
    }

    public UnbalancedTransactionException(TransactionId transactionId, Money totalDebits, Money totalCredits) {
        super(String.format("Transaction %s is unbalanced: debits=%s, credits=%s",
                transactionId != null ? transactionId.value() : "unknown",
                totalDebits,
                totalCredits));
        this.transactionId = transactionId;
        this.totalDebits = totalDebits;
        this.totalCredits = totalCredits;
    }

    public TransactionId getTransactionId() {
        return transactionId;
    }

    public Money getTotalDebits() {
        return totalDebits;
    }

    public Money getTotalCredits() {
        return totalCredits;
    }
}
