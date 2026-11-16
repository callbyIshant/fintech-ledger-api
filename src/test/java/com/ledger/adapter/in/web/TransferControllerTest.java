package com.ledger.adapter.in.web;

import com.ledger.adapter.in.web.TransferController.TransferRequest;
import com.ledger.adapter.in.web.TransferController.TransferResponse;
import com.ledger.application.context.TenantContext;
import com.ledger.application.context.TenantContextData;
import com.ledger.application.port.in.TransferFundsUseCase;
import com.ledger.application.port.in.TransferFundsUseCase.TransferResult;
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

@DisplayName("TransferController Unit Tests")
class TransferControllerTest {

    private TransferFundsUseCase transferFundsUseCase;
    private TransferController controller;

    private TenantId tenantId;
    private UUID sourceId;
    private UUID destId;

    @BeforeEach
    void setUp() {
        transferFundsUseCase = mock(TransferFundsUseCase.class);
        controller = new TransferController(transferFundsUseCase);

        tenantId = TenantId.of("tenant-x");
        sourceId = UUID.randomUUID();
        destId = UUID.randomUUID();

        TenantContext.set(new TenantContextData("tenant-x", "user-op", Set.of(UserRole.OPERATOR)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("POST /api/v1/transfers with valid Idempotency-Key should execute transfer and return 200 OK")
    void shouldExecuteTransfer() {
        TransactionId txId = TransactionId.random();
        TransferResult result = new TransferResult(
                txId, tenantId, AccountId.of(sourceId), AccountId.of(destId),
                Money.of("75.00", "USD"), "idem-key-1", Instant.now()
        );

        when(transferFundsUseCase.transferFunds(any())).thenReturn(result);

        TransferRequest request = new TransferRequest(
                sourceId, destId, new BigDecimal("75.00"), "USD", "Payment for services"
        );

        ResponseEntity<TransferResponse> response = controller.transferFunds("idem-key-1", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(txId.value(), response.getBody().transactionId());
        assertEquals("tenant-x", response.getBody().tenantId());
        assertEquals(sourceId, response.getBody().sourceAccountId());
        assertEquals(destId, response.getBody().destinationAccountId());
        assertEquals(new BigDecimal("75.00"), response.getBody().amount());
        assertEquals("idem-key-1", response.getBody().idempotencyKey());
    }
}
