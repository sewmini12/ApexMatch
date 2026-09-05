# ApexMatch Phased Development Plan

This document defines the iterative roadmap for developing ApexMatch from a core matching engine prototype into an enterprise-grade financial matching engine.

---

## Phase 1 — Core Matching Engine (Completed)
- [x] Immutable domain definitions: `Order`, `OrderSide`, `OrderType`, `Trade`.
- [x] In-memory `OrderBook` using dual `PriorityQueue` max/min heaps.
- [x] Strict Price-Time Priority (FIFO) matching algorithm in `MatchingEngine`.
- [x] Partial fills, multiple order executions, and cross-symbol isolation.
- [x] Input validation rules via `OrderValidator`.
- [x] Comprehensive JUnit 5 unit test suite (24 unit tests covering matching and validation edge cases).

---

## Phase 2 — Spring Boot REST API (Completed)
- [x] Spring Boot application wrapper (`ApexMatchApplication`).
- [x] REST Controller (`OrderController`) with `POST /api/orders`.
- [x] `OrderRequest` DTO separating client inputs from internal sequence/ID generation.
- [x] Service orchestration layer (`OrderService`).
- [x] Resolving package boundaries, dependency injection wiring, and constructor injection.
- [x] End-to-end HTTP request processing tests via Postman / MockMvc (`OrderControllerTest`).

---

## Phase 3 — PostgreSQL & Trade Persistence (Completed)
- [x] PostgreSQL driver and Spring Data JPA dependencies in `pom.xml`.
- [x] `TradeEntity` JPA entity mapping to `trades` table.
- [x] `TradeRepository` interface extending `JpaRepository`.
- [x] `application.properties` database connection and HikariCP pooling.
- [x] Mapping domain `Trade` objects to `TradeEntity` upon execution in `OrderService`.
- [x] Automated verification of persisted trades in PostgreSQL with H2 test fallbacks.

---

## Phase 4 — Validation & Error Handling (Completed)
- [x] Domain validation (`OrderValidator`) and controller/service checks.
- [x] Global exception handling and structured error responses.
- [x] Proper HTTP status code mappings (`400 Bad Request`).

---

## Phase 5 — Concurrency & Thread Safety (Completed)
- [x] Introduce explicit `ReentrantLock` in `MatchingEngine` to guarantee atomic matching operations.
- [x] Protected critical section covering book access, matching, trade execution, and book mutation.
- [x] Guarantee unconditional lock release using `try/finally`.
- [x] Eliminate race conditions when concurrent orders target the shared order-book state.
- [x] Multi-threaded concurrent order execution test suite (`MatchingEngineConcurrencyTest` and `OrderServiceTest`).

---

## Phase 6 — Asynchronous Persistence (Future)
- [ ] Decouple trade persistence from the critical matching loop using Spring `@Async` or an event publisher (Disruptor / LMAX pattern).
- [ ] Guarantee matching engine microsecond response times independent of database latency.
- [ ] Resilient retry and dead-letter handling for database write failures.

---

## Phase 7 — Production Polish & Containerization (Future)
- [ ] OpenAPI 3 / Swagger documentation and Swagger UI.
- [ ] Structured JSON logging (SLF4J / Logback) with correlation IDs.
- [ ] Multi-stage `Dockerfile` and `docker-compose.yml` bundling Spring Boot and PostgreSQL.
- [ ] End-to-end integration test suite using Testcontainers.
- [ ] Performance benchmarking and documentation.
