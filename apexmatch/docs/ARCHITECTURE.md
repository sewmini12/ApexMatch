# ApexMatch Architecture Documentation

## 1. Project Purpose

ApexMatch is a high-performance, real-time stock order matching engine developed with Java 21 and Spring Boot 3.5.x. The system simulates the core backend processing logic of an electronic financial exchange. 

The exchange itself is not a counterparty; it does not buy or sell securities for its own account. Instead, it acts as an impartial facilitator that receives incoming BUY and SELL orders from market participants, matches compatible counter-orders according to strict **Price-Time Priority (FIFO)**, and emits immutable trade execution records.

---

## 2. High-Level Architecture & Request Flow

ApexMatch separates transient, high-throughput matching operations from persistent storage:

```text
                    POSTMAN / CLIENT
                           │
                           │ HTTP POST /api/orders
                           ▼
                  +----------------+
                  | OrderController|  [Web / REST Layer]
                  +----------------+
                           │
                           │ OrderRequest DTO
                           ▼
                  +----------------+
                  |  OrderService  |  [Application Service Layer]
                  +----------------+
                           │
                           │ Domain Order (with generated ID, normalized symbol & sequence)
                           ▼
                  +----------------+
                  | MatchingEngine |  [Core Matching Service & Per-Symbol Lock Acquisition]
                  +----------------+
                           │
                           ▼
               +-----------------------+
               |   OrderBookManager    |  [ConcurrentHashMap<String, OrderBook>]
               +-----------------------+
                │          │          │
                ▼          ▼          ▼
         +------------+ +------------+ +------------+
         | AAPL Book  | | GOOG Book  | | TSLA Book  |  [Independent Dual Heaps]
         +------------+ +------------+ +------------+
                           │
                           │ List<Trade> (Executed Matches)
                           ▼
                  +----------------+
                  |  OrderService  |
                  +----------------+
                           │
                           │ TradeEntity
                           ▼
                  +----------------+
                  |TradeRepository |  [Persistence Layer]
                  +----------------+
                           │
                           │ JDBC / Hibernate
                           ▼
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
- **Primary Responsibility**: Core matching algorithm orchestration and per-symbol locking.
- Acquires the dedicated `ReentrantLock` for the incoming order's symbol from `OrderBookManager`.
- Retrieves the symbol's dedicated `OrderBook`.
- Evaluates incoming orders strictly against the resting orders of that specific symbol:
  - Verifies price feasibility (LIMIT BUY price $\ge$ resting SELL price, LIMIT SELL price $\le$ resting BUY price).
  - Matches MARKET orders against best available counter-order prices.
- Generates `Trade` domain events with unique trade identifiers, execution prices, and traded quantities.
- Calculates remaining quantities, updates order states, and handles partial fills.
- Inserts any unfulfilled LIMIT order balance into that symbol's `OrderBook`.
- Unconditionally releases the symbol's `ReentrantLock` in a `finally` block.

### 3.4 OrderBookManager (`com.apexmatch.engine`)
- **Primary Responsibility**: Lifecycle and thread-safe registry of per-symbol order books and concurrency locks.
- Backed by `ConcurrentHashMap<String, OrderBook>` and `ConcurrentHashMap<String, ReentrantLock>`.
- Normalizes ticker symbols (trimmed uppercase, e.g. `aapl` -> `AAPL`).
- Dynamically creates and returns dedicated `OrderBook` instances on demand.
- Provides per-symbol `ReentrantLock` instances, eliminating lock contention between independent symbols.

### 3.5 OrderBook (`com.apexmatch.engine`)
- **Primary Responsibility**: High-performance in-memory order queue management for a single trading symbol.
- Maintains two distinct binary heaps using Java's `PriorityQueue`:
  - **BUY Book**: Max-heap ordered by Price descending, then by Sequence Number ascending (earliest first).
  - **SELL Book**: Min-heap ordered by Price ascending, then by Sequence Number ascending (earliest first).
- Provides $O(1)$ peek operations for best bids and asks, and $O(\log N)$ insertions and poll operations.

### 3.6 PostgreSQL Database (`com.apexmatch.entity` & `com.apexmatch.repository`)
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

### Why Separate Order Books Per Symbol are Required
1. **Physical Isolation**: In an exchange, securities are completely independent financial instruments. An order for Apple (`AAPL`) must never cross with an order for Tesla (`TSLA`). Separate order books ensure zero risk of cross-instrument execution.
2. **Heap Integrity**: Dual-heap algorithms sort strictly by price. Mixing multiple tickers in one heap breaks price discovery because higher-priced instruments would starve lower-priced instruments from reaching the top of the queue.
3. **Independent Latency & Scaling**: Distinct books allow the engine to partition workloads per ticker, isolating trading spikes in one symbol from affecting matching speed in others.

---

## 5. Concurrency Architecture & Thread Safety

ApexMatch protects in-memory order book state using per-symbol Java `java.util.concurrent.locks.ReentrantLock` instances managed by `OrderBookManager`.

### 5.1 Per-Symbol Locking Model
When concurrent HTTP requests arrive via the REST API, multiple threads invoke `MatchingEngine.submitOrder(Order)`:
- **Symbol-Level Lock Acquisition**: The engine retrieves a dedicated `ReentrantLock` for `incomingOrder.getSymbol()`.
- **Zero Cross-Symbol Contention**: Threads executing orders for `AAPL`, `GOOG`, and `TSLA` acquire different locks and run in parallel on separate CPU cores with zero lock contention.
- **Strict Intra-Symbol Serialization**: Threads executing orders for the *same* symbol are serialized, guaranteeing thread-safe mutations on the underlying `PriorityQueue` structures.
- **Guaranteed Release**: A `try / finally` block ensures that `lock.unlock()` is unconditionally executed even if an unexpected exception occurs.

```java
String symbol = OrderBookManager.normalizeSymbol(incomingOrder.getSymbol());
ReentrantLock lock = orderBookManager.getLockForSymbol(symbol);

lock.lock();
try {
    OrderBook orderBook = orderBookManager.getOrCreateOrderBook(symbol);
    // 1. Evaluate opposite counter-orders within this symbol's book
    // 2. Execute trades and calculate remaining balances
    // 3. Dequeue filled orders
    // 4. Enqueue unfulfilled remainder into this symbol's book
    return trades;
} finally {
    lock.unlock();
}
```

### 5.2 Deadlock Prevention
Deadlock cannot occur between symbols because each order is strictly associated with exactly one symbol and only ever acquires that symbol's single lock. There are no nested or multi-symbol lock acquisitions.
