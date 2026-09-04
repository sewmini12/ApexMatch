# ApexMatch Database Documentation

ApexMatch uses **PostgreSQL** as the persistent relational store for executed trades and audit history, accessed through **Spring Data JPA** and **Hibernate**.

---

## 1. Architectural Pipeline

The system enforces a clean separation of concerns between domain calculation and data persistence:

```text
       +-------------------------+
       |        OrderBook        |   (In-Memory PriorityQueues)
       +-------------------------+
                    |
                    v
       +-------------------------+
       |     MatchingEngine      |   (Executes matches, generates trades)
       +-------------------------+
                    |
                    v
       +-------------------------+
       |    Trade (Domain)       |   (Immutable business record)
       +-------------------------+
                    |
                    v
       +-------------------------+
       |   TradeEntity (JPA)     |   (Relational mapping model)
       +-------------------------+
                    |
                    v
       +-------------------------+
       |     TradeRepository     |   (Spring Data JPA Repository)
       +-------------------------+
                    |
                    v
       +-------------------------+
       |       PostgreSQL        |   (Persistent table: 'trades')
       +-------------------------+
```

---

## 2. Separation of Domain Model vs. Database Entity

### Why `Trade` and `TradeEntity` Are Decoupled
1. **Domain Purity**: The core `Trade` class represents an in-memory domain event. It is free of ORM annotations (`@Entity`, `@Table`, `@Id`), proxy classes, and database-specific lifecycle concerns.
2. **Performance & Isolation**: The `MatchingEngine` operates exclusively on lightweight domain objects. It has zero dependency on database drivers, transaction boundaries, or Hibernate sessions.
3. **Schema Evolution**: Database table columns and naming conventions can evolve independently of the in-memory domain model.
4. **Testability**: Unit tests for the matching engine remain blazingly fast and completely independent of any database engine.

---

## 3. Database Schema: `trades` Table

The database schema is managed via Hibernate DDL generation (`hibernate.ddl-auto=update`).

| Column Name   | Data Type         | Constraints                | Description                                       |
|:--------------|:------------------|:---------------------------|:--------------------------------------------------|
| `id`          | `BIGINT`          | Primary Key, Auto-Gen      | Internal surrogate database primary key           |
| `trade_id`    | `VARCHAR(64)`     | NOT NULL, Unique Index     | Business trade identifier (e.g., `TRD-1`)         |
| `symbol`      | `VARCHAR(16)`     | NOT NULL                   | Stock ticker symbol (e.g., `AAPL`)                |
| `buyer`       | `VARCHAR(64)`     | NOT NULL                   | Identifier of the buyer party                     |
| `seller`      | `VARCHAR(64)`     | NOT NULL                   | Identifier of the seller party                    |
| `price`       | `NUMERIC(19, 4)`  | NOT NULL                   | Executed trade price (precise decimal)            |
| `quantity`    | `BIGINT`          | NOT NULL                   | Executed share quantity                           |
| `executed_at` | `TIMESTAMP`       | NOT NULL                   | Timestamp when trade was executed                 |

---

## 4. Database Connection Configuration

Configuration is located in `src/main/resources/application.properties`. 

> [!IMPORTANT]
> Real credentials or production passwords must **never** be committed to version control. The application utilizes environment variable substitutions with local development defaults.

```properties
# DataSource Configuration
spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:apexmatch}
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASSWORD:postgres}
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA / Hibernate Configuration
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

### Local Environment Setup
To supply a custom password locally without modifying code:
```bash
# Windows PowerShell
$env:DB_PASSWORD="your_secure_password"
$env:DB_USER="postgres"
mvn spring-boot:run
```

For environments where PostgreSQL is not running during local unit test suites, unit tests run purely against in-memory domain models without starting the Spring Data JPA container.
