package com.ledger.adapter.in.web;

import com.ledger.adapter.in.web.DepositController.DepositRequest;
import com.ledger.adapter.in.web.DepositController.DepositResponse;
import com.ledger.application.context.TenantContext;
import com.ledger.application.context.TenantContextData;
import com.ledger.application.port.in.DepositFundsUseCase;
import com.ledger.application.port.in.DepositFundsUseCase.DepositResult;
import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.Money;
import com.ledger.core.domain.TenantId;
import com.ledger.core.domain.TransactionId;
import com.ledger.core.domain.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("DepositController Unit Tests")
class DepositControllerTest {

    private DepositFundsUseCase depositFundsUseCase;
    private DepositController controller;

    private TenantId tenantId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        depositFundsUseCase = mock(DepositFundsUseCase.class);
        controller = new DepositController(depositFundsUseCase);

        tenantId = TenantId.of("tenant-beta");
        accountId = UUID.randomUUID();

        TenantContext.set(new TenantContextData("tenant-beta", "user-2", Set.of(UserRole.OPERATOR)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("POST /api/v1/deposits should deposit funds and return 200 OK with DepositResponse")
    void shouldDepositFunds() {
        TransactionId txId = TransactionId.random();
        DepositResult result = new DepositResult(
                txId,
                tenantId,
                AccountId.of(accountId),
                Money.of("500.00", "USD"),
                Money.of("1500.00", "USD"),
                "idem-deposit-1",
                Instant.now()
        );

        when(depositFundsUseCase.depositFunds(any())).thenReturn(result);

        DepositRequest request = new DepositRequest(
                accountId,
                new BigDecimal("500.00"),
                "USD",
                "Inbound wire deposit"
        );

        ResponseEntity<DepositResponse> response = controller.depositFunds("idem-deposit-1", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(txId.value(), response.getBody().transactionId());
        assertEquals("tenant-beta", response.getBody().tenantId());
        assertEquals(accountId, response.getBody().accountId());
        assertEquals(new BigDecimal("500.00"), response.getBody().depositedAmount());
        assertEquals(new BigDecimal("1500.00"), response.getBody().newBalance());
        assertEquals("USD", response.getBody().currency());
        assertEquals("idem-deposit-1", response.getBody().idempotencyKey());
    }
}
