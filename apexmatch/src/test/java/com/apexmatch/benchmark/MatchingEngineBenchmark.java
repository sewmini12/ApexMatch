package com.apexmatch.benchmark;

import com.apexmatch.engine.MatchingEngine;
import com.apexmatch.engine.OrderBookManager;
import com.apexmatch.model.Order;
import com.apexmatch.model.OrderSide;
import com.apexmatch.model.OrderType;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(0)
@State(Scope.Benchmark)
public class MatchingEngineBenchmark {

    private static final String[] SYMBOLS = {"AAPL", "GOOG", "TSLA", "MSFT", "AMZN"};
    private static final int POOL_SIZE = 100_000;

    private OrderBookManager orderBookManager;
    private MatchingEngine matchingEngine;

    private Order[] nonMatchingOrders;
    private Order[] matchingBuyOrders;
    private Order[] matchingSellOrders;
    private Order[] multiSymbolOrders;
    private Order[] sweepSellOrders;
    private Order[] sweepBuyOrders;

    private final AtomicLong seqCounter = new AtomicLong(1);

    @State(Scope.Thread)
    public static class ThreadState {
        int index = 0;
    }

    @Setup(Level.Trial)
    public void setupTrial() {
        orderBookManager = new OrderBookManager();
        matchingEngine = new MatchingEngine(orderBookManager);

        nonMatchingOrders = new Order[POOL_SIZE];
        matchingBuyOrders = new Order[POOL_SIZE];
        matchingSellOrders = new Order[POOL_SIZE];
        multiSymbolOrders = new Order[POOL_SIZE];
        sweepSellOrders = new Order[POOL_SIZE];
        sweepBuyOrders = new Order[POOL_SIZE / 5];

        BigDecimal basePrice = new BigDecimal("150.00");

        for (int i = 0; i < POOL_SIZE; i++) {
            // Scenario 1: Non-matching BUY orders entering the book at descending prices
            BigDecimal insertPrice = basePrice.subtract(new BigDecimal(i % 50));
            nonMatchingOrders[i] = new Order(
                    "INS-" + i,
                    "USER-" + (i % 100),
                    "AAPL",
                    OrderSide.BUY,
                    OrderType.LIMIT,
                    insertPrice,
                    100,
                    i + 1
            );

            // Scenario 2: Continuous matching BUY & SELL pairs at equal price
            matchingBuyOrders[i] = new Order(
                    "M-BUY-" + i,
                    "BUYER-" + (i % 100),
                    "AAPL",
                    OrderSide.BUY,
                    OrderType.LIMIT,
                    basePrice,
                    100,
                    (long) i * 2 + 1
            );
            matchingSellOrders[i] = new Order(
                    "M-SELL-" + i,
                    "SELLER-" + (i % 100),
                    "AAPL",
                    OrderSide.SELL,
                    OrderType.LIMIT,
                    basePrice,
                    100,
                    (long) i * 2 + 2
            );

            // Scenario 4: Orders distributed across 5 symbols
            String symbol = SYMBOLS[i % SYMBOLS.length];
            multiSymbolOrders[i] = new Order(
                    "MS-" + i,
                    "USER-" + (i % 100),
                    symbol,
                    (i % 2 == 0) ? OrderSide.BUY : OrderSide.SELL,
                    OrderType.LIMIT,
                    basePrice.add(new BigDecimal((i % 10) - 5)),
                    50,
                    i + 1
            );

            // Scenario 3: Partial sweep orders (5 small sells of 20 shares, 1 buy of 100 shares)
            sweepSellOrders[i] = new Order(
                    "SWP-S-" + i,
                    "SELLER-" + (i % 50),
                    "AAPL",
                    OrderSide.SELL,
                    OrderType.LIMIT,
                    basePrice,
                    20,
                    i + 1
            );
        }

        for (int i = 0; i < sweepBuyOrders.length; i++) {
            sweepBuyOrders[i] = new Order(
                    "SWP-B-" + i,
                    "SWEEPER-" + i,
                    "AAPL",
                    OrderSide.BUY,
                    OrderType.LIMIT,
                    basePrice,
                    100,
                    (long) POOL_SIZE + i + 1
            );
        }
    }

    @Setup(Level.Iteration)
    public void setupIteration() {
        orderBookManager.clear();
    }

    // -------------------------------------------------------------------------
    // 1. Order Insertion / Non-Matching Orders
    // -------------------------------------------------------------------------
    @Benchmark
    public void benchmarkOrderInsertion(ThreadState state, Blackhole bh) {
        int idx = state.index++ % POOL_SIZE;
        Order order = nonMatchingOrders[idx];
        bh.consume(matchingEngine.submitOrder(order));
    }

    // -------------------------------------------------------------------------
    // 2. Matching Orders (Continuous Match)
    // -------------------------------------------------------------------------
    @Benchmark
    public void benchmarkMatchingOrders(ThreadState state, Blackhole bh) {
        int idx = state.index++ % POOL_SIZE;
        bh.consume(matchingEngine.submitOrder(matchingBuyOrders[idx]));
        bh.consume(matchingEngine.submitOrder(matchingSellOrders[idx]));
    }

    // -------------------------------------------------------------------------
    // 3. Partial Matching (1 order sweeping 5 resting orders)
    // -------------------------------------------------------------------------
    @Benchmark
    public void benchmarkPartialMatching(ThreadState state, Blackhole bh) {
        int baseIdx = (state.index * 5) % (POOL_SIZE - 5);
        int buyIdx = state.index % sweepBuyOrders.length;
        state.index++;

        for (int i = 0; i < 5; i++) {
            matchingEngine.submitOrder(sweepSellOrders[baseIdx + i]);
        }
        bh.consume(matchingEngine.submitOrder(sweepBuyOrders[buyIdx]));
    }

    // -------------------------------------------------------------------------
    // 4. Multi-Symbol Workload
    // -------------------------------------------------------------------------
    @Benchmark
    public void benchmarkMultiSymbolWorkload(ThreadState state, Blackhole bh) {
        int idx = state.index++ % POOL_SIZE;
        Order order = multiSymbolOrders[idx];
        bh.consume(matchingEngine.submitOrder(order));
    }

    // -------------------------------------------------------------------------
    // 5. Concurrent Workload (4 parallel threads exercising ReentrantLock)
    // -------------------------------------------------------------------------
    @Benchmark
    @Threads(4)
    public void benchmarkConcurrentWorkload(ThreadState state, Blackhole bh) {
        int idx = state.index++ % POOL_SIZE;
        Order order = multiSymbolOrders[idx];
        bh.consume(matchingEngine.submitOrder(order));
    }

    // -------------------------------------------------------------------------
    // Main Runner for Command Line Execution
    // -------------------------------------------------------------------------
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(MatchingEngineBenchmark.class.getSimpleName())
                .forks(0)
                .warmupIterations(2)
                .measurementIterations(3)
                .build();

        new Runner(opt).run();
    }
}
