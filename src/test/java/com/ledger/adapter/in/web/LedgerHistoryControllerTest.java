package com.ledger.adapter.in.web;

import com.ledger.adapter.in.web.LedgerHistoryController.CursorPageResponse;
import com.ledger.adapter.in.web.LedgerHistoryController.LedgerEntryResponse;
import com.ledger.application.context.TenantContext;
import com.ledger.application.context.TenantContextData;
import com.ledger.application.port.in.GetLedgerHistoryUseCase;
import com.ledger.application.port.in.GetLedgerHistoryUseCase.CursorPageResult;
import com.ledger.application.port.in.GetLedgerHistoryUseCase.GetLedgerHistoryCursorQuery;
import com.ledger.application.port.in.GetLedgerHistoryUseCase.LedgerEntryResult;
import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.EntryType;
import com.ledger.core.domain.Money;
import com.ledger.core.domain.TenantId;
import com.ledger.core.domain.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("LedgerHistoryController Unit Tests")
class LedgerHistoryControllerTest {

    private GetLedgerHistoryUseCase getLedgerHistoryUseCase;
    private LedgerHistoryController controller;

    private TenantId tenantId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        getLedgerHistoryUseCase = mock(GetLedgerHistoryUseCase.class);
        controller = new LedgerHistoryController(getLedgerHistoryUseCase);

        tenantId = TenantId.of("tenant-hist");
        accountId = UUID.randomUUID();

        TenantContext.set(new TenantContextData("tenant-hist", "user-hist", Set.of(UserRole.AUDITOR)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{accountId}/entries should return page of entries with nextCursor")
    void shouldReturnCursorPageOfEntries() {
        UUID entryId1 = UUID.randomUUID();
        UUID entryId2 = UUID.randomUUID();
        Instant now = Instant.now();

        LedgerEntryResult entry1 = new LedgerEntryResult(
                entryId1, AccountId.of(accountId), EntryType.CREDIT,
                Money.of("100.00", "USD"), now, "Deposit"
        );
        LedgerEntryResult entry2 = new LedgerEntryResult(
                entryId2, AccountId.of(accountId), EntryType.DEBIT,
                Money.of("40.00", "USD"), now.minusSeconds(10), "Transfer out"
        );

        String nextCursor = "cursor-token-abc";
        CursorPageResult<LedgerEntryResult> pageResult = new CursorPageResult<>(
                List.of(entry1, entry2), nextCursor, true
        );

        when(getLedgerHistoryUseCase.getLedgerHistoryWithCursor(any())).thenReturn(pageResult);

        ResponseEntity<CursorPageResponse<LedgerEntryResponse>> response =
                controller.getEntries(accountId, null, 2);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().entries().size());
        assertEquals(nextCursor, response.getBody().nextCursor());
        assertTrue(response.getBody().hasMore());

        LedgerEntryResponse first = response.getBody().entries().get(0);
        assertEquals(entryId1, first.entryId());
        assertEquals("CREDIT", first.type());
        assertEquals(new BigDecimal("100.00"), first.amount());
        assertEquals("USD", first.currency());
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{accountId}/entries should clamp limit between 1 and 100")
    void shouldClampLimit() {
        CursorPageResult<LedgerEntryResult> emptyResult = new CursorPageResult<>(List.of(), null, false);
        when(getLedgerHistoryUseCase.getLedgerHistoryWithCursor(any())).thenReturn(emptyResult);

        ArgumentCaptor<GetLedgerHistoryCursorQuery> captor = ArgumentCaptor.forClass(GetLedgerHistoryCursorQuery.class);

        // Limit > 100 should clamp to 100
        controller.getEntries(accountId, "prev-cursor", 500);
        verify(getLedgerHistoryUseCase).getLedgerHistoryWithCursor(captor.capture());
        assertEquals(100, captor.getValue().limit());
        assertEquals("prev-cursor", captor.getValue().cursor());

        // Limit < 1 should clamp to 1
        controller.getEntries(accountId, null, -5);
        verify(getLedgerHistoryUseCase, times(2)).getLedgerHistoryWithCursor(captor.capture());
        assertEquals(1, captor.getValue().limit());
    }
}
