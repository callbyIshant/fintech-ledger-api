package com.ledger.adapter.in.web;

import com.ledger.application.context.TenantContext;
import com.ledger.application.port.in.GetLedgerHistoryUseCase;
import com.ledger.application.port.in.GetLedgerHistoryUseCase.CursorPageResult;
import com.ledger.application.port.in.GetLedgerHistoryUseCase.GetLedgerHistoryCursorQuery;
import com.ledger.application.port.in.GetLedgerHistoryUseCase.LedgerEntryResult;
import com.ledger.core.domain.AccountId;
import com.ledger.core.domain.TenantId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/entries")
@Tag(name = "Ledger History", description = "Cursor-based pagination of account ledger entries")
public class LedgerHistoryController {

    private final GetLedgerHistoryUseCase getLedgerHistoryUseCase;

    public LedgerHistoryController(GetLedgerHistoryUseCase getLedgerHistoryUseCase) {
        this.getLedgerHistoryUseCase = getLedgerHistoryUseCase;
    }

    public record LedgerEntryResponse(
            UUID entryId,
            UUID accountId,
            String type,
            BigDecimal amount,
            String currency,
            Instant timestamp,
            String description
    ) {}

    public record CursorPageResponse<T>(
            List<T> entries,
            String nextCursor,
            boolean hasMore
    ) {}

    @GetMapping
    @Operation(summary = "Get ledger entries with cursor pagination",
               description = "Retrieves chronological ledger entries using keyset (created_at, entry_id) cursor pagination")
    public ResponseEntity<CursorPageResponse<LedgerEntryResponse>> getEntries(
            @PathVariable UUID accountId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        int effectiveLimit = Math.min(Math.max(1, limit), 100);
        TenantId tenantId = TenantId.of(TenantContext.getRequiredTenantId());

        GetLedgerHistoryCursorQuery query = new GetLedgerHistoryCursorQuery(
                tenantId,
                AccountId.of(accountId),
                cursor,
                effectiveLimit
        );

        CursorPageResult<LedgerEntryResult> pageResult = getLedgerHistoryUseCase.getLedgerHistoryWithCursor(query);

        List<LedgerEntryResponse> entries = pageResult.items().stream()
                .map(item -> new LedgerEntryResponse(
                        item.id(),
                        item.accountId().value(),
                        item.type().name(),
                        item.amount().amount(),
                        item.amount().currency().getCurrencyCode(),
                        item.entryTimestamp(),
                        item.description()
                ))
                .toList();

        return ResponseEntity.ok(new CursorPageResponse<>(entries, pageResult.nextCursor(), pageResult.hasMore()));
    }
}
