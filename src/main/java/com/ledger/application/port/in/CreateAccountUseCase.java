package com.ledger.application.port.in;

import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.AccountStatus;
import com.ledger.core.domain.Money;
import com.ledger.core.domain.TenantId;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

public interface CreateAccountUseCase {

    record CreateAccountCommand(
            TenantId tenantId,
            Currency currency,
            Money initialBalance
    ) {
        public CreateAccountCommand {
            Objects.requireNonNull(tenantId, "tenantId cannot be null");
            Objects.requireNonNull(currency, "currency cannot be null");
        }
    }

    record AccountResult(
            AccountId accountId,
            TenantId tenantId,
            Currency currency,
            Money balance,
            AccountStatus status,
            Instant createdAt
    ) {}

    AccountResult createAccount(CreateAccountCommand command);
}
