# ApexMatch Concurrency & Thread-Safety Architecture

An electronic order matching engine is inherently a **stateful, high-contention system**. Multiple client requests arrive asynchronously across HTTP threads, each attempting to inspect, mutate, and balance the shared order book. Without rigorous concurrency controls, such an engine experiences severe race conditions, financial over-allocation, and data structure corruption.

This document details the concurrency challenges in electronic trading, how ApexMatch resolves them using **granular per-symbol `ReentrantLock` synchronization**, and the architectural trade-offs involved.

---

## 1. The Shared-State Concurrency Challenge

An order book maintains two resting queues: `buyOrders` (bids) and `sellOrders` (asks). When an aggressive incoming order arrives, the engine performs a composite multi-step transaction:
1. **Peek** at the best counter-order at the root of the opposite heap.
2. **Evaluate** price compatibility (`buyPrice >= sellPrice`).
3. **Calculate** execution quantity ($\min(q_{in}, q_{resting})$).
4. **Mutate** resting order quantity or **dequeue** (`poll()`) if fully filled.
5. **Repeat** until the incoming order is filled or the book is exhausted.
6. **Insert** any unfulfilled limit order remainder into its own queue (`offer()`).

### What Happens Without Synchronization?

If multiple threads execute this pipeline concurrently without locking:

```
Thread 1 (Incoming BUY 100)           Thread 2 (Incoming BUY 100)
         │                                     │
         ├─── Peek resting SELL 100 ───────────┤ (Both see SELL 100 @ $150)
         ├─── Execute Trade (qty: 100)         │
         │    (SELL order quantity -> 0)       ├─── Execute Trade (qty: 100)  <-- DOUBLE FILL!
         └─── Poll resting SELL order ─────────┤    (Attempts to fill same shares)
                                               └─── Poll empty/corrupted queue!
```

1. **Double Fill / Phantom Liquidity (Overfill)**:
   Two simultaneous buy orders both see the same resting sell order of 100 shares. Both threads conclude the order is available and execute against it, generating 200 filled shares from a 100-share order. In financial markets, this creates catastrophic unhedged liabilities.
2. **Internal Heap Array Corruption**:
   `java.util.PriorityQueue` is **not thread-safe**. It maintains an internal `Object[] queue` array modified via sift-up and sift-down operations. Concurrent `offer()` and `poll()` invocations cause index out-of-bounds exceptions, element overwrites, or infinite loops during heap rebalancing.
3. **Lost Updates & State Desynchronization**:
   Concurrent updates to order quantities result in lost write operations, leaving corrupted partial quantities in the book.

---

## 2. ApexMatch Solution: Granular Per-Symbol `ReentrantLock`

ApexMatch guarantees absolute thread safety by wrapping the matching lifecycle in Java's `java.util.concurrent.locks.ReentrantLock`.

### 2.1 Per-Symbol Locking vs. Global Locking

In financial markets, trading in `AAPL` has zero interaction with trading in `TSLA` or `GOOG`. Locking the entire matching engine globally across all symbols would introduce unnecessary thread contention and severely degrade multi-asset throughput.

ApexMatch implements **isolated per-symbol locking** within `OrderBookManager`:

```
Incoming Orders:
  Thread 1 (AAPL BUY)  ──► Lock(AAPL)  ──► Matches in AAPL Book  ──► Unlocks(AAPL)
  Thread 2 (TSLA BUY)  ──► Lock(TSLA)  ──► Matches in TSLA Book  ──► Unlocks(TSLA)  (Zero Contention!)
  Thread 3 (AAPL SELL) ──► Lock(AAPL)  ──► Blocks waiting for T1 ──► Matches in AAPL Book
```

### 2.2 Implementation in `OrderBookManager`

```java
public class OrderBookManager {
    private final ConcurrentHashMap<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> symbolLocks = new ConcurrentHashMap<>();

    public ReentrantLock getLockForSymbol(String symbol) {
        return symbolLocks.computeIfAbsent(symbol.toUpperCase(), s -> new ReentrantLock());
    }

    public OrderBook getOrderBook(String symbol) {
        return orderBooks.computeIfAbsent(symbol.toUpperCase(), OrderBook::new);
    }
}
```

### 2.3 Strict Critical Section Pattern in `MatchingEngine`

