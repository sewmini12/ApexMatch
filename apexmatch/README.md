# ApexMatch — Ultra-High-Performance Order Matching Engine

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-007396?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20%2F%20Neon-336791?style=for-the-badge&logo=postgresql&logoColor=white)](https://neon.tech/)
[![Docker](https://img.shields.io/badge/Docker-Multi--Stage-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![JMH](https://img.shields.io/badge/JMH-1.37%20Benchmarked-FF6F00?style=for-the-badge&logo=openjdk&logoColor=white)](https://github.com/openjdk/jmh)
[![JUnit 5](https://img.shields.io/badge/JUnit-71%20Tests%20Passing-25A162?style=for-the-badge&logo=junit5&logoColor=white)](https://junit.org/junit5/)
[![OpenAPI](https://img.shields.io/badge/OpenAPI-Swagger%20UI-85EA2D?style=for-the-badge&logo=swagger&logoColor=black)](http://localhost:8080/swagger-ui/index.html)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)

**ApexMatch** is a deterministic, microsecond-grade electronic stock order matching engine engineered with **Java 21** and **Spring Boot 3.5.5**. Simulating the core continuous double-auction mechanics of tier-one financial exchanges (NASDAQ, NYSE, LSE), ApexMatch implements **Price-Time Priority (FIFO)** execution across independent trading symbols using dual in-memory binary heaps (`PriorityQueue`). It pairs ultra-low-latency in-memory execution (**over 4.5 million orders/second**) with fine-grained per-symbol `ReentrantLock` concurrency, ACID-compliant **PostgreSQL** trade persistence, interactive **Swagger/OpenAPI** documentation, and a zero-mock **Testcontainers** testing harness.

---

## Key Highlights

- ⚡ **~4.5 Million Orders/Sec**: Microbenchmarked using OpenJDK's Java Microbenchmark Harness (JMH 1.37) with ~220 ns execution latency per match.
- 🎯 **Price-Time Priority (FIFO)**: High bids and low asks are strictly prioritized; ties are resolved deterministically via monotonic sequence numbers.
- 🌐 **Multi-Symbol Order Book Partitioning**: Dedicated, dynamically allocated order books for `AAPL`, `GOOG`, `TSLA`, etc., with zero cross-symbol asset pollution.
- 🔒 **Thread-Safe Granular Concurrency**: Per-symbol `ReentrantLock` synchronization eliminates thread contention between independent symbols while preventing double-fills and heap array corruption.
- 🗄️ **Relational Settlement Ledger**: Durable, immutable audit log of executed trades persisted to PostgreSQL (Neon serverless cloud or local container) via Spring Data JPA.
- 🧪 **71 Automated Tests**: 58 unit/concurrency tests + 7 heavy load/stress tests + 6 real-database integration tests powered by Testcontainers (100% passing, 0 failures, 0 errors, 0 skipped).
- 📜 **Interactive Swagger UI**: Living OpenAPI 3.0 documentation with full Bean Validation contract enforcement and interactive testing playground.
- 🐳 **Production Docker Container**: Optimized multi-stage build producing an alpine JRE container image of only ~280MB.

---

## Architecture Overview

ApexMatch cleanly separates the volatile, microsecond-latency matching pipeline from durable relational trade persistence:

```text
                                CLIENT / TRADER
                                       │
                                       ▼ HTTP POST /api/orders
                                ┌──────────────┐
                                │OrderControler│  (REST API & Bean Validation)
                                └──────┬───────┘
                                       │
                                       ▼
                                ┌──────────────┐
                                │ OrderService │  (Sequence & Ingestion Pipeline)
                                └──────┬───────┘
                                       │
                                       ▼
                                ┌──────────────┐
                                │MatchingEngine│  (Acquires Symbol ReentrantLock)
                                └──────┬───────┘
                                       │
                   ┌───────────────────┴───────────────────┐
                   ▼                                       ▼
        ┌─────────────────────┐                 ┌─────────────────────┐
        │  OrderBookManager   │                 │     PostgreSQL      │
        │                     │                 │   (Settlement DB)   │
        │ ┌─────────────────┐ │                 │                     │
        │ │AAPL  OrderBook  │ │                 │  ┌───────────────┐  │
        │ │ • Max-Heap BUY  │ │                 │  │ trades table  │  │
        │ │ • Min-Heap SELL │ │                 │  │ (Audit Log)   │  │
        │ └─────────────────┘ │  Trades Settled │  └───────────────┘  │
        │ ┌─────────────────┐ ├────────────────►│                     │
        │ │TSLA  OrderBook  │ │  Via JPA Repo   │  • Trade ID         │
        │ │ • Max-Heap BUY  │ │                 │  • Price & Quantity │
        │ │ • Min-Heap SELL │ │                 │  • Counterparties   │
        │ └─────────────────┘ │                 │  • Execution Time   │
        │ ┌─────────────────┐ │                 └─────────────────────┘
        │ │GOOG... (Dynamic)│ │
        │ └─────────────────┘ │
        └─────────────────────┘
```

For a comprehensive review of state boundaries and lifecycle sequence diagrams, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Performance Benchmarking (JMH 1.37 Verified)

Core matching throughput was microbenchmarked using OpenJDK's official **Java Microbenchmark Harness (JMH)** on modern x86_64 architecture running Java 21:

### Microbenchmark Steady-State Results
| Benchmark Scenario | Throughput | Approx. Latency | Workload Characteristic |
| :--- | :--- | :--- | :--- |
| **`benchmarkMatchingOrders`** | **4,528,377 ops/sec** | **~220.8 ns** | Continuous 1:1 limit order matching |
| **`benchmarkMultiSymbolWorkload`** | **3,309,832 ops/sec** | **~302.1 ns** | Round-robin routing across 5 distinct symbols |
| **`benchmarkOrderInsertion`** | **3,258,037 ops/sec** | **~306.9 ns** | Pure resting order heap growth (no counter-trades) |
| **`benchmarkConcurrentWorkload`** | **1,502,634 ops/sec** | **~665.5 ns** | 4 concurrent threads contending on single symbol |
| **`benchmarkPartialMatching`** | **1,205,187 ops/sec** | **~829.7 ns** | 1 large order sweeping multiple resting levels |

### Batch Burst Execution Velocity
| Batch Size | Ingestion Rate (Orders/Sec) | Continuous Match Rate (Orders/Sec) |
| :--- | :--- | :--- |
| **1,000 Orders** | **3,556,760 orders/sec** | **5,605,120 orders/sec** |
| **10,000 Orders** | **3,408,500 orders/sec** | **4,571,900 orders/sec** |
| **50,000 Orders** | **2,827,000 orders/sec** | **4,856,000 orders/sec** |

*Note: Benchmarks measure the core algorithmic in-memory engine. End-to-end HTTP/network throughput is bounded by network latency and synchronous database I/O. For detailed methodology and hardware disclaimers, see [docs/PERFORMANCE.md](docs/PERFORMANCE.md).*

---

## Quick Start Guide

### Prerequisites
- **Java 21** (JDK) or higher
- **Maven 3.9+**
- **Docker** (Optional, required only for Testcontainers integration tests or containerized deployment)

### 1. Clone & Compile
```bash
git clone https://github.com/sewmini12/ApexMatch.git
cd ApexMatch/apexmatch
mvn clean compile
```

### 2. Run Locally with Maven
Supply your PostgreSQL connection (Neon Cloud or local instance):
```bash
# Set environment variables (PowerShell example)
$env:DB_URL="jdbc:postgresql://localhost:5432/apexmatch"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="password"

mvn spring-boot:run
```
*(Or create a `.env` file from `.env.example`)*

### 3. Run with Docker
Build and launch using the production multi-stage container:
```bash
# Build the optimized Docker image
docker build -t apexmatch .

# Run the container
docker run -d -p 8080:8080 \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://host.docker.internal:5432/apexmatch" \
  -e SPRING_DATASOURCE_USERNAME="postgres" \
  -e SPRING_DATASOURCE_PASSWORD="password" \
  --name apexmatch-container \
  apexmatch
```

### 4. Run Automated Tests
```bash
# Run the complete test suite (Unit + Testcontainers integration)
mvn clean test

# Run only fast unit and concurrency tests (skip Docker requirement)
mvn test -Dtest="!*IntegrationTest*"

# Run only Testcontainers PostgreSQL integration tests
mvn test -Dtest="*IntegrationTest*"
```

### 5. Run JMH Performance Benchmarks
```bash
# Compile benchmark classes
mvn clean test-compile

# Run core microbenchmarks
mvn exec:java -Dexec.classpathScope=test -Dexec.mainClass=org.openjdk.jmh.Main -Dexec.args="MatchingEngineBenchmark"

# Run batch workload benchmarks
mvn exec:java -Dexec.classpathScope=test -Dexec.mainClass=org.openjdk.jmh.Main -Dexec.args="MatchingEngineBatchBenchmark"
```

---

## REST API Reference

When ApexMatch is running locally, access the interactive Swagger UI at:
👉 **[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)**
👉 **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`

### Submit an Order
`POST /api/orders`
```json
{
  "userId": "ALICE",
  "symbol": "AAPL",
  "side": "BUY",
  "type": "LIMIT",
  "price": 150.00,
  "quantity": 100
}
```

### Execution Response (Immediate Fill)
```json
[
  {
    "tradeId": "TRD-1",
    "symbol": "AAPL",
    "buyer": "ALICE",
    "seller": "BOB",
    "price": 149.50,
    "quantity": 100
  }
]
```

*For complete endpoint specifications, validation rules, and error schemas, see [docs/API.md](docs/API.md).*

---

## Comprehensive Documentation Index

Explore the engineering deep-dives for every subsystem:

| Document | Description |
| :--- | :--- |
| 📐 [**ARCHITECTURE.md**](docs/ARCHITECTURE.md) | System layers, request lifecycle flow, in-memory vs. persistent state boundaries, and Mermaid diagrams. |
| ⚙️ [**MATCHING_ALGORITHM.md**](docs/MATCHING_ALGORITHM.md) | Price-Time Priority principles, dual-heap comparators, step-by-step trader scenarios, and time complexity. |
| 🔒 [**CONCURRENCY.md**](docs/CONCURRENCY.md) | Shared-state race conditions, per-symbol `ReentrantLock` isolation, Thread A vs B walkthrough, and lock trade-offs. |
| 🗄️ [**DATABASE.md**](docs/DATABASE.md) | PostgreSQL persistent ledger role, `trades` table DDL, `Trade` vs `TradeEntity` decoupling, and Neon setup. |
| 🌐 [**API.md**](docs/API.md) | REST API contract, Jakarta Bean Validation constraints, HTTP 400 error schema, and 7 concrete scenarios. |
| 🧪 [**TESTING.md**](docs/TESTING.md) | Test suite breakdown (71 tests across 3 tiers), Testcontainers integration, stress test harnesses, and commands. |
| 📊 [**PERFORMANCE.md**](docs/PERFORMANCE.md) | JMH 1.37 benchmark results, 5 microbenchmark scenarios, batch burst metrics, and execution instructions. |
| 💡 [**TECHNOLOGY_DECISIONS.md**](docs/TECHNOLOGY_DECISIONS.md) | Interview-ready technical rationales for Java 21, Spring Boot, PriorityQueue, PostgreSQL, and Testcontainers. |

---

## Project Roadmap & Evolution

- [x] **Phase 1**: Core In-Memory Domain Models & Price-Time Priority Heaps
- [x] **Phase 2**: Spring Boot REST Order Ingestion API
- [x] **Phase 3**: PostgreSQL & Neon Relational Trade Persistence
- [x] **Phase 4**: Concurrency & Thread-Safety via `ReentrantLock`
- [x] **Phase 5**: Multi-Symbol Order Book Architecture & Partitioned Locks
- [x] **Phase 6**: Interactive OpenAPI / Swagger UI Documentation
- [x] **Phase 7**: API Validation & Global Structured Error Handling
- [x] **Phase 8**: True-to-Production PostgreSQL Integration Testing with Testcontainers
- [x] **Phase 9**: Production Multi-Stage Docker Containerization
- [x] **Phase 10**: Microsecond In-Memory Performance Benchmarking via JMH
- [x] **Phase 11**: Architecture & Portfolio Engineering Polish
- [x] **Phase 12**: Heavy Load & Stress Testing (4/8/16 threads, volume conservation, strict symbol isolation)
- [ ] **Phase 13 (Future)**: Asynchronous Event-Driven Persistence via Kafka / Disruptor Ring Buffer

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
