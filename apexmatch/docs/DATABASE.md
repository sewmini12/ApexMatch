# ApexMatch Database Documentation: PostgreSQL & Neon Integration

ApexMatch uses **PostgreSQL** hosted on **Neon** as the persistent relational database for recording executed trades and audit history, accessed through **Spring Data JPA** and **Hibernate**.

---

## 1. Architectural Pipeline & Responsibilities

The system maintains a strict separation of concerns between real-time, microsecond-latency in-memory matching and durable relational persistence:

```text
       +-------------------------+
       |        OrderBook        |   (In-Memory PriorityQueue Heaps)
       +-------------------------+
                    │
                    ▼
       +-------------------------+
       |     MatchingEngine      |   (Price-time priority matching logic)
       +-------------------------+
                    │
                    ▼
       +-------------------------+
       |      Trade (Domain)     |   (Immutable business record)
       +-------------------------+
                    │
                    ▼
       +-------------------------+
       |    TradeEntity (JPA)    |   (Relational mapping model)
       +-------------------------+
                    │
                    ▼
       +-------------------------+
       |     TradeRepository     |   (Spring Data JPA Repository)
       +-------------------------+
                    │
                    ▼
       +-------------------------+
       |    PostgreSQL (Neon)    |   (Persistent 'trades' table)
       +-------------------------+
```

### Why In-Memory OrderBook vs. Persistent Database
1. **Latency Constraints**: Matching operations in electronic exchanges require microsecond speed. Disk I/O, database locks, and network latency on relational transactions would create severe throughput bottlenecks.
2. **OrderBook Remains In-Memory**: The binary heaps (`PriorityQueue`) provide $O(1)$ best bid/ask lookups and $O(\log N)$ insertions and removals.
3. **Database Is for Trade History**: Completed trades are persisted to PostgreSQL for settlement, auditability, reporting, and regulatory compliance.

---

## 2. Why Neon & Cloud PostgreSQL?

- **Serverless PostgreSQL**: Neon provides fully managed cloud PostgreSQL with automated branching, scaling, and backups.
- **Zero Local Footprint**: Eliminates local daemon and port configuration dependencies, ensuring uniform behavior across developer machines and cloud deployment environments.
- **Enforced Encryption (SSL/TLS)**: Secure by default via `sslmode=require`.

---

## 3. Separation of Domain Model vs. Database Entity

### Domain `Trade` vs JPA `TradeEntity`
- **Domain `Trade` (`com.apexmatch.model.Trade`)**: Lightweight, plain Java object representing an executed trade event produced by the `MatchingEngine`. Free of ORM annotations, proxies, or database dependencies.
- **JPA `TradeEntity` (`com.apexmatch.entity.TradeEntity`)**: Annotated with `@Entity` and `@Table(name = "trades")`, mapping table columns, surrogate primary keys, and timestamp metadata.
- **Mapping in `OrderService`**:
  ```java
  List<TradeEntity> entities = trades.stream()
      .map(trade -> new TradeEntity(
              trade.getTradeId(),
              trade.getSymbol(),
              trade.getBuyerId(),
              trade.getSellerId(),
              trade.getPrice(),
              trade.getQuantity(),
              LocalDateTime.now()
      ))
      .toList();
  tradeRepository.saveAll(entities);
  ```

---

## 4. Database Schema: `trades` Table

The schema is automatically synchronized via Hibernate (`spring.jpa.hibernate.ddl-auto=update`):

| Column Name   | Data Type         | Constraints                | Description                                       |
|:--------------|:------------------|:---------------------------|:--------------------------------------------------|
| `id`          | `BIGINT`          | Primary Key, Auto-Gen      | Surrogate primary key (IDENTITY)                  |
| `trade_id`    | `VARCHAR(64)`     | NOT NULL, Unique Index     | Business trade identifier (e.g., `TRD-1`)         |
| `symbol`      | `VARCHAR(16)`     | NOT NULL                   | Stock ticker symbol (e.g., `AAPL`)                |
| `buyer`       | `VARCHAR(64)`     | NOT NULL                   | Identifier of buyer trader                        |
| `seller`      | `VARCHAR(64)`     | NOT NULL                   | Identifier of seller trader                       |
| `price`       | `NUMERIC(19, 4)`  | NOT NULL                   | Trade price (BigDecimal precision, no floats)    |
| `quantity`    | `BIGINT`          | NOT NULL                   | Traded share volume                               |
| `executed_at` | `TIMESTAMP`       | NOT NULL                   | Execution timestamp                               |

---

## 5. Connecting to Neon PostgreSQL

Spring Boot reads the database parameters from environment variables defined in `src/main/resources/application.properties`:

```properties
spring.application.name=apexmatch
server.port=8080

# PostgreSQL / Neon DataSource Configuration
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

### Where to Place Your Neon Database Link

You can supply your Neon credentials using either of the following approaches:

#### Option 1: Using a `.env` file (Recommended for Local Dev)
1. Copy `.env.example` to `.env` in the root of the project:
   ```bash
   cp .env.example .env
   ```
2. Open `.env` and fill in your Neon details:
   ```env
   DB_URL=jdbc:postgresql://ep-your-host.region.aws.neon.tech/neondb?sslmode=require
   DB_USERNAME=your_username
   DB_PASSWORD=your_neon_password
   ```
   *(Note: `.env` is already configured in `.gitignore` so your secrets will never be committed to Git).*

#### Option 2: Terminal Environment Variables
Set the variables directly in your terminal before launching the application:

**Windows PowerShell:**
```powershell
$env:DB_URL="jdbc:postgresql://ep-your-host.region.aws.neon.tech/neondb?sslmode=require"
$env:DB_USERNAME="your_username"
$env:DB_PASSWORD="your_neon_password"

mvn spring-boot:run
```

**Windows Command Prompt (cmd):**
```cmd
set DB_URL=jdbc:postgresql://ep-your-host.region.aws.neon.tech/neondb?sslmode=require
set DB_USERNAME=your_username
set DB_PASSWORD=your_neon_password

mvn spring-boot:run
```

**Linux / macOS:**
```bash
export DB_URL="jdbc:postgresql://ep-your-host.region.aws.neon.tech/neondb?sslmode=require"
export DB_USERNAME="your_username"
export DB_PASSWORD="your_neon_password"

mvn spring-boot:run
```

---

## 6. Verifying Data in Neon

Once an order match occurs via `POST /api/orders`, verify the persisted trade records directly in the **Neon SQL Editor**:

```sql
-- View all executed trades
SELECT * FROM trades ORDER BY executed_at DESC;

-- Verify specific stock trades
SELECT trade_id, symbol, buyer, seller, price, quantity, executed_at 
FROM trades 
WHERE symbol = 'AAPL';
```

---

## 7. Security Best Practices

1. **No Credentials in Git**: Never hardcode database passwords, hostnames, or usernames in source code or properties files.
2. **Git Ignore Protection**: Ensure `.env` and `application-local.properties` are listed in `.gitignore`.
3. **Template Tracking**: Keep only `.env.example` committed with non-sensitive placeholder values.
