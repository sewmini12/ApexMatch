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

## Phase 2 — Spring Boot REST API (In Progress)
- [x] Spring Boot application wrapper (`ApexMatchApplication`).
- [x] REST Controller (`OrderController`) with `POST /api/orders`.
- [x] `OrderRequest` DTO separating client inputs from internal sequence/ID generation.
- [x] Service orchestration layer (`OrderService`).
- [ ] Resolving package boundaries, dependency injection wiring, and constructor injection.
- [ ] End-to-end HTTP request processing tests via Postman / MockMvc.

---

## Phase 3 — PostgreSQL & Trade Persistence (In Progress)
- [x] PostgreSQL driver and Spring Data JPA dependencies in `pom.xml`.
- [ ] `TradeEntity` JPA entity mapping to `trades` table.
- [ ] `TradeRepository` interface extending `JpaRepository`.
- [ ] `application.properties` database connection and HikariCP pooling.
- [ ] Mapping domain `Trade` objects to `TradeEntity` upon execution in `OrderService`.
- [ ] Automated verification of persisted trades in PostgreSQL.

---

## Phase 4 — Validation & Error Handling (Future)
- [ ] Spring Validation annotations (`@Valid`, `@NotBlank`, `@Positive`) on `OrderRequest`.
- [ ] Global exception handler (`@RestControllerAdvice`).
- [ ] Structured error response schema (`timestamp`, `status`, `error`, `message`, `path`).
- [ ] Proper HTTP status code mappings (`400 Bad Request`, `422 Unprocessable Entity`).

---

## Phase 5 — Concurrency & Thread Safety (Future)
- [ ] Introduce per-symbol `ReentrantLock` instances to guarantee atomic matching operations.
- [ ] Eliminate race conditions when concurrent BUY/SELL orders target identical counter-orders.
- [ ] Concurrent multi-threaded order generation stress test suite.

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
