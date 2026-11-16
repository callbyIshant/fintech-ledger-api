package com.ledger.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.adapter.in.web.AccountController.CreateAccountRequest;
import com.ledger.adapter.in.web.AuthController.TokenRequest;
import com.ledger.adapter.in.web.DepositController.DepositRequest;
import com.ledger.adapter.in.web.TransferController.TransferRequest;
import com.ledger.adapter.out.persistence.SpringDataAccountRepository;
import com.ledger.adapter.out.persistence.SpringDataLedgerEntryRepository;
import com.ledger.adapter.out.persistence.SpringDataTenantRepository;
import com.ledger.adapter.out.persistence.TenantJpaEntity;
import com.ledger.core.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("End-to-End Ledger Integration Tests with Testcontainers")
class EndToEndLedgerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SpringDataTenantRepository tenantRepository;

    @Autowired
    private SpringDataAccountRepository accountRepository;

    @Autowired
    private SpringDataLedgerEntryRepository ledgerEntryRepository;

    private static final String TENANT_ID = "tenant-e2e";
    private String jwtToken;

    @BeforeEach
    void setUp() throws Exception {
        // Ensure tenant exists in PostgreSQL
        if (!tenantRepository.existsById(TENANT_ID)) {
            tenantRepository.save(new TenantJpaEntity(TENANT_ID, "E2E Enterprise Tenant", "ACTIVE", Instant.now(), Instant.now()));
        }

        // Obtain authenticated JWT token for the tenant
        TokenRequest tokenRequest = new TokenRequest(TENANT_ID, "admin-user", Set.of(UserRole.ADMIN));
        MvcResult tokenResult = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tokenRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode tokenNode = objectMapper.readTree(tokenResult.getResponse().getContentAsString());
        jwtToken = tokenNode.get("accessToken").asText();
    }

    @Test
    @DisplayName("Full HTTP Lifecycle: Issue JWT -> Create Wallets -> Deposit -> Transfer -> Idempotent Replay -> Cursor Pagination")
    void testFullHttpLifecycle() throws Exception {
        // 1. Create Wallet A (Source)
        CreateAccountRequest createAccountA = new CreateAccountRequest("INR", BigDecimal.ZERO);
        MvcResult resA = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createAccountA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("INR"))
                .andReturn();
        UUID accountIdA = UUID.fromString(objectMapper.readTree(resA.getResponse().getContentAsString()).get("accountId").asText());

        // 2. Create Wallet B (Destination)
        CreateAccountRequest createAccountB = new CreateAccountRequest("INR", BigDecimal.ZERO);
        MvcResult resB = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createAccountB)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("INR"))
                .andReturn();
        UUID accountIdB = UUID.fromString(objectMapper.readTree(resB.getResponse().getContentAsString()).get("accountId").asText());

        // 3. Deposit 1,000.00 INR into Wallet A
        DepositRequest depositRequest = new DepositRequest(accountIdA, new BigDecimal("1000.00"), "INR", "Initial deposit");
        mockMvc.perform(post("/api/v1/deposits")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("Idempotency-Key", "e2e-deposit-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newBalance").value(1000.00));

        // 4. Transfer 400.00 INR from Wallet A to Wallet B
        TransferRequest transferRequest = new TransferRequest(accountIdA, accountIdB, new BigDecimal("400.00"), "INR", "Payment 1");
        MvcResult transferRes1 = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("Idempotency-Key", "e2e-transfer-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isOk())
                .andReturn();
        UUID originalTxId = UUID.fromString(objectMapper.readTree(transferRes1.getResponse().getContentAsString()).get("transactionId").asText());

        // 5. Duplicate Transfer Retry with same Idempotency-Key (Assert no double-debit)
        MvcResult transferRes2 = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("Idempotency-Key", "e2e-transfer-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isOk())
                .andReturn();
        UUID replayedTxId = UUID.fromString(objectMapper.readTree(transferRes2.getResponse().getContentAsString()).get("transactionId").asText());
        assertEquals(originalTxId, replayedTxId, "Idempotent replay must return original transactionId");

        // Verify balances after transfer & retry
        mockMvc.perform(get("/api/v1/accounts/" + accountIdA)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(600.00));

        mockMvc.perform(get("/api/v1/accounts/" + accountIdB)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(400.00));

        // 6. Keyset Cursor Pagination on Wallet A entries
        MvcResult page1Result = mockMvc.perform(get("/api/v1/accounts/" + accountIdA + "/entries?limit=1")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn();

        JsonNode page1Node = objectMapper.readTree(page1Result.getResponse().getContentAsString());
        assertEquals(1, page1Node.get("entries").size());
        String nextCursor = page1Node.get("nextCursor").asText();
        assertNotNull(nextCursor, "nextCursor must be present when hasMore is true");

        // Fetch second page using nextCursor
        mockMvc.perform(get("/api/v1/accounts/" + accountIdA + "/entries?limit=1&cursor=" + nextCursor)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1));
    }

    @Test
    @DisplayName("High Concurrency Stress Test: 50 Threads transferring 300 INR against 10,000 INR balance")
    void testHighConcurrencyTransfers() throws Exception {
        // 1. Seed Source Wallet with 10,000.00 INR and Destination Wallet with 0.00 INR
        CreateAccountRequest createSrc = new CreateAccountRequest("INR", new BigDecimal("10000.00"));
        MvcResult srcRes = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createSrc)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID sourceId = UUID.fromString(objectMapper.readTree(srcRes.getResponse().getContentAsString()).get("accountId").asText());

        CreateAccountRequest createDst = new CreateAccountRequest("INR", BigDecimal.ZERO);
        MvcResult dstRes = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDst)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID destId = UUID.fromString(objectMapper.readTree(dstRes.getResponse().getContentAsString()).get("accountId").asText());

        // 2. Fire 50 concurrent threads transferring 300.00 INR each
        int totalThreads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger insufficientFundsCount = new AtomicInteger(0);
        AtomicInteger otherErrors = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            final String idempotencyKey = "concurrent-stress-" + UUID.randomUUID();
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize all threads to fire simultaneously
                    TransferRequest req = new TransferRequest(sourceId, destId, new BigDecimal("300.00"), "INR", "Concurrent transfer");

                    MvcResult result = mockMvc.perform(post("/api/v1/transfers")
                                    .header("Authorization", "Bearer " + jwtToken)
                                    .header("Idempotency-Key", idempotencyKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    if (status == 200) {
                        successCount.incrementAndGet();
                    } else if (status == 422) {
                        String body = result.getResponse().getContentAsString();
                        if (body.contains("INSUFFICIENT_FUNDS")) {
                            insufficientFundsCount.incrementAndGet();
                        } else {
                            otherErrors.incrementAndGet();
                        }
                    } else {
                        otherErrors.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherErrors.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Fire all threads
        startLatch.countDown();
        finishLatch.await();
        executor.shutdown();

        // 3. Assertions
        assertEquals(0, otherErrors.get(), "No unexpected HTTP errors allowed");
        assertEquals(33, successCount.get(), "Exactly 33 transfers of 300.00 INR must succeed from 10,000.00 INR");
        assertEquals(17, insufficientFundsCount.get(), "Exactly 17 transfers must fail with INSUFFICIENT_FUNDS (HTTP 422)");

        // 4. Assert final balances
        MvcResult finalSrcRes = mockMvc.perform(get("/api/v1/accounts/" + sourceId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andReturn();
        BigDecimal finalSrcBal = new BigDecimal(objectMapper.readTree(finalSrcRes.getResponse().getContentAsString()).get("balance").asText());
        assertEquals(0, new BigDecimal("100.0000").compareTo(finalSrcBal), "Source account remaining balance must be exactly 100.0000");

        MvcResult finalDstRes = mockMvc.perform(get("/api/v1/accounts/" + destId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andReturn();
        BigDecimal finalDstBal = new BigDecimal(objectMapper.readTree(finalDstRes.getResponse().getContentAsString()).get("balance").asText());
        assertEquals(0, new BigDecimal("9900.0000").compareTo(finalDstBal), "Destination account balance must be exactly 9900.0000");

        // 5. Invariant: Total Debits must strictly equal Total Credits in database
        BigDecimal totalDebits = ledgerEntryRepository.findAll().stream()
                .filter(e -> "DEBIT".equals(e.getType()))
                .map(e -> e.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = ledgerEntryRepository.findAll().stream()
                .filter(e -> "CREDIT".equals(e.getType()))
                .map(e -> e.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, totalDebits.compareTo(totalCredits), "Total ledger debits must strictly equal total credits (Double-Entry Invariant)");
    }
}
