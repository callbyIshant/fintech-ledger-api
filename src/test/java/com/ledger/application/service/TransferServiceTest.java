package com.ledger.application.service;

import com.ledger.application.port.in.TransferFundsUseCase.TransferFundsCommand;
import com.ledger.application.port.in.TransferFundsUseCase.TransferResult;
import com.ledger.application.port.out.AccountRepositoryPort;
import com.ledger.application.port.out.IdempotencyPort;
import com.ledger.application.port.out.TransactionRepositoryPort;
import com.ledger.core.domain.Account;
import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.AccountNotFoundException;
import com.ledger.core.domain.AccountStatus;
import com.ledger.core.domain.CurrencyMismatchException;
import com.ledger.core.domain.IdempotencyConflictException;
import com.ledger.core.domain.InsufficientFundsException;
import com.ledger.core.domain.Money;
import com.ledger.core.domain.TenantId;
import com.ledger.core.domain.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("TransferService Unit Tests")
class TransferServiceTest {

    private AccountRepositoryPort accountRepositoryPort;
    private TransactionRepositoryPort transactionRepositoryPort;
    private IdempotencyPort idempotencyPort;
    private TransferService transferService;

    private TenantId tenantId;
    private Currency usd;
    private AccountId sourceAccountId;
    private AccountId destAccountId;
    private Account sourceAccount;
    private Account destAccount;

    @BeforeEach
    void setUp() {
        accountRepositoryPort = mock(AccountRepositoryPort.class);
        transactionRepositoryPort = mock(TransactionRepositoryPort.class);
        idempotencyPort = mock(IdempotencyPort.class);
        transferService = new TransferService(accountRepositoryPort, transactionRepositoryPort, idempotencyPort);

        tenantId = TenantId.of("tenant-ledger");
        usd = Currency.getInstance("USD");

        // Two distinct accounts
        sourceAccountId = AccountId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        destAccountId = AccountId.of(UUID.fromString("22222222-2222-2222-2222-222222222222"));

        sourceAccount = new Account(sourceAccountId, tenantId, usd, Money.of("500.00", "USD"), AccountStatus.ACTIVE, Instant.now(), Instant.now(), 0L);
        destAccount = new Account(destAccountId, tenantId, usd, Money.of("100.00", "USD"), AccountStatus.ACTIVE, Instant.now(), Instant.now(), 0L);
    }

    @Test
    @DisplayName("Should execute transfer successfully, debit source, credit dest, and persist balanced transaction")
    void shouldExecuteTransferSuccessfully() {
        when(idempotencyPort.find(anyString(), anyString(), anyString(), eq(TransferResult.class)))
                .thenReturn(Optional.empty());

        when(accountRepositoryPort.findByTenantIdAndAccountIdsForUpdate(eq(tenantId), anyList()))
                .thenReturn(List.of(sourceAccount, destAccount));

        TransferFundsCommand command = new TransferFundsCommand(
                tenantId, sourceAccountId, destAccountId, Money.of("200.00", "USD"), "key-1", "Invoice payment"
        );

        TransferResult result = transferService.transferFunds(command);

        assertNotNull(result);
        assertEquals(Money.of("200.00", "USD"), result.amount());
        assertEquals("key-1", result.idempotencyKey());

        // Source account debited
        assertEquals(Money.of("300.00", "USD"), sourceAccount.getBalance());
        // Destination account credited
        assertEquals(Money.of("300.00", "USD"), destAccount.getBalance());

        // Accounts and transaction saved
        verify(accountRepositoryPort, times(1)).save(sourceAccount);
        verify(accountRepositoryPort, times(1)).save(destAccount);
        verify(transactionRepositoryPort, times(1)).save(any(Transaction.class));

        // Cached in idempotency store
        verify(idempotencyPort, times(1)).save(
                eq(tenantId.value()), eq("key-1"), anyString(), eq(result), eq(Duration.ofHours(24))
        );
    }

    @Test
    @DisplayName("Should enforce deterministic UUID lock ordering: source > dest orders ascending")
    @SuppressWarnings("unchecked")
    void shouldEnforceDeterministicLockOrdering() {
        // Arrange source with greater UUID than dest
        AccountId higherId = AccountId.of(UUID.fromString("99999999-9999-9999-9999-999999999999"));
        AccountId lowerId = AccountId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        Account accHigh = new Account(higherId, tenantId, usd, Money.of("500.00", "USD"), AccountStatus.ACTIVE, Instant.now(), Instant.now(), 0L);
        Account accLow = new Account(lowerId, tenantId, usd, Money.of("100.00", "USD"), AccountStatus.ACTIVE, Instant.now(), Instant.now(), 0L);

        when(accountRepositoryPort.findByTenantIdAndAccountIdsForUpdate(eq(tenantId), anyList()))
                .thenReturn(List.of(accLow, accHigh));

        TransferFundsCommand command = new TransferFundsCommand(
                tenantId, higherId, lowerId, Money.of("50.00", "USD"), "key-order", "Lock order test"
        );

        transferService.transferFunds(command);

        ArgumentCaptor<List<AccountId>> captor = ArgumentCaptor.forClass(List.class);
        verify(accountRepositoryPort).findByTenantIdAndAccountIdsForUpdate(eq(tenantId), captor.capture());

        List<AccountId> lockedIds = captor.getValue();
        assertEquals(2, lockedIds.size());
        // Must be sorted in ASC order: lowerId first, higherId second
        assertEquals(lowerId, lockedIds.get(0));
        assertEquals(higherId, lockedIds.get(1));
    }

