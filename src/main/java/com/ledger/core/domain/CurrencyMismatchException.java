package com.ledger.core.domain;

import java.util.Currency;

public class CurrencyMismatchException extends RuntimeException {

    private final String expectedCurrency;
    private final String actualCurrency;

    public CurrencyMismatchException(String message) {
        super(message);
        this.expectedCurrency = null;
        this.actualCurrency = null;
    }

    public CurrencyMismatchException(Currency expected, Currency actual) {
        this(expected != null ? expected.getCurrencyCode() : null,
             actual != null ? actual.getCurrencyCode() : null);
    }

    public CurrencyMismatchException(String expectedCurrency, String actualCurrency) {
        super(String.format("Currency mismatch: expected %s but got %s", expectedCurrency, actualCurrency));
        this.expectedCurrency = expectedCurrency;
        this.actualCurrency = actualCurrency;
    }

    public String getExpectedCurrency() {
        return expectedCurrency;
    }

    public String getActualCurrency() {
        return actualCurrency;
    }
}
