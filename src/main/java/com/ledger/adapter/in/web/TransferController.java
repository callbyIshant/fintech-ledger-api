package com.ledger.adapter.in.web;

import com.ledger.application.context.TenantContext;
import com.ledger.application.port.in.TransferFundsUseCase;
import com.ledger.application.port.in.TransferFundsUseCase.TransferFundsCommand;
import com.ledger.application.port.in.TransferFundsUseCase.TransferResult;
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
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers", description = "Atomic idempotent double-entry money transfers")
public class TransferController {

    private final TransferFundsUseCase transferFundsUseCase;

    public TransferController(TransferFundsUseCase transferFundsUseCase) {
        this.transferFundsUseCase = transferFundsUseCase;
    }

    public record TransferRequest(
            @NotNull(message = "sourceAccountId must not be null")
            UUID sourceAccountId,

            @NotNull(message = "destinationAccountId must not be null")
            UUID destinationAccountId,

            @NotNull(message = "amount must not be null")
            @Positive(message = "amount must be strictly positive")
            BigDecimal amount,

            @NotBlank(message = "currency must not be blank")
            String currency,

            String description
    ) {}

    public record TransferResponse(
            UUID transactionId,
            String tenantId,
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal amount,
            String currency,
            String idempotencyKey,
            Instant completedAt
    ) {}

    @PostMapping
    @Operation(summary = "Transfer funds", description = "Executes an atomic transfer between two accounts with mandatory idempotency key")
    public ResponseEntity<TransferResponse> transferFunds(
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody TransferRequest request
    ) {
        TenantId tenantId = TenantId.of(TenantContext.getRequiredTenantId());
        Currency currency = Currency.getInstance(request.currency().toUpperCase());
        Money amount = Money.of(request.amount(), currency);

        TransferFundsCommand command = new TransferFundsCommand(
                tenantId,
                AccountId.of(request.sourceAccountId()),
                AccountId.of(request.destinationAccountId()),
                amount,
                idempotencyKey,
                request.description()
        );

        TransferResult result = transferFundsUseCase.transferFunds(command);

        TransferResponse response = new TransferResponse(
                result.transactionId() != null ? result.transactionId().value() : null,
                result.tenantId().value(),
                result.sourceAccountId().value(),
                result.destinationAccountId().value(),
                result.amount().amount(),
                result.amount().currency().getCurrencyCode(),
                result.idempotencyKey(),
                result.completedAt()
        );

        return ResponseEntity.ok(response);
    }
}