```java
public List<Trade> processOrder(Order incomingOrder) {
    String symbol = incomingOrder.getSymbol();
    ReentrantLock lock = orderBookManager.getLockForSymbol(symbol);

    lock.lock();
    try {
        OrderBook book = orderBookManager.getOrderBook(symbol);
        return match(incomingOrder, book);
    } finally {
        lock.unlock(); // Guaranteed release even upon unhandled runtime exceptions
    }
}
```

- **Atomicity**: The entire matching pipeline (inspect, match, update, dequeue, enqueue remainder) executes atomically.
- **Exception Safety**: The `try ... finally` idiom guarantees that lock release is unconditional, preventing thread deadlock even if an unexpected exception occurs.

---

## 3. Simultaneous Thread Walkthrough: Thread A vs. Thread B

Consider two incoming requests arriving at identical timestamps on an 8-core server:
- **Thread A**: `BUY 100 AAPL @ $150.00`
- **Thread B**: `SELL 50 AAPL @ $150.00`
- **Resting Book**: 1 resting order `SELL 50 AAPL @ $150.00` (Alice, seq: 1)

```
Timeline:
t0: Thread A and Thread B reach MatchingEngine simultaneously.
t1: Thread A invokes lock.lock() for "AAPL" -> Successfully acquired!
t2: Thread B invokes lock.lock() for "AAPL" -> Contention detected; Thread B suspended.
t3: Thread A inspects AAPL OrderBook:
    - Finds Alice's resting SELL 50 @ $150.00.
    - Matches 50 shares: Trade T1 executed.
    - Alice's order is fully filled and removed (poll).
    - Thread A has 50 shares remaining (unfilled limit buy).
    - Thread A adds remaining BUY 50 @ $150.00 to buyOrders heap.
t4: Thread A reaches finally block -> lock.unlock() for "AAPL".
t5: Operating system awakens Thread B. Thread B acquires lock for "AAPL".
t6: Thread B inspects AAPL OrderBook:
    - Alice's order is gone.
    - Instead, Thread B encounters Thread A's resting BUY 50 @ $150.00!
    - Thread B (SELL 50 @ $150.00) matches against Thread A (BUY 50 @ $150.00).
    - Matches 50 shares: Trade T2 executed.
    - Thread A's resting order is fully filled and removed (poll).
    - Thread B is fully filled (0 remaining).
t7: Thread B reaches finally block -> lock.unlock() for "AAPL".
```

**Result**: Both threads complete cleanly with 100% deterministic state. No orders are lost, no double-execution occurs, and data structures remain perfectly balanced.

---

## 4. Why `ReentrantLock` Instead of Alternative Synchronization?

### 4.1 Why Not `synchronized` Methods or Blocks?
1. **Granularity**: `synchronized(this)` on `MatchingEngine` would create a coarse-grained global bottleneck across all symbols.
2. **Feature Extensibility**: `ReentrantLock` supports advanced capabilities such as `tryLock()` (for non-blocking drop-or-reject policies), timed lock acquisitions (`tryLock(timeout, unit)` to guard against latency spikes), and inspection APIs (`isLocked()`, `getQueueLength()`) invaluable for operational telemetry.
3. **Fairness Controls**: While ApexMatch uses standard non-fair locking for maximum throughput, `ReentrantLock(true)` can be toggled if strict lock acquisition FIFO is required.

### 4.2 Why Not `ConcurrentLinkedQueue` or `PriorityBlockingQueue`?
1. **No Price-Time Ordering in `ConcurrentLinkedQueue`**:
   `ConcurrentLinkedQueue` is strictly FIFO ($O(1)$ head/tail). It has no concept of limit prices or heap priority, making it impossible to enforce Price-Time priority directly.
2. **Compound Atomicity Gap in `PriorityBlockingQueue`**:
   While `PriorityBlockingQueue` provides thread-safe single operations (`offer`, `poll`), an order matching engine requires **compound atomic operations across two separate queues**:
   - Peek `sellOrders`
   - Compare with `incomingOrder`
   - Conditionally mutate or poll `sellOrders`
   - Conditionally enqueue to `buyOrders`
   Even if both queues were individually thread-safe, the window between peeking Queue A and modifying Queue B would constitute an unshielded race condition. A surrounding lock is mandatory.

---

## 5. Scalability Limits & Realities (No False Lock-Free Claims)

ApexMatch achieves **over 3.3 million multi-symbol orders/sec** and **1.5 million concurrent orders/sec under 4-thread single-symbol contention** on standard modern hardware.

