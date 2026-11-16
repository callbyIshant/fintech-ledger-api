package com.ledger.application.port.in;

import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.Money;
import com.ledger.core.domain.TenantId;
import com.ledger.core.domain.TransactionId;

import java.time.Instant;
import java.util.Objects;

public interface TransferFundsUseCase {

    record TransferFundsCommand(
            TenantId tenantId,
            AccountId sourceAccountId,
            AccountId destinationAccountId,
            Money amount,
            String idempotencyKey,
            String description
    ) {
        public TransferFundsCommand {
            Objects.requireNonNull(tenantId, "tenantId cannot be null");
            Objects.requireNonNull(sourceAccountId, "sourceAccountId cannot be null");
            Objects.requireNonNull(destinationAccountId, "destinationAccountId cannot be null");
            Objects.requireNonNull(amount, "amount cannot be null");
            if (sourceAccountId.equals(destinationAccountId)) {
                throw new IllegalArgumentException("Source and destination accounts must be distinct: " + sourceAccountId);
            }
        }
    }

    record TransferResult(
            TransactionId transactionId,
            TenantId tenantId,
            AccountId sourceAccountId,
            AccountId destinationAccountId,
            Money amount,
            String idempotencyKey,
            Instant completedAt
    ) {}

    TransferResult transferFunds(TransferFundsCommand command);
}
