package com.ledger.adapter.in.web.exception;

import com.ledger.core.domain.AccountFrozenException;
import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.AccountNotFoundException;
import com.ledger.core.domain.CurrencyMismatchException;
import com.ledger.core.domain.IdempotencyConflictException;
import com.ledger.core.domain.InsufficientFundsException;
import com.ledger.core.domain.Money;
import com.ledger.core.domain.TransactionId;
import com.ledger.core.domain.UnbalancedTransactionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("InsufficientFundsException should map to 422 with INSUFFICIENT_FUNDS errorCode")
    void shouldHandleInsufficientFunds() {
        AccountId accountId = AccountId.random();
        InsufficientFundsException ex = new InsufficientFundsException(
                accountId, Money.of("100.00", "USD"), Money.of("50.00", "USD")
        );

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleInsufficientFunds(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        ProblemDetail body = response.getBody();
        assertNotNull(body);
        assertEquals("INSUFFICIENT_FUNDS", body.getProperties().get("errorCode"));
        assertEquals(accountId.value(), body.getProperties().get("accountId"));
        assertEquals("100.00 USD", body.getProperties().get("requestedAmount"));
        assertEquals("50.00 USD", body.getProperties().get("currentBalance"));
    }

    @Test
    @DisplayName("IdempotencyConflictException should map to 422 with IDEMPOTENCY_CONFLICT errorCode")
    void shouldHandleIdempotencyConflict() {
        IdempotencyConflictException ex = new IdempotencyConflictException("idem-key-999", "Payload mismatch");

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleIdempotencyConflict(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        ProblemDetail body = response.getBody();
        assertNotNull(body);
        assertEquals("IDEMPOTENCY_CONFLICT", body.getProperties().get("errorCode"));
        assertEquals("idem-key-999", body.getProperties().get("idempotencyKey"));
    }

    @Test
    @DisplayName("AccessDeniedException should map to 403 with TENANT_ACCESS_DENIED errorCode")
    void shouldHandleAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("Tenant access restricted");

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleAccessDenied(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        ProblemDetail body = response.getBody();
        assertNotNull(body);
        assertEquals("TENANT_ACCESS_DENIED", body.getProperties().get("errorCode"));
    }

    @Test
    @DisplayName("AccountNotFoundException should map to 404 with ACCOUNT_NOT_FOUND errorCode")
    void shouldHandleAccountNotFound() {
        AccountId accountId = AccountId.random();
        AccountNotFoundException ex = new AccountNotFoundException(accountId);

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleAccountNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ProblemDetail body = response.getBody();
        assertNotNull(body);
        assertEquals("ACCOUNT_NOT_FOUND", body.getProperties().get("errorCode"));
        assertEquals(accountId.value(), body.getProperties().get("accountId"));
    }

    @Test
    @DisplayName("AccountFrozenException should map to 422 with ACCOUNT_FROZEN errorCode")
    void shouldHandleAccountFrozen() {
        AccountId accountId = AccountId.random();
        AccountFrozenException ex = new AccountFrozenException(accountId);

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleAccountFrozen(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        ProblemDetail body = response.getBody();
        assertNotNull(body);
        assertEquals("ACCOUNT_FROZEN", body.getProperties().get("errorCode"));
        assertEquals(accountId.value(), body.getProperties().get("accountId"));
    }

    @Test
    @DisplayName("CurrencyMismatchException should map to 400 with CURRENCY_MISMATCH errorCode")
    void shouldHandleCurrencyMismatch() {
        CurrencyMismatchException ex = new CurrencyMismatchException("USD", "EUR");

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleCurrencyMismatch(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ProblemDetail body = response.getBody();
        assertNotNull(body);
        assertEquals("CURRENCY_MISMATCH", body.getProperties().get("errorCode"));
        assertEquals("USD", body.getProperties().get("expectedCurrency"));
        assertEquals("EUR", body.getProperties().get("actualCurrency"));
    }

    @Test
    @DisplayName("UnbalancedTransactionException should map to 400 with UNBALANCED_TRANSACTION errorCode")
    void shouldHandleUnbalancedTransaction() {
        TransactionId txId = TransactionId.random();
        UnbalancedTransactionException ex = new UnbalancedTransactionException(
                txId, Money.of("100.00", "USD"), Money.of("90.00", "USD")
        );

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleUnbalancedTransaction(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ProblemDetail body = response.getBody();
        assertNotNull(body);
        assertEquals("UNBALANCED_TRANSACTION", body.getProperties().get("errorCode"));
        assertEquals(txId.value(), body.getProperties().get("transactionId"));
        assertEquals("100.00 USD", body.getProperties().get("totalDebits"));
        assertEquals("90.00 USD", body.getProperties().get("totalCredits"));
    }

    @Test
    @DisplayName("MethodArgumentNotValidException should map to 400 with field errors")
    void shouldHandleValidationErrors() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "amount", "must not be null"));

        Method method = getClass().getDeclaredMethod("shouldHandleValidationErrors");
        MethodParameter parameter = new MethodParameter(method, -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ProblemDetail body = response.getBody();
        assertNotNull(body);
        assertEquals("VALIDATION_ERROR", body.getProperties().get("errorCode"));
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) body.getProperties().get("errors");
        assertNotNull(errors);
        assertEquals("must not be null", errors.get("amount"));
    }

    @Test
    @DisplayName("MissingRequestHeaderException should map to 400 with MISSING_HEADER errorCode")
    void shouldHandleMissingHeader() throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod("shouldHandleMissingHeader");
        MethodParameter parameter = new MethodParameter(method, -1);
        MissingRequestHeaderException ex = new MissingRequestHeaderException("Idempotency-Key", parameter);

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleMissingHeader(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ProblemDetail body = response.getBody();
        assertNotNull(body);
        assertEquals("MISSING_HEADER", body.getProperties().get("errorCode"));
        assertEquals("Idempotency-Key", body.getProperties().get("headerName"));
    }
}
