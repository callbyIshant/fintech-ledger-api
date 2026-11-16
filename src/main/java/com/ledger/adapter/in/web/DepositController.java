package com.ledger.adapter.in.web;

import com.ledger.application.context.TenantContext;
import com.ledger.application.port.in.DepositFundsUseCase;
import com.ledger.application.port.in.DepositFundsUseCase.DepositFundsCommand;
import com.ledger.application.port.in.DepositFundsUseCase.DepositResult;
import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.Money;
import com.ledger.core.domain.TenantId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deposits")
@Tag(name = "Deposits", description = "Deposit funds into ledger accounts")
public class DepositController {

    private final DepositFundsUseCase depositFundsUseCase;

    public DepositController(DepositFundsUseCase depositFundsUseCase) {
        this.depositFundsUseCase = depositFundsUseCase;
    }

    public record DepositRequest(
            @NotNull(message = "accountId must not be null")
            UUID accountId,

            @NotNull(message = "amount must not be null")
            @Positive(message = "amount must be strictly positive")
            BigDecimal amount,

            @NotBlank(message = "currency must not be blank")
            String currency,

            String description
    ) {}

    public record DepositResponse(
            UUID transactionId,
            String tenantId,
            UUID accountId,
            BigDecimal depositedAmount,
            BigDecimal newBalance,
            String currency,
            String idempotencyKey,
            Instant completedAt
    ) {}

    @PostMapping
    @Operation(summary = "Deposit funds", description = "Credits funds into an account with idempotency protection")
    public ResponseEntity<DepositResponse> depositFunds(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody DepositRequest request
    ) {
        TenantId tenantId = TenantId.of(TenantContext.getRequiredTenantId());
        Currency currency = Currency.getInstance(request.currency().toUpperCase());
        Money amount = Money.of(request.amount(), currency);

        DepositFundsCommand command = new DepositFundsCommand(
                tenantId,
                AccountId.of(request.accountId()),
                amount,
                idempotencyKey,
                request.description()
        );

        DepositResult result = depositFundsUseCase.depositFunds(command);

        DepositResponse response = new DepositResponse(
                result.transactionId() != null ? result.transactionId().value() : null,
                result.tenantId().value(),
                result.accountId().value(),
                result.depositedAmount().amount(),
                result.newBalance().amount(),
                result.depositedAmount().currency().getCurrencyCode(),
                result.idempotencyKey(),
                result.completedAt()
        );

        return ResponseEntity.ok(response);
    }
}