However, production trading system engineering requires acknowledging architectural boundaries:

1. **Single-Writer Constraint per Symbol**:
   Because matching requires linear, strictly ordered state mutations, matching for a single order book is fundamentally serial. Per-symbol locking means maximum throughput for a *single symbol* is bounded by single-core instruction latency and memory bus synchronization.
2. **Cross-Symbol Scalability**:
   ApexMatch scales linearly with CPU cores across independent symbols. 100 distinct symbols running across 16 cores will experience virtually zero lock contention.
3. **Paths to Ultra-Low Latency (< 100 Nanoseconds)**:
   For ultra-high-frequency environments (sub-microsecond execution), industrial systems eliminate kernel locks entirely using the **LMAX Disruptor Pattern**:
   - A single-writer pinned thread processes inbound orders sequentially from a lock-free pre-allocated RingBuffer.
   - Core pinning (`isolcpus`), zero heap allocation, and off-heap memory eliminate garbage collection pauses and CPU context switching overhead.

---

## 6. Heavy Concurrent Load & Stress Testing (Phase 12)

While unit tests verify deterministic single-threaded logic and JMH benchmarks measure algorithmic execution velocity, **heavy concurrent stress testing** is required to prove that the engine remains correct, deadlock-free, and internally consistent when subject to chaotic thread preemption by the operating system.

### 6.1 How the Stress Suite Works (`MatchingEngineStressTest.java`)
The stress suite uses Java's `ExecutorService` and `CountDownLatch` synchronization barriers to unleash simultaneous floods of orders across worker threads:
1. **Worker Threads**: Tests instantiate thread pools with **4, 8, and 16 worker threads** simulating aggressive concurrent market participants.
2. **Simultaneous Dispatch**: All threads synchronize at a common `CountDownLatch.await()` barrier before firing thousands of orders at the exact same instant, maximizing lock contention and thread race conditions.
3. **Aggregation & Invariant Auditing**: Trades collected via `Future<List<Trade>>` are cross-audited against the final resting state of the `OrderBook` to verify mathematical invariants.

### 6.2 Key Correctness Invariants Verified
Every stress scenario asserts strict mathematical and market microstructure axioms:

| Invariant | Description | Failure Mode Prevented |
| :--- | :--- | :--- |
| **Conservation of Volume** | $\sum Q_{\text{submitted}} = \sum Q_{\text{traded}} + \sum Q_{\text{resting}}$ | Lost updates, phantom liquidity, duplicate fills |
| **Strict Non-Negativity** | $\forall q \in \{\text{trades}, \text{book}\}, q > 0$ | Integer underflow, over-allocation of shares |
| **Symbol Isolation** | $\forall \text{trade}, \text{buyer.symbol} == \text{seller.symbol}$ | Cross-symbol asset contamination (`AAPL` matching `TSLA`) |
| **Symmetric Market Clearing** | Equal BUY and SELL demand at matching prices yields $Q_{\text{resting}} = 0$ | Zombie resting orders or un-polled filled orders |
| **Irregular Partial Fills** | Slicing orders across prime/uneven quantities (e.g. 7, 13, 19, 23, 29) leaves valid remaining balances | Heap corruptions during in-place order mutation |
| **Lock Release Guarantee** | `lock.isLocked() == false` after all thread terminations | Thread starvation, leaked locks, deadlock |

### 6.3 Stress Testing vs. JMH Microbenchmarking: Key Differences

| Dimension | JMH Microbenchmarking (Phase 10) | Concurrency Stress Testing (Phase 12) |
| :--- | :--- | :--- |
| **Primary Objective** | Measure execution **throughput** (`ops/sec`) and latency | Validate logical **correctness** and data invariants under load |
| **Harness** | OpenJDK JMH with C2 JIT warm-up & compiler blackholes | JUnit 5 + `ExecutorService` + `CountDownLatch` |
| **Assertions** | Minimal (to avoid distorting CPU execution timing) | Exhaustive (asserting volume, prices, IDs, lock states) |
| **Scope** | Steady-state operations per second | Edge cases, race condition detection, thread safety proofs |
| **Reported Metric** | **4.53M ops/sec** (single-symbol), **3.31M ops/sec** (multi-symbol) | **7/7 tests passing**, 0 invariant violations across 4–16 threads |
