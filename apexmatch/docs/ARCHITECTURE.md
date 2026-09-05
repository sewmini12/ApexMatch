# ApexMatch Architecture Documentation

## 1. Project Purpose

ApexMatch is a high-performance, real-time stock order matching engine developed with Java 21 and Spring Boot 3.5.x. The system simulates the core backend processing logic of an electronic financial exchange. 

The exchange itself is not a counterparty; it does not buy or sell securities for its own account. Instead, it acts as an impartial facilitator that receives incoming BUY and SELL orders from market participants, matches compatible counter-orders according to strict **Price-Time Priority (FIFO)**, and emits immutable trade execution records.

---

## 2. High-Level Architecture & Request Flow

ApexMatch separates transient, high-throughput matching operations from persistent storage:

```text
                    POSTMAN / CLIENT
                           |
                           | HTTP POST /api/orders
                           v
                  +----------------+
                  | OrderController|  [Web / REST Layer]
                  +----------------+
                           |
                           | OrderRequest DTO
                           v
                  +----------------+
                  |  OrderService  |  [Application Service Layer]
                  +----------------+
                           |
                           | Domain Order (with generated ID & Sequence)
                           v
                  +----------------+
                  | MatchingEngine |  [Core Domain / Matching Service]
                  +----------------+
                           |
            +--------------+--------------+
            |                             |
            v                             v
     +--------------+              +--------------+
     |  OrderBook   |              |  OrderBook   |   [In-Memory Heaps]
     | (BUY Orders) |              | (SELL Orders)|
     +--------------+              +--------------+
            |                             |
            +--------------+--------------+
                           |
                           | List<Trade> (Executed Matches)
                           v
                  +----------------+
                  |  OrderService  |
                  +----------------+
                           |
                           | TradeEntity
                           v
                  +----------------+
                  |TradeRepository |  [Persistence Layer]
                  +----------------+
                           |
                           | JDBC / Hibernate
                           v
                  +----------------+
                  |   PostgreSQL   |  [Permanent Storage]
                  +----------------+
```

---

## 3. Layer Responsibilities

### 3.1 OrderController (`com.apexmatch.controller`)
- **Primary Responsibility**: Exposes external REST API endpoints (`POST /api/orders`).
- Accepts and deserializes incoming HTTP JSON payloads into `OrderRequest` DTOs.
- Passes the DTO to `OrderService`.
- Returns HTTP responses containing execution results or accepted statuses.
- **Strict Boundary**: Contains zero business rules, zero validation of financial limits, and zero matching logic.

### 3.2 OrderService (`com.apexmatch.service`)
- **Primary Responsibility**: Application orchestration and translation between external DTOs and internal domain models.
- Generates system identifiers: unique `orderId` (e.g., `ORD-1`) and monotonically increasing `sequenceNumber` for time priority.
- Constructs the domain `Order` model.
- Validates the order via domain validator (`OrderValidator`).
- Delegates order execution to `MatchingEngine`.
- Maps domain `Trade` objects to `TradeEntity` records and coordinates persistence via `TradeRepository`.

### 3.3 MatchingEngine (`com.apexmatch.engine`)
- **Primary Responsibility**: Core matching algorithm execution.
- Evaluates incoming orders against the resting orders in `OrderBook`.
- Enforces Price-Time Priority rules:
  - Validates that counter-orders match the same stock symbol.
  - Verifies price feasibility (LIMIT BUY price $\ge$ resting SELL price, LIMIT SELL price $\le$ resting BUY price).
  - Matches MARKET orders against best available counter-order prices.
- Generates `Trade` domain events with unique trade identifiers, execution prices, and traded quantities.
- Calculates remaining quantities, updates order states, and handles partial fills.
- Inserts any unfulfilled LIMIT order balance into the `OrderBook`.

### 3.4 OrderBook (`com.apexmatch.engine`)
- **Primary Responsibility**: High-performance in-memory order queue management.
- Maintains two distinct binary heaps using Java's `PriorityQueue`:
  - **BUY Book**: Max-heap ordered by Price descending, then by Sequence Number ascending (earliest first).
  - **SELL Book**: Min-heap ordered by Price ascending, then by Sequence Number ascending (earliest first).
- Provides $O(1)$ peek operations for best bids and asks, and $O(\log N)$ insertions and poll operations.

### 3.5 PostgreSQL Database (`com.apexmatch.entity` & `com.apexmatch.repository`)
- **Primary Responsibility**: Permanent audit log, historical reporting, and post-trade compliance.
- Records executed trades into the `trades` table.
- Stores execution metadata: trade identifier, stock symbol, buyer ID, seller ID, execution price, quantity, and execution timestamp.

---

## 4. In-Memory OrderBook Architecture

### Why OrderBook is In-Memory Rather than Database-Driven
In financial exchange architectures:
1. **Latency Constraints**: Relational database round-trips, transaction logging (WAL), table locks, and disk I/O typically take milliseconds. An in-memory matching engine completes price-time comparisons and pointer manipulations in microseconds or sub-microseconds.
2. **Dynamic Queue Reordering**: PriorityQueues allow continuous, efficient $O(\log N)$ reorganizations of bids and asks. Performing matching queries against SQL tables (`SELECT ... ORDER BY price, sequence LIMIT 1 FOR UPDATE`) causes severe row lock contention, deadlocks, and serialization bottlenecks under concurrent loads.
3. **Decoupled Persistence**: Matching is decoupled from database transactions. Trades are produced in memory and persisted asynchronously or synchronously to PostgreSQL as an immutable event stream.

---

## 5. Concurrency Architecture & Thread Safety

ApexMatch protects the shared in-memory order book state using Java's `java.util.concurrent.locks.ReentrantLock`.

### 5.1 ReentrantLock Critical Section
When concurrent HTTP requests arrive via the REST API, multiple threads invoke `MatchingEngine.submitOrder(Order)`:
- **Lock Acquisition**: `lock.lock()` is acquired immediately prior to evaluating counter-orders in the `OrderBook`.
- **Protected Critical Section**:
  - Scanning and peeking at opposite counter-orders (`getBestSell` / `getBestBuy`).
  - Cross-symbol validation.
  - Limit and market price feasibility checks.
  - Partial fill quantity computations and trade object generation.
  - Dequeuing filled orders (`removeBestSell` / `removeBestBuy`).
  - Inserting any unfulfilled LIMIT remainder into the order book (`addOrder`).
- **Guaranteed Release**: `try / finally` ensures that `lock.unlock()` is unconditionally executed even if an unexpected exception occurs.

```java
lock.lock();
try {
    // 1. Access and search counter-orders
    // 2. Execute trades and calculate remaining balances
    // 3. Update or remove orders from queues
    // 4. Enqueue unfulfilled remainder
    return trades;
} finally {
    lock.unlock();
}
```

### 5.2 Future Concurrency Enhancements
- **Symbol-Level Partitioning / Sharding**: Dedicated `OrderBook` and `ReentrantLock` instances per ticker symbol (e.g. separate AAPL lock, TSLA lock) to allow parallel matching across independent stocks.
- **Asynchronous Persistence**: Offloading PostgreSQL writes from the matching loop to a decoupled worker queue or ring buffer.
