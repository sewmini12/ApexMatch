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

## Database Configuration

PostgreSQL connection settings are defined in `src/main/resources/application.properties` with environment variable overrides:

```properties
spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:apexmatch}
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASSWORD:postgres}
spring.jpa.hibernate.ddl-auto=update
```

For database schema and setup guidelines, see [docs/DATABASE.md](docs/DATABASE.md).

---

## Testing

The test suite covers matching rules, order validations, and edge cases:
- 12 unit tests for `MatchingEngine` (market buys, limit matches, priority rules, FIFO tie-breaking, partial fills, multiple matches, symbol isolation).
- 12 unit tests for `OrderValidator` (null orders, invalid prices, negative quantities, boundary conditions).

Run tests via Maven:
```bash
mvn clean test
```

---

## How to Run

### Prerequisites
- JDK 21+ installed and configured on `PATH`
- Maven 3.8+
- PostgreSQL (optional for pure unit testing, required for REST persistence)

### Running the Application
```bash
# Build the application
mvn clean package -DskipTests

# Run Spring Boot
mvn spring-boot:run
```

Or run the standalone demonstration:
```bash
mvn exec:java -Dexec.mainClass="com.apexmatch.Main"
```

---

## Project Roadmap

- **Phase 4**: Bean Validation (`@Valid`) and global exception handling (`@RestControllerAdvice`).
- **Phase 5**: Thread safety and concurrency with per-symbol `ReentrantLock` instances.
- **Phase 6**: Asynchronous database persistence using worker queues / Spring `@Async`.
- **Phase 7**: OpenAPI/Swagger documentation, Docker containerization, and Testcontainers.

See [docs/DEVELOPMENT_PLAN.md](docs/DEVELOPMENT_PLAN.md) and [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md) for progress tracking.
