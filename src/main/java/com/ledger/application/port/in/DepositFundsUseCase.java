package com.ledger.application.port.in;

import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.Money;
import com.ledger.core.domain.TenantId;
import com.ledger.core.domain.TransactionId;

import java.time.Instant;
import java.util.Objects;

public interface DepositFundsUseCase {

    record DepositFundsCommand(
            TenantId tenantId,
            AccountId accountId,
            Money amount,
            String idempotencyKey,
            String description
    ) {
        public DepositFundsCommand {
            Objects.requireNonNull(tenantId, "tenantId cannot be null");
            Objects.requireNonNull(accountId, "accountId cannot be null");
            Objects.requireNonNull(amount, "amount cannot be null");
        }
    }

    record DepositResult(
            TransactionId transactionId,
            TenantId tenantId,
            AccountId accountId,
            Money depositedAmount,
            Money newBalance,
            String idempotencyKey,
            Instant completedAt
    ) {}

    DepositResult depositFunds(DepositFundsCommand command);
}
