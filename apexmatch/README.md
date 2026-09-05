# ApexMatch

ApexMatch is an electronic stock order matching engine developed with Java 21 and Spring Boot 3.5.x. It simulates the core matching logic of a financial exchange by accepting BUY and SELL orders and matching compatible counterparties based on the **Price-Time Priority (FIFO)** rule.

---

## Features

- **Price-Time Priority Matching**: Higher bids and lower asks have priority; orders with identical prices are matched in FIFO order using monotonic sequence numbers.
- **In-Memory PriorityQueue OrderBook**: Max-heap for BUY orders and min-heap for SELL orders for rapid lookup of the best counter-orders.
- **Order Types**:
  - **LIMIT**: Executes at the specified limit price or better; unexecuted quantity rests in the order book.
  - **MARKET**: Executes immediately against resting counter-orders at prevailing market prices.
- **Partial Fills & Multi-Match Support**: Single orders can match across multiple resting counter-orders until fully filled or counter-liquidity is exhausted.
- **Domain & Persistence Decoupling**: In-memory domain models (`Order`, `Trade`) operate independently from JPA database entities (`TradeEntity`).
- **RESTful Order Ingestion**: HTTP API for order placement, returning immediate trade execution reports.
- **PostgreSQL Trade Persistence**: Executed trades are recorded in a relational database for auditability and compliance.

---

## Technology Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.5.5
- **Build Tool**: Apache Maven
- **Web**: Spring Web (MVC / REST)
- **Persistence**: Spring Data JPA / Hibernate
- **Database**: PostgreSQL
- **Testing**: JUnit 5, Mockito

---

## Architecture Overview

ApexMatch processes orders through decoupled layers:

```text
                    POSTMAN / CLIENT
                           |
                           v
                    OrderController     (REST Endpoint)
                           |
                           v
                     OrderService       (Sequence generation & Orchestration)
                           |
                           v
                    MatchingEngine      (Price-Time Matching Logic)
                           |
                           v
                      OrderBook         (Dual PriorityQueue Heaps)
                           |
                           v
                         Trade          (Domain Trade Record)
                           |
                           v
                      TradeEntity       (JPA Entity)
                           |
                           v
                    TradeRepository     (Spring Data JPA)
                           |
                           v
                      PostgreSQL        (Persistent Storage)
```

For complete architectural details, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Order Matching Algorithm

1. **BUY Priority**:
   - Primary: Highest price first.
   - Secondary: Earliest sequence number first (FIFO).
2. **SELL Priority**:
   - Primary: Lowest price first.
   - Secondary: Earliest sequence number first (FIFO).
3. **Execution Price**: Determined by the passive order already resting in the book.
4. **Symbol Isolation**: Orders for different symbols (e.g., `AAPL` vs. `TSLA`) never match.

For in-depth algorithm mechanics, see [docs/ORDER_MATCHING.md](docs/ORDER_MATCHING.md).

---

## REST API Example

### Submit a Limit Sell Order
```http
POST /api/orders
Content-Type: application/json

{
  "userId": "BOB",
  "symbol": "AAPL",
  "side": "SELL",
  "type": "LIMIT",
  "price": 145.00,
  "quantity": 60
}
```
**Response** (`200 OK`):
```json
[]
```
*(Order rests in book as no matching BUY order exists)*

### Submit a Limit Buy Order
```http
POST /api/orders
Content-Type: application/json

{
  "userId": "ALICE",
  "symbol": "AAPL",
  "side": "BUY",
  "type": "LIMIT",
  "price": 150.00,
  "quantity": 100
}
```
**Response** (`200 OK`):
```json
[
  {
    "tradeId": "TRD-1",
    "symbol": "AAPL",
    "buyer": "ALICE",
    "seller": "BOB",
    "price": 145.00,
    "quantity": 60
  }
]
```
*(Alice matches Bob for 60 shares at $145.00; Alice's remaining 40 shares rest in the BUY book at $150.00)*

For full API specifications, see [docs/API.md](docs/API.md).

---

## Database Configuration (Neon PostgreSQL)

ApexMatch connects to PostgreSQL (such as [Neon](https://neon.tech)) via Spring Data JPA. Database credentials are read from environment variables:

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

To configure your Neon database:
1. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```
2. Set your Neon connection parameters:
   ```env
   DB_URL=jdbc:postgresql://ep-your-host.region.aws.neon.tech/neondb?sslmode=require
   DB_USERNAME=your_username
   DB_PASSWORD=your_neon_password
   ```

For comprehensive database architecture and schema details, see [docs/DATABASE.md](docs/DATABASE.md).

---

## Testing

The test suite covers matching rules, order validations, edge cases, and concurrency safety:
- 12 unit tests for `MatchingEngine` (market buys, limit matches, priority rules, FIFO tie-breaking, partial fills, multiple matches, symbol isolation).
- 12 unit tests for `OrderValidator` (null orders, invalid prices, negative quantities, boundary conditions).
- 3 unit tests for `OrderService` (Bob & Alice matching scenario, validation failures, and concurrent order processing).
- 2 web MVC tests for `OrderController` (REST API JSON serialization and endpoint status verification).
- 4 multi-threaded concurrency tests in `MatchingEngineConcurrencyTest` (competing buyers for limited liquidity, symmetric two-sided markets, cross-symbol isolation under concurrency, and lock release safety).

Run all 33 tests via Maven:
```bash
mvn clean test
```

---

## How to Run

### Prerequisites
- JDK 21+ installed and configured on `PATH`
- Maven 3.8+
- Neon PostgreSQL connection details (or any PostgreSQL instance)

### Running the Application

Set your environment variables and start Spring Boot:

**Windows PowerShell:**
```powershell
$env:DB_URL="jdbc:postgresql://ep-your-host.region.aws.neon.tech/neondb?sslmode=require"
$env:DB_USERNAME="your_username"
$env:DB_PASSWORD="your_password"

mvn spring-boot:run
```

**Linux / macOS:**
```bash
export DB_URL="jdbc:postgresql://ep-your-host.region.aws.neon.tech/neondb?sslmode=require"
export DB_USERNAME="your_username"
export DB_PASSWORD="your_password"

mvn spring-boot:run
```

Or run the standalone in-memory demonstration without a database:
```bash
java -cp target/classes com.apexmatch.Main
```

---

## Project Roadmap

- **Phase 4**: Bean Validation (`@Valid`) and global exception handling (`@RestControllerAdvice`).
- **Phase 5**: Thread safety and concurrency with per-symbol `ReentrantLock` instances.
- **Phase 6**: Asynchronous database persistence using worker queues / Spring `@Async`.
- **Phase 7**: OpenAPI/Swagger documentation, Docker containerization, and Testcontainers.

See [docs/DEVELOPMENT_PLAN.md](docs/DEVELOPMENT_PLAN.md) and [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md) for progress tracking.
