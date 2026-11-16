package com.ledger.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Money Value Object Tests")
class MoneyTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency JPY = Currency.getInstance("JPY");

    @Test
    @DisplayName("Should create Money with correct scale for currency")
    void shouldCreateMoneyWithCorrectScale() {
        Money usd = Money.of(new BigDecimal("10.5"), USD);
        assertEquals(new BigDecimal("10.50"), usd.amount());
        assertEquals(2, usd.amount().scale());

        Money jpy = Money.of(new BigDecimal("1000.7"), JPY);
        assertEquals(new BigDecimal("1001"), jpy.amount());
        assertEquals(0, jpy.amount().scale());
    }

    @ParameterizedTest(name = "Input {0} should round to {1} with HALF_EVEN")
    @CsvSource({
            "2.505, 2.50",
            "2.515, 2.52",
            "2.525, 2.52",
            "2.535, 2.54",
            "10.005, 10.00",
            "10.015, 10.02",
            "10.025, 10.02",
            "10.035, 10.04"
    })
    @DisplayName("Should correctly apply Banker's Rounding (HALF_EVEN)")
    void shouldApplyHalfEvenRounding(String input, String expected) {
        Money money = Money.of(new BigDecimal(input), USD);
        assertEquals(new BigDecimal(expected), money.amount());
    }

    @Test
    @DisplayName("Should add amounts with same currency")
    void shouldAddSameCurrency() {
        Money a = Money.of("10.25", "USD");
        Money b = Money.of("5.50", "USD");

        Money sum = a.add(b);

        assertEquals(Money.of("15.75", "USD"), sum);
    }

    @Test
    @DisplayName("Should throw CurrencyMismatchException on add with different currencies")
    void shouldThrowOnAddWithCurrencyMismatch() {
        Money usd = Money.of("10.00", "USD");
        Money eur = Money.of("10.00", "EUR");

        CurrencyMismatchException ex = assertThrows(CurrencyMismatchException.class, () -> usd.add(eur));
        assertEquals("USD", ex.getExpectedCurrency());
        assertEquals("EUR", ex.getActualCurrency());
    }

    @Test
    @DisplayName("Should subtract amounts with same currency")
    void shouldSubtractSameCurrency() {
        Money a = Money.of("10.25", "USD");
        Money b = Money.of("5.50", "USD");

        Money diff = a.subtract(b);

        assertEquals(Money.of("4.75", "USD"), diff);
    }

    @Test
    @DisplayName("Should throw CurrencyMismatchException on subtract with different currencies")
    void shouldThrowOnSubtractWithCurrencyMismatch() {
        Money usd = Money.of("10.00", "USD");
        Money eur = Money.of("5.00", "EUR");

        assertThrows(CurrencyMismatchException.class, () -> usd.subtract(eur));
    }

    @Test
    @DisplayName("Should correctly evaluate isNegative, isPositive, and isZero")
    void shouldEvaluatePredicates() {
        Money positive = Money.of("10.00", "USD");
        Money zero = Money.zero(USD);
        Money negative = Money.of("-5.00", "USD");

        assertTrue(positive.isPositive());
        assertFalse(positive.isNegative());
        assertFalse(positive.isZero());

        assertFalse(zero.isPositive());
        assertFalse(zero.isNegative());
        assertTrue(zero.isZero());

        assertFalse(negative.isPositive());
        assertTrue(negative.isNegative());
        assertFalse(negative.isZero());
    }

    @Test
    @DisplayName("Should compare money values of same currency")
    void shouldCompareMoneyValues() {
        Money smaller = Money.of("10.00", "USD");
        Money larger = Money.of("20.00", "USD");
        Money equivalent = Money.of(new BigDecimal("10.000"), USD);

        assertTrue(smaller.isLessThan(larger));
        assertTrue(larger.isGreaterThan(smaller));
        assertTrue(smaller.isLessThanOrEqual(equivalent));
        assertTrue(smaller.isGreaterThanOrEqual(equivalent));
        assertEquals(0, smaller.compareTo(equivalent));
    }

    @Test
    @DisplayName("Should correctly handle abs and negate")
    void shouldHandleAbsAndNegate() {
        Money negative = Money.of("-15.50", "USD");
        assertEquals(Money.of("15.50", "USD"), negative.abs());
        assertEquals(Money.of("15.50", "USD"), negative.negate());

        Money positive = Money.of("15.50", "USD");
        assertEquals(Money.of("-15.50", "USD"), positive.negate());
    }

    @Test
    @DisplayName("Should be equal regardless of original BigDecimal scale representation")
    void shouldBeEqualRegardlessOfInputScale() {
        Money m1 = Money.of(new BigDecimal("100"), USD);
        Money m2 = Money.of(new BigDecimal("100.00"), USD);
        Money m3 = Money.of(new BigDecimal("100.0000"), USD);

        assertEquals(m1, m2);
        assertEquals(m2, m3);
        assertEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    @DisplayName("Should reject null amount or null currency")
    void shouldRejectNulls() {
        assertThrows(NullPointerException.class, () -> new Money(null, USD));
        assertThrows(NullPointerException.class, () -> new Money(BigDecimal.TEN, null));
    }
}
