package com.ledger.adapter.in.web;

import com.ledger.adapter.in.web.security.TenantAccessGuard;
import com.ledger.application.context.TenantContext;
import com.ledger.application.port.in.CreateAccountUseCase;
import com.ledger.application.port.in.CreateAccountUseCase.AccountResult;
import com.ledger.application.port.in.CreateAccountUseCase.CreateAccountCommand;
import com.ledger.application.port.out.AccountRepositoryPort;
import com.ledger.core.domain.Account;
import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.AccountNotFoundException;
import com.ledger.core.domain.Money;
import com.ledger.core.domain.TenantId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "Account creation and balance querying operations")
public class AccountController {

    private final CreateAccountUseCase createAccountUseCase;
    private final AccountRepositoryPort accountRepositoryPort;
    private final TenantAccessGuard tenantAccessGuard;

    public AccountController(CreateAccountUseCase createAccountUseCase,
                             AccountRepositoryPort accountRepositoryPort,
                             TenantAccessGuard tenantAccessGuard) {
        this.createAccountUseCase = createAccountUseCase;
        this.accountRepositoryPort = accountRepositoryPort;
        this.tenantAccessGuard = tenantAccessGuard;
    }

    public record CreateAccountRequest(
            @NotNull(message = "currency must be provided")
            String currency,

            BigDecimal initialBalance
    ) {}

    public record AccountResponse(
            UUID accountId,
            String tenantId,
            String currency,
            BigDecimal balance,
            String status,
            Instant createdAt
    ) {}

    @PostMapping
    @Operation(summary = "Create an account", description = "Creates a new ledger account for the authenticated tenant")
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        TenantId tenantId = TenantId.of(TenantContext.getRequiredTenantId());
        Currency currency = Currency.getInstance(request.currency().toUpperCase());
        Money initialMoney = request.initialBalance() != null
                ? Money.of(request.initialBalance(), currency)
                : Money.zero(currency);

        AccountResult result = createAccountUseCase.createAccount(
                new CreateAccountCommand(tenantId, currency, initialMoney)
        );

        AccountResponse response = new AccountResponse(
                result.accountId().value(),
                result.tenantId().value(),
                result.currency().getCurrencyCode(),
                result.balance().amount(),
                result.status().name(),
                result.createdAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get account details and balance", description = "Retrieves current balance and status for an account")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID accountId) {
        TenantId tenantId = TenantId.of(TenantContext.getRequiredTenantId());
        AccountId accId = AccountId.of(accountId);

        Account account = accountRepositoryPort.findByTenantIdAndAccountId(tenantId, accId)
                .orElseThrow(() -> new AccountNotFoundException(tenantId, accId));

        tenantAccessGuard.checkTenantAccess(account.getTenantId().value());

        AccountResponse response = new AccountResponse(
                account.getId().value(),
                account.getTenantId().value(),
                account.getCurrency().getCurrencyCode(),
                account.getBalance().amount(),
                account.getStatus().name(),
                account.getCreatedAt()
        );

        return ResponseEntity.ok(response);
    }
}
