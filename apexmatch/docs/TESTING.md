# ApexMatch Comprehensive Testing Strategy

ApexMatch employs a rigorous, multi-tiered testing strategy combining high-coverage unit tests, heavy-load concurrency stress tests, and real-database integration tests running on ephemeral PostgreSQL instances via **Testcontainers**.

---

## 1. Test Suite Summary & Metrics

- **Total Automated Tests**: `71`
  - **Unit & Concurrency Tests**: `58`
  - **Heavy Concurrent Stress Tests**: `7` (`com.apexmatch.stress`)
  - **PostgreSQL Integration Tests (Testcontainers)**: `6` (`com.apexmatch.integration`)
- **Failures**: `0`
- **Errors**: `0`
- **Skipped**: `0`
- **Code Coverage Target**: > 90% branch and line coverage across domain, matching engine, and persistence layers.

---

## 2. Testing Architecture & Tiers

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                   ApexMatch Test Suite                                  │
├──────────────────────────┬───────────────────────────┬──────────────────────────────────┤
│        Tier 1            │          Tier 2           │              Tier 3              │
│ Fast Unit & Mock Tests   │ Concurrency Stress Tests  │   PostgreSQL Integration Tests   │
│       (58 Tests)         │         (7 Tests)         │             (6 Tests)            │
├──────────────────────────┼───────────────────────────┼──────────────────────────────────┤
│ • Pure POJO execution    │ • High-volume orders      │ • Real PostgreSQL in Docker      │
│ • Deterministic logic    │ • 4, 8, 16 worker threads │ • Testcontainers auto-lifecycle  │
│ • Sub-second execution   │ • Invariant conservation  │ • @ServiceConnection injection   │
│ • MockMvc controllers    │ • Deadlock & race guards  │ • Full HTTP-to-DB roundtrip      │
└──────────────────────────┴───────────────────────────┴──────────────────────────────────┘
```

---

## 3. Unit & Concurrency Test Breakdown (Tier 1)

### 3.1 Core Matching Engine (`MatchingEngineTest.java`)
Validates fundamental market microstructure rules:
- **Price-Time Priority**: Verifies that orders with superior prices match first, and tied prices are resolved strictly by order entry timestamp/sequence.
- **Full & Partial Fills**: Ensures remaining order quantities are updated in-place or re-queued accurately.
- **Market Orders**: Asserts that market orders execute immediately against the best resting counter-orders without resting.
- **Price Improvement**: Confirms aggressive incoming orders execute at the passive resting order's limit price.
- **No-Match Preservation**: Confirms unmatched limit orders remain safely in the order book.

### 3.2 Multi-Symbol Order Book (`OrderBookManagerTest.java`, `MultiSymbolOrderBookTest.java`)
- **Symbol Isolation**: Proves that orders for `AAPL`, `GOOG`, and `TSLA` operate in completely separate order books and never match against one another.
- **Dynamic Book Allocation**: Verifies that `OrderBookManager` provisions new symbol books dynamically using thread-safe `computeIfAbsent`.
- **Per-Symbol State Cleansing**: Tests bulk and targeted symbol cache clearance.

### 3.3 Concurrency & Thread-Safety (`MatchingEngineConcurrencyTest.java`)
- **Multi-Threaded Race Conditions**: Uses `ExecutorService` and `CountDownLatch` to blast concurrent buy and sell orders from 10+ concurrent threads.
- **Zero Lost Orders**: Verifies total executed trade volume plus remaining book quantities equals exactly the total quantity submitted across all threads.
- **Lock Integrity**: Validates that per-symbol `ReentrantLock` guards against heap array corruption and prevents phantom overfills.

### 3.4 Validation & Exception Handling (`OrderValidatorTest.java`, `OrderControllerTest.java`)
- **Jakarta Bean Validation**: Tests constraints for `@NotBlank`, `@NotNull`, and `@Min(1)`.
- **Domain Business Rules**: Confirms `LIMIT` orders without prices or `MARKET` orders with prices produce structured `HTTP 400 Bad Request` responses with field-level details.
- **MockMvc Controller Tests**: Tests endpoint serialization, HTTP status codes, and error payload structures.

---

## 4. Heavy Concurrent Load & Stress Testing (Tier 2)

Dedicated suite in `com.apexmatch.stress.MatchingEngineStressTest` validating data integrity and mathematical invariants under high contention across **4, 8, and 16 worker threads**:

### 4.1 Stress Scenarios Implemented
1. **High-Volume Single-Symbol Workload**:
   - 8 concurrent threads submitting 2,000 orders against `AAPL`.
   - Asserts **Conservation of Volume**: $\sum Q_{\text{submitted}} = \sum Q_{\text{traded}} + \sum Q_{\text{resting}}$.
   - Verifies all trade prices and quantities are strictly positive, trade IDs are globally unique, and the symbol lock is released.
2. **High-Volume Multi-Symbol Partitioned Workload**:
   - 16 concurrent threads dispatching 2,400 orders across `AAPL`, `GOOG`, `TSLA`, `MSFT`, and `AMZN`.
   - Asserts strict symbol isolation: zero cross-symbol executions, symbol prices stay within expected valuation bands, and independent volume conservation holds for each symbol book.
3. **Concurrent Symmetric Matching**:
   - 8 threads submitting 800 matching BUY/SELL order pairs simultaneously.
   - Asserts complete market clearing: 100% of demand executes, leaving the order book completely empty (`isEmpty() == true`).
4. **Concurrent Partial Matching with Irregular Slices**:
   - Asymmetric, irregular order sizes (prime slices: 7, 13, 19, 23, 29, 31, 37, 41, 43, 47 shares).
   - Validates that non-negativity is preserved across partial fills and that opposite books never rest at identical prices.
5. **Thread Scaling Verification (4, 8, 16 Threads)**:
   - Parameterized execution asserting that volume conservation and lock cleanliness hold unconditionally regardless of thread pool depth.

---

## 5. Integration Testing with Testcontainers (Tier 3)

Integration tests execute against a real, ephemeral PostgreSQL instance using **Testcontainers**, eliminating the fragility of in-memory H2 mock databases (which lack PostgreSQL-specific dialect features and concurrency semantics).

### 5.1 Integration Test Classes
1. **`OrderControllerIntegrationTest.java`**:
   - Executes an end-to-end HTTP request through Spring `MockMvc`.
   - Traverses `OrderController` $\rightarrow$ `OrderService` $\rightarrow$ `MatchingEngine` $\rightarrow$ `TradeRepository` $\rightarrow$ Real PostgreSQL database.
   - Asserts both the HTTP response payload and the actual persisted SQL row in the `trades` table.
2. **`TradePersistenceIntegrationTest.java`**:
   - **Test 1 — Trade Persistence**: Verifies exact column and field mappings (`trade_id`, `buyer`, `seller`, `price`, `quantity`, `executed_at`).
   - **Test 2 — Partial Fill Persistence**: Confirms partial fill trades persist correctly while remaining balances remain in memory.
   - **Test 3 — Multi-Trade Match Persistence**: Ensures a single aggressive order sweeping multiple resting orders persists all generated trades atomically.
   - **Test 4 — Multi-Symbol Isolation**: Verifies cross-symbol executions write distinct symbol records without cross-talk.
   - **Test 5 — Transaction Atomicity**: Verifies transactional integrity across multiple trade executions.

### 5.2 Dynamic PostgreSQL Connection with `@ServiceConnection`
```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
public class OrderControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    // Dynamic JDBC URL, username, and password automatically injected by Spring Boot 3.1+
}
```

---

## 6. How to Run the Tests

### 6.1 Run the Full Test Suite (All 71 Tests)
Requires Docker to be running on the host machine:
```bash
mvn clean test
```

### 6.2 Run Fast Unit & Concurrency Tests (Skip Docker)
```bash
mvn test -Dtest="!*IntegrationTest*"
```

### 6.3 Run Concurrency Stress Tests Specifically
```bash
mvn test -Dtest="MatchingEngineStressTest"
```

### 6.4 Run Only Testcontainers Integration Tests
```bash
mvn test -Dtest="*IntegrationTest*"
```
