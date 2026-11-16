package com.ledger.application.service;

import com.ledger.application.port.in.TransferFundsUseCase.TransferFundsCommand;
import com.ledger.application.port.in.TransferFundsUseCase.TransferResult;
import com.ledger.application.port.out.AccountRepositoryPort;
import com.ledger.application.port.out.IdempotencyPort;
import com.ledger.application.port.out.TransactionRepositoryPort;
import com.ledger.core.domain.Account;
import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.AccountStatus;
import com.ledger.core.domain.EntryType;
import com.ledger.core.domain.InsufficientFundsException;
import com.ledger.core.domain.LedgerEntry;
import com.ledger.core.domain.Money;
import com.ledger.core.domain.TenantId;
import com.ledger.core.domain.Transaction;
import com.ledger.core.domain.TransactionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TransferService High Concurrency Stress Test")
class TransferConcurrencyStressTest {

    private TransferService transferService;
    private InMemoryAccountRepository accountRepository;
    private InMemoryTransactionRepository transactionRepository;
    private NoOpIdempotencyPort idempotencyPort;

    private TenantId tenantId;
    private AccountId sourceAccountId;
    private AccountId destAccountId;
    private Currency inr;

    @BeforeEach
    void setUp() {
        tenantId = TenantId.of("tenant-stress");
        sourceAccountId = AccountId.random();
        destAccountId = AccountId.random();
        inr = Currency.getInstance("INR");

        accountRepository = new InMemoryAccountRepository();
        transactionRepository = new InMemoryTransactionRepository();
        idempotencyPort = new NoOpIdempotencyPort();

        transferService = new TransferService(
                accountRepository,
                transactionRepository,
                idempotencyPort
        );

        // Seed Source account with 10,000.00 INR and Destination account with 0.00 INR
        Account source = new Account(
                sourceAccountId, tenantId, inr,
                Money.of("10000.00", "INR"), AccountStatus.ACTIVE,
                Instant.now(), Instant.now(), 0L
        );
        Account destination = new Account(
                destAccountId, tenantId, inr,
                Money.of("0.00", "INR"), AccountStatus.ACTIVE,
                Instant.now(), Instant.now(), 0L
        );
        accountRepository.save(source);
        accountRepository.save(destination);
    }

