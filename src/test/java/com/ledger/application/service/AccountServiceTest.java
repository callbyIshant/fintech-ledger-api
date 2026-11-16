package com.ledger.application.service;

import com.ledger.application.port.in.CreateAccountUseCase.CreateAccountCommand;
import com.ledger.application.port.in.CreateAccountUseCase.AccountResult;
import com.ledger.application.port.in.DepositFundsUseCase.DepositFundsCommand;
import com.ledger.application.port.in.DepositFundsUseCase.DepositResult;
import com.ledger.application.port.out.AccountRepositoryPort;
import com.ledger.application.port.out.IdempotencyPort;
import com.ledger.application.port.out.TransactionRepositoryPort;
import com.ledger.core.domain.Account;
import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.AccountNotFoundException;
import com.ledger.core.domain.AccountStatus;
import com.ledger.core.domain.CurrencyMismatchException;
import com.ledger.core.domain.Money;
import com.ledger.core.domain.TenantId;
import com.ledger.core.domain.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("AccountService Unit Tests")
class AccountServiceTest {

    private AccountRepositoryPort accountRepositoryPort;
    private TransactionRepositoryPort transactionRepositoryPort;
    private IdempotencyPort idempotencyPort;
    private AccountService accountService;

    private TenantId tenantId;
    private Currency usd;

    @BeforeEach
    void setUp() {
        accountRepositoryPort = mock(AccountRepositoryPort.class);
        transactionRepositoryPort = mock(TransactionRepositoryPort.class);
        idempotencyPort = mock(IdempotencyPort.class);
        accountService = new AccountService(accountRepositoryPort, transactionRepositoryPort, idempotencyPort);

        tenantId = TenantId.of("tenant-corp");
        usd = Currency.getInstance("USD");
    }

    @Test
    @DisplayName("Should create account with specified initial balance")
    void shouldCreateAccount() {
        when(accountRepositoryPort.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateAccountCommand command = new CreateAccountCommand(
                tenantId, usd, Money.of("100.00", "USD")
        );

        AccountResult result = accountService.createAccount(command);

        assertNotNull(result);
        assertEquals(tenantId, result.tenantId());
        assertEquals(usd, result.currency());
        assertEquals(Money.of("100.00", "USD"), result.balance());
        assertEquals(AccountStatus.ACTIVE, result.status());
        verify(accountRepositoryPort, times(1)).save(any(Account.class));
    }

    @Test
    @DisplayName("Should deposit funds into account and create balanced transaction")
    void shouldDepositFunds() {
        AccountId accountId = AccountId.random();
        Account account = new Account(accountId, tenantId, usd, Money.of("50.00", "USD"), AccountStatus.ACTIVE, Instant.now(), Instant.now(), 0L);

        when(idempotencyPort.find(anyString(), anyString(), anyString(), eq(DepositResult.class)))
                .thenReturn(Optional.empty());

        when(accountRepositoryPort.findByTenantIdAndAccountIdsForUpdate(eq(tenantId), anyList()))
                .thenReturn(List.of(account));

        DepositFundsCommand command = new DepositFundsCommand(
                tenantId, accountId, Money.of("150.00", "USD"), "key-dep-1", "External deposit"
        );

        DepositResult result = accountService.depositFunds(command);

        assertNotNull(result);
        assertEquals(Money.of("150.00", "USD"), result.depositedAmount());
        assertEquals(Money.of("200.00", "USD"), result.newBalance());
        assertEquals("key-dep-1", result.idempotencyKey());

        verify(accountRepositoryPort, times(1)).save(account);
        verify(transactionRepositoryPort, times(1)).save(any(Transaction.class));
        verify(idempotencyPort, times(1)).save(eq(tenantId.value()), eq("key-dep-1"), anyString(), eq(result), any(Duration.class));
    }

    @Test
    @DisplayName("Should return cached DepositResult when duplicate idempotency key is submitted")
    void shouldReturnCachedDepositResult() {
        AccountId accountId = AccountId.random();
        DepositResult cached = new DepositResult(null, tenantId, accountId, Money.of("100.00", "USD"), Money.of("300.00", "USD"), "key-dep-dup", Instant.now());

        when(idempotencyPort.find(anyString(), eq("key-dep-dup"), anyString(), eq(DepositResult.class)))
                .thenReturn(Optional.of(cached));

        DepositFundsCommand command = new DepositFundsCommand(
                tenantId, accountId, Money.of("100.00", "USD"), "key-dep-dup", "Deposit"
        );

        DepositResult result = accountService.depositFunds(command);

        assertEquals(cached, result);
        verifyNoInteractions(accountRepositoryPort);
        verifyNoInteractions(transactionRepositoryPort);
    }

    @Test
    @DisplayName("Should throw CurrencyMismatchException when depositing wrong currency")
    void shouldRejectDepositWithWrongCurrency() {
        AccountId accountId = AccountId.random();
        Account account = new Account(accountId, tenantId, usd, Money.of("50.00", "USD"), AccountStatus.ACTIVE, Instant.now(), Instant.now(), 0L);

        when(idempotencyPort.find(anyString(), anyString(), anyString(), eq(DepositResult.class)))
                .thenReturn(Optional.empty());

        when(accountRepositoryPort.findByTenantIdAndAccountIdsForUpdate(eq(tenantId), anyList()))
                .thenReturn(List.of(account));

        DepositFundsCommand command = new DepositFundsCommand(
                tenantId, accountId, Money.of("50.00", "EUR"), "key-eur", "EUR deposit"
        );

        assertThrows(CurrencyMismatchException.class, () -> accountService.depositFunds(command));
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException when account does not exist")
    void shouldThrowWhenDepositAccountMissing() {
        AccountId accountId = AccountId.random();
        when(accountRepositoryPort.findByTenantIdAndAccountIdsForUpdate(eq(tenantId), anyList()))
                .thenReturn(List.of());

        DepositFundsCommand command = new DepositFundsCommand(
                tenantId, accountId, Money.of("50.00", "USD"), "key-none", "Deposit"
        );

        assertThrows(AccountNotFoundException.class, () -> accountService.depositFunds(command));
    }
}
