package com.ledger.application.service;

import com.ledger.adapter.out.redis.RedisIdempotencyAdapter;
import com.ledger.application.port.in.CreateAccountUseCase;
import com.ledger.application.port.in.DepositFundsUseCase;
import com.ledger.application.port.out.AccountRepositoryPort;
import com.ledger.application.port.out.IdempotencyPort;
import com.ledger.application.port.out.TransactionRepositoryPort;
import com.ledger.core.domain.Account;
import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.AccountNotFoundException;
import com.ledger.core.domain.CurrencyMismatchException;
import com.ledger.core.domain.LedgerEntry;
import com.ledger.core.domain.Money;
import com.ledger.core.domain.Transaction;
import com.ledger.core.domain.TransactionId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountService implements CreateAccountUseCase, DepositFundsUseCase {

    private static final AccountId SYSTEM_SETTLEMENT_ACCOUNT = AccountId.of(UUID.fromString("00000000-0000-0000-0000-000000000000"));

    private final AccountRepositoryPort accountRepositoryPort;
    private final TransactionRepositoryPort transactionRepositoryPort;
    private final IdempotencyPort idempotencyPort;

    public AccountService(AccountRepositoryPort accountRepositoryPort,
                          TransactionRepositoryPort transactionRepositoryPort,
                          IdempotencyPort idempotencyPort) {
        this.accountRepositoryPort = accountRepositoryPort;
        this.transactionRepositoryPort = transactionRepositoryPort;
        this.idempotencyPort = idempotencyPort;
    }

    @Override
    @Transactional
    public AccountResult createAccount(CreateAccountCommand command) {
        Objects.requireNonNull(command, "CreateAccountCommand cannot be null");
        AccountId accountId = AccountId.random();
        Money initialBalance = command.initialBalance() != null
                ? command.initialBalance()
                : Money.zero(command.currency());

        Account account = Account.create(accountId, command.tenantId(), command.currency(), initialBalance);
        Account saved = accountRepositoryPort.save(account);

        return new AccountResult(
                saved.getId(),
                saved.getTenantId(),
                saved.getCurrency(),
                saved.getBalance(),
                saved.getStatus(),
                saved.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public DepositResult depositFunds(DepositFundsCommand command) {
        Objects.requireNonNull(command, "DepositFundsCommand cannot be null");

        String payload = String.format("%s:%s:%s",
                command.accountId().value(),
                command.amount().amount().toPlainString(),
                command.amount().currency().getCurrencyCode());
        String payloadHash = RedisIdempotencyAdapter.computeHash(payload);

        // Check idempotency cache
        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            Optional<DepositResult> cached = idempotencyPort.find(
                    command.tenantId().value(),
                    command.idempotencyKey(),
                    payloadHash,
                    DepositResult.class
            );
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        // Fetch account with pessimistic lock
        List<Account> locked = accountRepositoryPort.findByTenantIdAndAccountIdsForUpdate(
                command.tenantId(), List.of(command.accountId()));

        Account account = locked.stream()
                .findFirst()
                .orElseThrow(() -> new AccountNotFoundException(command.tenantId(), command.accountId()));

        if (!command.amount().currency().equals(account.getCurrency())) {
            throw new CurrencyMismatchException(account.getCurrency(), command.amount().currency());
        }

        // Domain credit enforcement
        account.credit(command.amount());

        // Create balanced ledger transaction
        TransactionId txId = TransactionId.random();
        Transaction tx = Transaction.create(txId, command.tenantId(),
                command.description() != null ? command.description() : "Deposit funds");
        Instant timestamp = Instant.now();

        tx.addEntry(LedgerEntry.debit(SYSTEM_SETTLEMENT_ACCOUNT, command.amount(), timestamp, "Deposit settlement"));
        tx.addEntry(LedgerEntry.credit(account.getId(), command.amount(), timestamp, "Deposit credit"));
        tx.finalizeTransaction();

        accountRepositoryPort.save(account);
        transactionRepositoryPort.save(tx);

        DepositResult result = new DepositResult(
                txId,
                command.tenantId(),
                command.accountId(),
                command.amount(),
                account.getBalance(),
                command.idempotencyKey(),
                tx.getFinalizedAt()
        );

        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            idempotencyPort.save(
                    command.tenantId().value(),
                    command.idempotencyKey(),
                    payloadHash,
                    result,
                    Duration.ofHours(24)
            );
        }

        return result;
    }
}
