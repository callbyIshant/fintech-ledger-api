package com.ledger.adapter.in.web;

import com.ledger.adapter.in.web.AccountController.AccountResponse;
import com.ledger.adapter.in.web.AccountController.CreateAccountRequest;
import com.ledger.adapter.in.web.security.TenantAccessGuard;
import com.ledger.application.context.TenantContext;
import com.ledger.application.context.TenantContextData;
import com.ledger.application.port.in.CreateAccountUseCase;
import com.ledger.application.port.in.CreateAccountUseCase.AccountResult;
import com.ledger.application.port.out.AccountRepositoryPort;
import com.ledger.core.domain.Account;
import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.AccountStatus;
import com.ledger.core.domain.Money;
import com.ledger.core.domain.TenantId;
import com.ledger.core.domain.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("AccountController Unit Tests")
class AccountControllerTest {

    private CreateAccountUseCase createAccountUseCase;
    private AccountRepositoryPort accountRepositoryPort;
    private TenantAccessGuard tenantAccessGuard;
    private AccountController controller;

    private TenantId tenantId;
    private Currency usd;

    @BeforeEach
    void setUp() {
        createAccountUseCase = mock(CreateAccountUseCase.class);
        accountRepositoryPort = mock(AccountRepositoryPort.class);
        tenantAccessGuard = new TenantAccessGuard();
        controller = new AccountController(createAccountUseCase, accountRepositoryPort, tenantAccessGuard);

        tenantId = TenantId.of("tenant-alpha");
        usd = Currency.getInstance("USD");
        TenantContext.set(new TenantContextData(tenantId.value(), "user-1", Set.of(UserRole.OPERATOR)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("POST /api/v1/accounts should create account and return 201 Created")
    void shouldCreateAccount() {
        AccountId accountId = AccountId.random();
        AccountResult result = new AccountResult(
                accountId, tenantId, usd, Money.of("100.00", "USD"), AccountStatus.ACTIVE, Instant.now()
        );
        when(createAccountUseCase.createAccount(any())).thenReturn(result);

        CreateAccountRequest request = new CreateAccountRequest("USD", new BigDecimal("100.00"));
        ResponseEntity<AccountResponse> response = controller.createAccount(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(accountId.value(), response.getBody().accountId());
        assertEquals("tenant-alpha", response.getBody().tenantId());
        assertEquals("USD", response.getBody().currency());
        assertEquals(new BigDecimal("100.00"), response.getBody().balance());
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{accountId} should return 200 OK with balance")
    void shouldGetAccount() {
        AccountId accountId = AccountId.random();
        Account account = new Account(accountId, tenantId, usd, Money.of("250.00", "USD"), AccountStatus.ACTIVE, Instant.now(), Instant.now(), 0L);

        when(accountRepositoryPort.findByTenantIdAndAccountId(tenantId, accountId))
                .thenReturn(Optional.of(account));

        ResponseEntity<AccountResponse> response = controller.getAccount(accountId.value());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(accountId.value(), response.getBody().accountId());
        assertEquals(new BigDecimal("250.00"), response.getBody().balance());
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{accountId} should throw AccessDeniedException when cross-tenant access occurs")
    void shouldDenyCrossTenantAccess() {
        AccountId accountId = AccountId.random();
        Account account = new Account(accountId, TenantId.of("tenant-beta"), usd, Money.of("250.00", "USD"), AccountStatus.ACTIVE, Instant.now(), Instant.now(), 0L);

        // Account belongs to tenant-beta in repository
        when(accountRepositoryPort.findByTenantIdAndAccountId(tenantId, accountId))
                .thenReturn(Optional.of(account));

        // When accessed by tenant-alpha, TenantAccessGuard throws AccessDeniedException
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> {
            controller.getAccount(accountId.value());
        });
    }
}
