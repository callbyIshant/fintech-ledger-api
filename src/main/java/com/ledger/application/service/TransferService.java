package com.ledger.application.service;

import com.ledger.adapter.out.redis.RedisIdempotencyAdapter;
import com.ledger.application.port.in.TransferFundsUseCase;
import com.ledger.application.port.out.AccountRepositoryPort;
import com.ledger.application.port.out.IdempotencyPort;
import com.ledger.application.port.out.TransactionRepositoryPort;
import com.ledger.core.domain.Account;
import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.AccountNotFoundException;
import com.ledger.core.domain.CurrencyMismatchException;
import com.ledger.core.domain.LedgerEntry;
import com.ledger.core.domain.Transaction;
import com.ledger.core.domain.TransactionId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class TransferService implements TransferFundsUseCase {

    private final AccountRepositoryPort accountRepositoryPort;
    private final TransactionRepositoryPort transactionRepositoryPort;
    private final IdempotencyPort idempotencyPort;

    public TransferService(AccountRepositoryPort accountRepositoryPort,
                           TransactionRepositoryPort transactionRepositoryPort,
                           IdempotencyPort idempotencyPort) {
        this.accountRepositoryPort = accountRepositoryPort;
        this.transactionRepositoryPort = transactionRepositoryPort;
        this.idempotencyPort = idempotencyPort;
    }

    @Override
    @Transactional
    public TransferResult transferFunds(TransferFundsCommand command) {
        Objects.requireNonNull(command, "TransferFundsCommand cannot be null");

        String payload = computePayload(command);
        String payloadHash = RedisIdempotencyAdapter.computeHash(payload);

        // 1. Check idempotency cache
        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            Optional<TransferResult> cached = idempotencyPort.find(
                    command.tenantId().value(),
                    command.idempotencyKey(),
                    payloadHash,
                    TransferResult.class
            );
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        // 2. Deadlock-free pessimistic locking in deterministic UUID ASC order
        List<AccountId> sortedAccountIds = Stream.of(command.sourceAccountId(), command.destinationAccountId())
                .sorted(Comparator.comparing(a -> a.value().toString()))
                .toList();

        List<Account> lockedAccounts = accountRepositoryPort.findByTenantIdAndAccountIdsForUpdate(
                command.tenantId(), sortedAccountIds);

        Account sourceAccount = lockedAccounts.stream()
                .filter(a -> a.getId().equals(command.sourceAccountId()))
                .findFirst()
                .orElseThrow(() -> new AccountNotFoundException(command.tenantId(), command.sourceAccountId()));

        Account destAccount = lockedAccounts.stream()
                .filter(a -> a.getId().equals(command.destinationAccountId()))
                .findFirst()
                .orElseThrow(() -> new AccountNotFoundException(command.tenantId(), command.destinationAccountId()));

        // 3. Currency checks
        if (!sourceAccount.getCurrency().equals(destAccount.getCurrency())) {
            throw new CurrencyMismatchException(sourceAccount.getCurrency(), destAccount.getCurrency());
        }
        if (!command.amount().currency().equals(sourceAccount.getCurrency())) {
            throw new CurrencyMismatchException(sourceAccount.getCurrency(), command.amount().currency());
        }

        // 4. Update balances (domain logic enforces active status and sufficient balance)
        sourceAccount.debit(command.amount());
        destAccount.credit(command.amount());

        // 5. Create balanced transaction aggregate
        TransactionId txId = TransactionId.random();
        Transaction tx = Transaction.create(txId, command.tenantId(), command.description());
        Instant timestamp = Instant.now();

        tx.addEntry(LedgerEntry.debit(sourceAccount.getId(), command.amount(), timestamp,
                "Transfer to " + destAccount.getId().value()));
        tx.addEntry(LedgerEntry.credit(destAccount.getId(), command.amount(), timestamp,
                "Transfer from " + sourceAccount.getId().value()));

        tx.finalizeTransaction();

        // 6. Persist atomically
        accountRepositoryPort.save(sourceAccount);
        accountRepositoryPort.save(destAccount);
        transactionRepositoryPort.save(tx);

        // 7. Store in idempotency store
        TransferResult result = new TransferResult(
                txId,
                command.tenantId(),
                command.sourceAccountId(),
                command.destinationAccountId(),
                command.amount(),
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

    private String computePayload(TransferFundsCommand cmd) {
        return String.format("%s:%s:%s:%s",
                cmd.sourceAccountId().value(),
                cmd.destinationAccountId().value(),
                cmd.amount().amount().toPlainString(),
                cmd.amount().currency().getCurrencyCode());
    }
}
