# Fintech Double-Entry Ledger API

A high-performance, multi-tenant double-entry ledger platform engineered with **Java 21 Virtual Threads**, **Spring Boot 3.4+**, **PostgreSQL 16**, and **Redis 7**. Built on strict **Hexagonal Architecture** (Ports and Adapters) to ensure zero framework leakage into financial core logic.

---

## 1. Architectural Overview

The application follows the **Hexagonal Architecture (Ports and Adapters)** pattern to isolate financial invariants from infrastructure concerns:

```
src/main/java/com/ledger/
├── core/domain/                 # Pure Java 21 domain layer (ZERO Spring/JPA dependencies)
│   ├── Account.java             # Balance tracking, non-negative checks, status enforcement
│   ├── LedgerEntry.java         # Immutable DEBIT/CREDIT entry records
│   ├── Transaction.java         # Double-entry transaction, validates sum(debits) == sum(credits)
│   ├── Money.java               # BigDecimal value object with HALF_EVEN bankers rounding
│   └── exceptions/              # InsufficientFundsException, AccountFrozenException, etc.
│
├── application/
│   ├── context/                 # Scoped multi-tenant context (TenantContext)
│   ├── port/in/                 # Inbound driving ports (Use Cases & Commands)
│   │   ├── CreateAccountUseCase.java
│   │   ├── DepositFundsUseCase.java
│   │   ├── TransferFundsUseCase.java
│   │   └── GetLedgerHistoryUseCase.java
│   ├── port/out/                # Outbound driven ports (Repository & Idempotency interfaces)
│   └── service/                 # Transactional use case orchestrators
│
└── adapter/
    ├── in/web/                  # REST Controllers & Security
    │   ├── AccountController.java
    │   ├── TransferController.java
    │   ├── DepositController.java
    │   ├── LedgerHistoryController.java
    │   ├── security/            # Stateless JWT filter chain & TenantAccessGuard
    │   ├── ratelimit/           # Bucket4j tenant-isolated rate limiting filter
    │   └── exception/           # RFC 7807 ProblemDetail GlobalExceptionHandler
    └── out/
        ├── persistence/         # Spring Data JPA, Hibernate, Flyway migrations
        └── redis/               # Redis idempotency adapter (SHA-256 + 24hr TTL)
```

---

## 2. Double-Entry Accounting Model & Financial Invariants

1. **Zero-Sum Balance Rule**:
   $$\sum \text{Debits} = \sum \text{Credits}$$
   Every transaction requires at least one `DEBIT` and one `CREDIT`. The domain model and PostgreSQL database enforce that all transactions must strictly balance before finalizing.
2. **Immutable Append-Only Audit Trail**:
   Ledger entries are strictly append-only. No row in `ledger_entries` is ever updated or deleted. Corrections are represented as new compensating transactions.
