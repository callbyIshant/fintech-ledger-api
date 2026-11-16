package com.ledger.adapter.in.web.exception;

import com.ledger.core.domain.AccountFrozenException;
import com.ledger.core.domain.AccountNotFoundException;
import com.ledger.core.domain.CurrencyMismatchException;
import com.ledger.core.domain.IdempotencyConflictException;
import com.ledger.core.domain.InsufficientFundsException;
import com.ledger.core.domain.UnbalancedTransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ProblemDetail> handleInsufficientFunds(InsufficientFundsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Insufficient Funds");
        problem.setType(URI.create("urn:problem:insufficient-funds"));
        problem.setProperty("errorCode", "INSUFFICIENT_FUNDS");
        problem.setProperty("timestamp", Instant.now());
        if (ex.getAccountId() != null) {
            problem.setProperty("accountId", ex.getAccountId().value());
        }
        if (ex.getRequestedAmount() != null) {
            problem.setProperty("requestedAmount", ex.getRequestedAmount().toString());
        }
        if (ex.getCurrentBalance() != null) {
            problem.setProperty("currentBalance", ex.getCurrentBalance().toString());
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyConflict(IdempotencyConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Idempotency Conflict");
        problem.setType(URI.create("urn:problem:idempotency-conflict"));
        problem.setProperty("errorCode", "IDEMPOTENCY_CONFLICT");
        problem.setProperty("timestamp", Instant.now());
        if (ex.getIdempotencyKey() != null) {
            problem.setProperty("idempotencyKey", ex.getIdempotencyKey());
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Forbidden");
        problem.setType(URI.create("urn:problem:tenant-access-denied"));
        problem.setProperty("errorCode", "TENANT_ACCESS_DENIED");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleAccountNotFound(AccountNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Account Not Found");
        problem.setType(URI.create("urn:problem:account-not-found"));
        problem.setProperty("errorCode", "ACCOUNT_NOT_FOUND");
        problem.setProperty("timestamp", Instant.now());
        if (ex.getAccountId() != null) {
            problem.setProperty("accountId", ex.getAccountId().value());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(AccountFrozenException.class)
    public ResponseEntity<ProblemDetail> handleAccountFrozen(AccountFrozenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Account Frozen");
        problem.setType(URI.create("urn:problem:account-frozen"));
        problem.setProperty("errorCode", "ACCOUNT_FROZEN");
        problem.setProperty("timestamp", Instant.now());
        if (ex.getAccountId() != null) {
            problem.setProperty("accountId", ex.getAccountId().value());
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    public ResponseEntity<ProblemDetail> handleCurrencyMismatch(CurrencyMismatchException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Currency Mismatch");
        problem.setType(URI.create("urn:problem:currency-mismatch"));
        problem.setProperty("errorCode", "CURRENCY_MISMATCH");
        problem.setProperty("timestamp", Instant.now());
        if (ex.getExpectedCurrency() != null) {
            problem.setProperty("expectedCurrency", ex.getExpectedCurrency());
        }
        if (ex.getActualCurrency() != null) {
            problem.setProperty("actualCurrency", ex.getActualCurrency());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(UnbalancedTransactionException.class)
    public ResponseEntity<ProblemDetail> handleUnbalancedTransaction(UnbalancedTransactionException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Unbalanced Transaction");
        problem.setType(URI.create("urn:problem:unbalanced-transaction"));
        problem.setProperty("errorCode", "UNBALANCED_TRANSACTION");
        problem.setProperty("timestamp", Instant.now());
        if (ex.getTransactionId() != null) {
            problem.setProperty("transactionId", ex.getTransactionId().value());
        }
        if (ex.getTotalDebits() != null) {
            problem.setProperty("totalDebits", ex.getTotalDebits().toString());
        }
        if (ex.getTotalCredits() != null) {
            problem.setProperty("totalCredits", ex.getTotalCredits().toString());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed for request payload");
        problem.setTitle("Validation Error");
        problem.setType(URI.create("urn:problem:validation-error"));
        problem.setProperty("errorCode", "VALIDATION_ERROR");
        problem.setProperty("timestamp", Instant.now());

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        problem.setProperty("errors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingHeader(MissingRequestHeaderException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Missing Required Header");
        problem.setType(URI.create("urn:problem:missing-header"));
        problem.setProperty("errorCode", "MISSING_HEADER");
        problem.setProperty("headerName", ex.getHeaderName());
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed JSON request payload");
        problem.setTitle("Malformed Request Body");
        problem.setType(URI.create("urn:problem:malformed-request"));
        problem.setProperty("errorCode", "MALFORMED_JSON");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        problem.setType(URI.create("urn:problem:bad-request"));
        problem.setProperty("errorCode", "BAD_REQUEST");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex) {
        log.error("Unhandled exception caught by GlobalExceptionHandler: ", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal server error occurred");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("urn:problem:internal-error"));
        problem.setProperty("errorCode", "INTERNAL_ERROR");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