    @Test
    @DisplayName("50 concurrent threads transferring 300.00 INR: 33 succeed, 17 fail, final balance 100.0000, debits == credits")
    void shouldHandleFiftyConcurrentTransfersCorrectly() throws InterruptedException {
        int totalThreads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger insufficientFundsCount = new AtomicInteger(0);
        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            final String idempotencyKey = "stress-tx-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize all 50 threads to start together
                    TransferFundsCommand command = new TransferFundsCommand(
                            tenantId,
                            sourceAccountId,
                            destAccountId,
                            Money.of("300.00", "INR"),
                            idempotencyKey,
                            "Concurrent batch transfer"
                    );
                    TransferResult result = transferService.transferFunds(command);
                    if (result != null) {
                        successCount.incrementAndGet();
                    }
                } catch (InsufficientFundsException e) {
                    insufficientFundsCount.incrementAndGet();
                } catch (Exception e) {
                    unexpectedErrorCount.incrementAndGet();
                } finally {
                    accountRepository.releaseLocks();
                    finishLatch.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        finishLatch.await();
        executor.shutdown();

        // 1. Assert thread completion distribution
        assertEquals(0, unexpectedErrorCount.get(), "Unexpected errors encountered during concurrent execution");
        assertEquals(33, successCount.get(), "Exactly 33 transfers of 300.00 INR should succeed from 10,000.00 INR");
        assertEquals(17, insufficientFundsCount.get(), "Exactly 17 transfers must fail with InsufficientFundsException");

        // 2. Assert final account balances
        Account finalSource = accountRepository.findByTenantIdAndAccountId(tenantId, sourceAccountId).orElseThrow();
        Account finalDest = accountRepository.findByTenantIdAndAccountId(tenantId, destAccountId).orElseThrow();

        assertEquals(0, new BigDecimal("100.0000").compareTo(finalSource.getBalance().amount()),
                "Final source account balance must be exactly 100.0000 INR");
        assertEquals(0, new BigDecimal("9900.0000").compareTo(finalDest.getBalance().amount()),
                "Final destination account balance must be exactly 9900.0000 INR");

        // 3. Assert global ledger invariant: sum(debits) == sum(credits)
        BigDecimal totalDebits = transactionRepository.getAllEntries().stream()
                .filter(e -> e.type() == EntryType.DEBIT)
                .map(e -> e.amount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = transactionRepository.getAllEntries().stream()
                .filter(e -> e.type() == EntryType.CREDIT)
                .map(e -> e.amount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, new BigDecimal("9900.0000").compareTo(totalDebits));
        assertEquals(0, new BigDecimal("9900.0000").compareTo(totalCredits));
        assertEquals(0, totalDebits.compareTo(totalCredits), "Total ledger debits must strictly equal total credits");
    }

    /**
     * Thread-safe in-memory account repository simulating database row locking.
     */
    private static class InMemoryAccountRepository implements AccountRepositoryPort {
        private final Map<UUID, Account> storage = new ConcurrentHashMap<>();
        private final Map<UUID, ReentrantLock> accountLocks = new ConcurrentHashMap<>();
        private final ThreadLocal<List<ReentrantLock>> threadLocks = ThreadLocal.withInitial(ArrayList::new);

        @Override
        public Account save(Account account) {
            storage.put(account.getId().value(), account);
            return account;
        }

        @Override
        public Optional<Account> findById(AccountId accountId) {
            return Optional.ofNullable(storage.get(accountId.value()));
        }

        @Override
        public Optional<Account> findByTenantIdAndAccountId(TenantId tenantId, AccountId accountId) {
            Account acc = storage.get(accountId.value());
            return (acc != null && acc.getTenantId().equals(tenantId)) ? Optional.of(acc) : Optional.empty();
        }

        @Override
        public List<Account> findByTenantIdAndAccountIdsForUpdate(TenantId tenantId, Collection<AccountId> accountIds) {
            List<Account> accounts = new ArrayList<>();
            for (AccountId id : accountIds) {
                ReentrantLock lock = accountLocks.computeIfAbsent(id.value(), k -> new ReentrantLock());
                lock.lock();
                threadLocks.get().add(lock);
                Account acc = storage.get(id.value());
                if (acc != null && acc.getTenantId().equals(tenantId)) {
                    accounts.add(acc);
                }
            }
            return accounts;
        }

        public void releaseLocks() {
            List<ReentrantLock> locks = threadLocks.get();
            for (int i = locks.size() - 1; i >= 0; i--) {
                locks.get(i).unlock();
            }
            locks.clear();
        }

        @Override
        public boolean existsById(AccountId accountId) {
            return storage.containsKey(accountId.value());
        }
    }

    /**
     * Thread-safe in-memory transaction repository recording all ledger entries.
     */
    private static class InMemoryTransactionRepository implements TransactionRepositoryPort {
        private final List<Transaction> transactions = Collections.synchronizedList(new ArrayList<>());
        private final List<LedgerEntry> allEntries = Collections.synchronizedList(new ArrayList<>());

        @Override
        public Transaction save(Transaction transaction) {
            transactions.add(transaction);
            allEntries.addAll(transaction.getEntries());
            return transaction;
        }

        @Override
        public Optional<Transaction> findById(TransactionId transactionId) {
            return transactions.stream()
                    .filter(t -> t.getId().equals(transactionId))
                    .findFirst();
        }

        @Override
        public Optional<Transaction> findByTenantIdAndTransactionId(TenantId tenantId, TransactionId transactionId) {
            return transactions.stream()
                    .filter(t -> t.getTenantId().equals(tenantId) && t.getId().equals(transactionId))
                    .findFirst();
        }

        @Override
        public List<Transaction> findByTenantId(TenantId tenantId) {
            return transactions.stream()
                    .filter(t -> t.getTenantId().equals(tenantId))
                    .toList();
        }

        public List<LedgerEntry> getAllEntries() {
            return new ArrayList<>(allEntries);
        }
    }

    /**
     * No-op idempotency port for concurrency stress test.
     */
    private static class NoOpIdempotencyPort implements IdempotencyPort {
        @Override
        public <T> Optional<T> find(String tenantId, String idempotencyKey, String payloadHash, Class<T> responseType) {
            return Optional.empty();
        }

        @Override
        public void save(String tenantId, String idempotencyKey, String payloadHash, Object response, Duration ttl) {
        }
    }
}