    @Test
    @DisplayName("Should return cached TransferResult without executing transfer when duplicate key arrives (idempotent replay)")
    void shouldReturnCachedOnIdempotentReplay() {
        TransferResult cachedResult = new TransferResult(
                null, tenantId, sourceAccountId, destAccountId, Money.of("50.00", "USD"), "key-dup", Instant.now()
        );

        when(idempotencyPort.find(anyString(), eq("key-dup"), anyString(), eq(TransferResult.class)))
                .thenReturn(Optional.of(cachedResult));

        TransferFundsCommand command = new TransferFundsCommand(
                tenantId, sourceAccountId, destAccountId, Money.of("50.00", "USD"), "key-dup", "Replay"
        );

        TransferResult result = transferService.transferFunds(command);

        assertEquals(cachedResult, result);
        // Repository and account mutations must NOT have occurred
        verifyNoInteractions(accountRepositoryPort);
        verifyNoInteractions(transactionRepositoryPort);
    }

    @Test
    @DisplayName("Should throw IdempotencyConflictException when duplicate key arrives with different payload")
    void shouldThrowOnPayloadMismatch() {
        when(idempotencyPort.find(anyString(), eq("key-conflict"), anyString(), eq(TransferResult.class)))
                .thenThrow(new IdempotencyConflictException("key-conflict", "Payload hash mismatch"));

        TransferFundsCommand command = new TransferFundsCommand(
                tenantId, sourceAccountId, destAccountId, Money.of("75.00", "USD"), "key-conflict", "Mismatch"
        );

        assertThrows(IdempotencyConflictException.class, () -> transferService.transferFunds(command));
        verifyNoInteractions(accountRepositoryPort);
    }

    @Test
    @DisplayName("Should reject self-transfer attempt")
    void shouldRejectSelfTransfer() {
        assertThrows(IllegalArgumentException.class, () ->
                new TransferFundsCommand(tenantId, sourceAccountId, sourceAccountId, Money.of("10.00", "USD"), "key-self", "Self"));
    }

    @Test
    @DisplayName("Should throw InsufficientFundsException when source balance is inadequate")
    void shouldRejectInsufficientFunds() {
        when(accountRepositoryPort.findByTenantIdAndAccountIdsForUpdate(eq(tenantId), anyList()))
                .thenReturn(List.of(sourceAccount, destAccount));

        TransferFundsCommand command = new TransferFundsCommand(
                tenantId, sourceAccountId, destAccountId, Money.of("999.00", "USD"), "key-overdraft", "Overdraft"
        );

        assertThrows(InsufficientFundsException.class, () -> transferService.transferFunds(command));
        verify(transactionRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("Should throw CurrencyMismatchException when accounts have different currencies")
    void shouldRejectDifferentAccountCurrencies() {
        Account eurAccount = new Account(destAccountId, tenantId, Currency.getInstance("EUR"), Money.of("100.00", "EUR"), AccountStatus.ACTIVE, Instant.now(), Instant.now(), 0L);

        when(accountRepositoryPort.findByTenantIdAndAccountIdsForUpdate(eq(tenantId), anyList()))
                .thenReturn(List.of(sourceAccount, eurAccount));

        TransferFundsCommand command = new TransferFundsCommand(
                tenantId, sourceAccountId, destAccountId, Money.of("50.00", "USD"), "key-curr", "Currency diff"
        );

        assertThrows(CurrencyMismatchException.class, () -> transferService.transferFunds(command));
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException when account is missing")
    void shouldThrowWhenAccountNotFound() {
        when(accountRepositoryPort.findByTenantIdAndAccountIdsForUpdate(eq(tenantId), anyList()))
                .thenReturn(List.of(sourceAccount)); // Destination account missing

        TransferFundsCommand command = new TransferFundsCommand(
                tenantId, sourceAccountId, destAccountId, Money.of("10.00", "USD"), "key-missing", "Missing dest"
        );

        assertThrows(AccountNotFoundException.class, () -> transferService.transferFunds(command));
    }
}