3. **Deterministic Rounding & Precision**:
   All monetary calculations use `BigDecimal` with 4-decimal precision (`NUMERIC(19, 4)`) and `RoundingMode.HALF_EVEN` (Banker's rounding) via the `Money` value object.
4. **Database-Level Invariant Enforcement**:
   - `chk_accounts_balance_non_negative`: `balance >= 0` ensures accounts never overdraw.
   - `chk_ledger_entries_amount_positive`: `amount > 0` ensures entries carry strictly positive magnitudes.
   - `chk_accounts_status` & `chk_transactions_status`: Enforce strict state machines (`ACTIVE`, `FROZEN`, `DRAFT`, `POSTED`).

---

## 3. Concurrency & Idempotency Architecture

### Deadlock-Free Pessimistic Row Locking
Concurrent transfers between accounts (e.g. Thread 1 transferring $A \to B$ while Thread 2 transfers $B \to A$) can induce cyclic deadlocks in relational databases. To eliminate deadlocks:
- Both source and destination accounts are locked in a **strict deterministic order** (`ORDER BY a.id ASC`) using `@Lock(LockModeType.PESSIMISTIC_WRITE)`.
- Because locks are acquired in identical ascending sequence, cyclic lock waits cannot occur.

### Redis Idempotency with SHA-256 Conflict Detection
Every financial mutation endpoint (`/transfers`, `/deposits`) requires an `Idempotency-Key` header:
- **Key Formulation**: `ledger:idempotency:{tenantId}:{idempotencyKey}` with a 24-hour TTL.
- **Payload Hashing**: Computes a SHA-256 digest of the request payload.
- **Idempotent Replay**: If the same key is submitted with an identical payload hash, the cached result is returned immediately without executing transfers or touching accounts.
- **Conflict Rejection**: If the same key is submitted with a altered payload, an `IdempotencyConflictException` is raised, returning an **RFC 7807** `422 Unprocessable Entity` (`IDEMPOTENCY_CONFLICT`).

### Keyset Cursor-Based Pagination
The ledger entries endpoint uses keyset cursor pagination over composite index `(tenant_id, account_id, created_at DESC)`:
$$\text{WHERE } (created\_at < :cursorCreatedAt) \lor (created\_at = :cursorCreatedAt \land id < :cursorId)$$
- Cursors are encoded as URL-safe Base64 strings (`epochMilli:uuid`).
- Queries run in $O(1)$ constant time without the memory and performance degradation of SQL `OFFSET`.

### Tenant-Isolated Rate Limiting
- Built on **Bucket4j** and Redis.
- Enforces an isolated token bucket per `tenantId` (default: 100 capacity, 100 refills/min).
- Excess requests return HTTP `429 Too Many Requests` with a standard `Retry-After` header.

---

## 4. Local Quickstart

### Prerequisites
- Java 21+ (`java -version`)
- Docker & Docker Compose (`docker compose version`)

### 1. Start Infrastructure
```bash
docker-compose up -d
```
Verifies PostgreSQL 16 (port 5432) and Redis 7 (port 6379) with healthchecks.

### 2. Run the Application
```bash
./mvnw spring-boot:run
```
Flyway automatically runs all database migrations on startup. Virtual threads (`spring.threads.virtual.enabled=true`) are active.

### 3. Interactive API Documentation (Swagger UI)
Open your browser to:
[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

---

## 5. End-to-End API Usage Walkthrough

### Step 1: Generate Tenant JWT Token
```bash
curl -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "tenant-alpha",
    "userId": "usr_operator_1",
    "role": "OPERATOR"
  }'
```
Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "type": "Bearer",
  "tenantId": "tenant-alpha",
  "userId": "usr_operator_1",
  "roles": ["OPERATOR"],
  "expiresAt": "2026-11-17T18:30:00Z"
}
```

Set your token:
```bash
export TOKEN="eyJhbGciOiJIUzI1NiJ9..."
```

### Step 2: Create Source & Destination Accounts
```bash
# Create Account 1
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"currency": "INR", "initialBalance": 0.00}'

# Create Account 2
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"currency": "INR", "initialBalance": 0.00}'
```

### Step 3: Deposit Funds
```bash
curl -X POST http://localhost:8080/api/v1/deposits \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: dep-key-001" \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "<ACCOUNT_1_UUID>",
    "amount": 10000.00,
    "currency": "INR",
    "description": "Initial reserve deposit"
  }'
```

### Step 4: Transfer Funds with Idempotency Protection
```bash
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: trf-key-001" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccountId": "<ACCOUNT_1_UUID>",
    "destinationAccountId": "<ACCOUNT_2_UUID>",
    "amount": 300.00,
    "currency": "INR",
    "description": "Supplier payout"
  }'
```

Retrying this exact curl command returns the cached transfer receipt instantly without double-debiting.

### Step 5: Query Ledger Entries with Keyset Pagination
```bash
curl -X GET "http://localhost:8080/api/v1/accounts/<ACCOUNT_1_UUID>/entries?limit=10" \
  -H "Authorization: Bearer $TOKEN"
```
Response:
```json
{
  "entries": [
    {
      "entryId": "a1b2c3d4-...",
      "accountId": "<ACCOUNT_1_UUID>",
      "type": "DEBIT",
      "amount": 300.0000,
      "currency": "INR",
      "timestamp": "2026-11-16T18:30:00Z",
      "description": "Supplier payout"
    }
  ],
  "nextCursor": "MTcyNDAz...==",
  "hasMore": false
}
```

---

## 6. Testing & Verification

Run the comprehensive unit, domain invariant, and concurrency test suites:
```bash
./mvnw test
```

Run full integration and verification suite:
```bash
./mvnw verify
```

### Concurrency Stress Test Specification
- **50 Concurrent Threads** simultaneously transfer 300.00 INR each from a 10,000.00 INR account using `CountDownLatch` and `ExecutorService`.
- **Verified Invariants**:
  - Exactly **33 transfers succeed** ($33 \times 300.00 = 9,900.00$ INR).
  - Exactly **17 transfers fail** with `InsufficientFundsException` (HTTP 422 `INSUFFICIENT_FUNDS`).
  - Remaining source account balance is strictly **`100.0000` INR**.
  - Final destination account balance is strictly **`9900.0000` INR**.
  - Total debits strictly equal total credits ($\sum \text{Debits} = \sum \text{Credits}$) across all ledger entries in the database.
