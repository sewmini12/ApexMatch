# ApexMatch Order Matching Algorithm

ApexMatch implements a continuous electronic order matching engine utilizing the **Price-Time Priority (FIFO)** rule, the industry-standard algorithm used by major financial exchanges (e.g., NASDAQ, NYSE, LSE).

---

## 1. Price-Time Priority Principles

Orders resting in the order book are prioritized primarily by price competitiveness, and secondarily by arrival sequence.

### 1.1 BUY (Bid) Priority
- **Primary Rule**: Higher bid prices have priority over lower bid prices (buyers willing to pay more are served first).
- **Secondary Rule (Tie-Breaker)**: If two buy orders have the identical price, the order with the earlier sequence number (older order) has priority.

#### Example:
Consider three resting BUY orders:
1. `BUY 100 @ $150.00` (Sequence: 3)
2. `BUY 100 @ $155.00` (Sequence: 5)
3. `BUY 100 @ $150.00` (Sequence: 2)

**Evaluation Order**:
1. `$155.00` (Sequence 5) — *Highest price*
2. `$150.00` (Sequence 2) — *Same price, earlier sequence*
3. `$150.00` (Sequence 3) — *Same price, later sequence*

---

### 1.2 SELL (Ask) Priority
- **Primary Rule**: Lower ask prices have priority over higher ask prices (sellers willing to accept less are served first).
- **Secondary Rule (Tie-Breaker)**: If two sell orders have the identical price, the order with the earlier sequence number (older order) has priority.

#### Example:
Consider three resting SELL orders:
1. `SELL 50 @ $146.00` (Sequence: 4)
2. `SELL 50 @ $145.00` (Sequence: 2)
3. `SELL 50 @ $145.00` (Sequence: 1)

**Evaluation Order**:
1. `$145.00` (Sequence 1) — *Lowest price, earlier sequence*
2. `$145.00` (Sequence 2) — *Lowest price, later sequence*
3. `$146.00` (Sequence 4) — *Higher price*

---

## 2. Java PriorityQueue & Heap Ordering

ApexMatch leverages Java's `java.util.PriorityQueue` (a binary heap) to guarantee efficient retrieval of the best bid and best ask in $O(1)$ time and heap modification in $O(\log N)$ time.

### BUY Comparator
```java
Comparator.comparing(Order::getPrice)
          .reversed()
          .thenComparing(Order::getSequenceNumber);
```
- Orders with higher prices are prioritized (reverse natural order).
- Tied prices are resolved by ascending sequence numbers (natural order).

### SELL Comparator
```java
Comparator.comparing(Order::getPrice)
          .thenComparing(Order::getSequenceNumber);
```
- Orders with lower prices are prioritized (natural order).
- Tied prices are resolved by ascending sequence numbers (natural order).

---

## 3. Order Types & Execution Rules

### 3.1 LIMIT Orders
A LIMIT order specifies a maximum purchase price for a BUY, or a minimum selling price for a SELL:
- **Incoming LIMIT BUY**: Matches against resting SELL orders if `buyPrice >= sellPrice`.
- **Incoming LIMIT SELL**: Matches against resting BUY orders if `sellPrice <= buyPrice`.
- If unmatched or partially filled, the remainder rests in the order book.

### 3.2 MARKET Orders
A MARKET order requests immediate execution at the best available counter-order price:
- Does not specify a limit price (`price == null`).
- Unconditionally consumes the best counter-orders until filled or until the counter-book is empty.
- Any unfulfilled remainder does **not** enter the order book (preventing null-price comparator failures) and is handled per exchange policy.

### 3.3 Trade Execution Price
The execution price is always determined by the **resting order** (the passive order already waiting in the book), not the aggressive incoming order:
- If an existing SELL order rests at `$145.00` and an incoming LIMIT BUY arrives at `$150.00`, the trade executes at **`$145.00`** (price improvement for the buyer).
- If an existing BUY order rests at `$150.00` and an incoming LIMIT SELL arrives at `$145.00`, the trade executes at **`$150.00`** (price improvement for the seller).

---

## 4. Partial Fills & Multi-Order Matching

When an incoming order quantity differs from the counter-order quantity:
1. **Trade Quantity**: $\min(\text{incoming quantity}, \text{opposite quantity})$.
2. **Book Update**:
   - If the counter-order quantity reaches `0`, it is dequeued from the book (`poll()`).
   - If the counter-order is partially filled, its remaining balance stays at the front of the book with its original priority sequence intact.
3. **Loop Continuation**: If the incoming order still has remaining unfilled quantity, the matching loop evaluates the next best counter-order until either:
   - The incoming quantity reaches `0`.
   - The opposite book is exhausted.
   - The price boundary condition fails (`buyPrice < sellPrice`).

---

## 5. Stock Symbol Protection

Orders across different ticker symbols (e.g., `AAPL` vs. `TSLA`) must never match:
- The engine enforces `incomingOrder.getSymbol().equals(oppositeOrder.getSymbol())`.
- If symbols do not match, matching terminates immediately, preventing cross-symbol asset pollution.
