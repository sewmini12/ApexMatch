# ApexMatch Performance Benchmarking & Latency Analysis

This document details the performance characteristics and algorithmic throughput of the ApexMatch core order matching engine, measured using the **Java Microbenchmark Harness (JMH)**.

---

## 1. Executive Summary & Headline Throughput

Under single-threaded and multi-threaded in-memory workloads, ApexMatch sustains:
- **Peak Single-Match Throughput**: **4,528,377 orders/second** (~220 ns per order match).
- **Batch Processing Velocity**: **5,605,000 orders/second** in 1,000-order matching bursts.
- **Multi-Symbol Independent Throughput**: **3,309,832 orders/second** across distinct symbols.
- **Pure Order Book Ingestion**: **3,258,037 resting orders/second**.
- **Heavy Concurrency (4 Threads Contended)**: **1,502,634 operations/second** under active lock acquisition.

---

## 2. Microbenchmark Results (`MatchingEngineBenchmark.java`)

Each benchmark scenario measures steady-state throughput (`ops/sec`) after JIT warm-up across 5 iterations (3 seconds per iteration, 1 fork).

| Benchmark Test Scenario | Score (Throughput) | Error Margin | Approx. Latency / Op | Description |
| :--- | :--- | :--- | :--- | :--- |
| **`benchmarkMatchingOrders`** | **4,528,377 ops/sec** | $\pm 84,655$ | **~220.8 ns** | Continuous 1:1 limit order matching (instant execution). |
| **`benchmarkMultiSymbolWorkload`** | **3,309,832 ops/sec** | $\pm 42,912$ | **~302.1 ns** | Round-robin routing across `AAPL`, `GOOG`, `TSLA`, `MSFT`. |
| **`benchmarkOrderInsertion`** | **3,258,037 ops/sec** | $\pm 58,140$ | **~306.9 ns** | Pure resting order insertion without matches (heap expansion). |
| **`benchmarkConcurrentWorkload`** | **1,502,634 ops/sec** | $\pm 61,043$ | **~665.5 ns** | 4 concurrent threads contending on the same symbol's `ReentrantLock`. |
| **`benchmarkPartialMatching`** | **1,205,187 ops/sec** | $\pm 29,881$ | **~829.7 ns** | 1 large incoming order sweeping multiple resting orders (partial fills). |

---

## 3. Batch Workload Benchmarks (`MatchingEngineBatchBenchmark.java`)

To simulate exchange burst conditions where bursts of orders arrive in bulk queues, batch benchmarks measure the time required to ingest and match contiguous batches of 1,000, 10,000, and 50,000 orders:

### 3.1 Batch Order Ingestion (Resting Book Growth)
| Batch Size | Benchmark Throughput | Effective Order Ingestion Rate | Time per Batch |
| :--- | :--- | :--- | :--- |
| **1,000 Orders** | **3,556.76 ops/sec** | **3,556,760 orders/sec** | ~0.281 ms |
| **10,000 Orders** | **340.85 ops/sec** | **3,408,500 orders/sec** | ~2.934 ms |
| **50,000 Orders** | **56.54 ops/sec** | **2,827,000 orders/sec** | ~17.687 ms |

### 3.2 Batch Continuous Matching (Execution Bursts)
| Batch Size | Benchmark Throughput | Effective Order Matching Rate | Time per Batch |
| :--- | :--- | :--- | :--- |
| **1,000 Orders** | **5,605.12 ops/sec** | **5,605,120 orders/sec** | ~0.178 ms |
| **10,000 Orders** | **457.19 ops/sec** | **4,571,900 orders/sec** | ~2.187 ms |
| **50,000 Orders** | **97.12 ops/sec** | **4,856,000 orders/sec** | ~10.297 ms |

---

## 4. Benchmark Methodology & Test Harness

- **Harness Framework**: Java Microbenchmark Harness (JMH) version `1.37`
- **JMH Mode**: `Throughput` (`ops/sec`)
- **Warm-up**: 3 iterations, 2.0 seconds per iteration (ensures complete C2 JIT compilation)
- **Measurement**: 5 iterations, 3.0 seconds per iteration
- **JVM Execution**: 1 Fork, OpenJDK 64-Bit Server VM (Java 21)
- **Garbage Collection**: ZGC / G1 ergonomics default for Java 21
- **Hardware Profile**: AMD Ryzen / modern x86_64 multi-core processor running Windows 11

---

## 5. Engineering Disclaimers & Scope of Measurements

To preserve scientific rigor, the following operational scope boundaries apply:

> [!IMPORTANT]
> 1. **Core In-Memory Scope**: These benchmarks measure the raw throughput of the algorithmic core (`OrderBook`, `MatchingEngine`, `OrderBookManager`).
> 2. **Exclusion of Relational I/O**: PostgreSQL database writes and network socket latency are **intentionally excluded** from these microbenchmarks. A full end-to-end HTTP request involving JSON serialization, Spring MVC dispatching, and synchronous JDBC writes will exhibit lower throughput (~5,000–15,000 req/sec) dictated by network and database I/O.
> 3. **Hardware & Operating System Variance**: Microbenchmark numbers depend on CPU core clock, L1/L2/L3 cache architectures, RAM bus speeds, and OS thread scheduler behavior. Results will vary across different machines.

---

## 6. How to Reproduce Benchmarks Locally

Both microbenchmarks and batch benchmarks are executable directly via Maven without requiring a database or Docker container:

### Compile Benchmark Test Classes
```bash
mvn clean test-compile
```

### Run Microbenchmark Suite (Individual Operations)
```bash
mvn exec:java -Dexec.classpathScope=test -Dexec.mainClass=org.openjdk.jmh.Main -Dexec.args="MatchingEngineBenchmark"
```

### Run Batch Workload Suite (Burst Operations)
```bash
mvn exec:java -Dexec.classpathScope=test -Dexec.mainClass=org.openjdk.jmh.Main -Dexec.args="MatchingEngineBatchBenchmark"
```

### Run a Single Specific Benchmark
```bash
mvn exec:java -Dexec.classpathScope=test -Dexec.mainClass=org.openjdk.jmh.Main -Dexec.args="benchmarkMatchingOrders"
```
