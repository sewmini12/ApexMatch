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

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(0)
@State(Scope.Benchmark)
public class MatchingEngineBatchBenchmark {

    @Param({"1000", "10000", "50000"})
    public int orderCount;

    private OrderBookManager orderBookManager;
    private MatchingEngine matchingEngine;

    private Order[] nonMatchingOrders;
    private Order[] batchBuyOrders;
    private Order[] batchSellOrders;

    @Setup(Level.Trial)
    public void setupTrial() {
        orderBookManager = new OrderBookManager();
        matchingEngine = new MatchingEngine(orderBookManager);

        nonMatchingOrders = new Order[50_000];
        batchBuyOrders = new Order[50_000];
        batchSellOrders = new Order[50_000];

        BigDecimal basePrice = new BigDecimal("150.00");

        for (int i = 0; i < 50_000; i++) {
            nonMatchingOrders[i] = new Order(
                    "BATCH-INS-" + i,
                    "USER-" + (i % 100),
                    "AAPL",
                    OrderSide.BUY,
                    OrderType.LIMIT,
                    basePrice.subtract(new BigDecimal(i % 50)),
                    100,
                    i + 1
            );

            batchBuyOrders[i] = new Order(
                    "BATCH-BUY-" + i,
                    "BUYER-" + (i % 100),
                    "AAPL",
                    OrderSide.BUY,
                    OrderType.LIMIT,
                    basePrice,
                    100,
                    (long) i * 2 + 1
            );

            batchSellOrders[i] = new Order(
                    "BATCH-SELL-" + i,
                    "SELLER-" + (i % 100),
                    "AAPL",
                    OrderSide.SELL,
                    OrderType.LIMIT,
                    basePrice,
                    100,
                    (long) i * 2 + 2
            );
        }
    }

    @Setup(Level.Invocation)
    public void setupInvocation() {
        orderBookManager.clear();
    }

    @Benchmark
    public void benchmarkBatchInsertion(Blackhole bh) {
        for (int i = 0; i < orderCount; i++) {
            bh.consume(matchingEngine.submitOrder(nonMatchingOrders[i]));
        }
    }

    @Benchmark
    public void benchmarkBatchMatching(Blackhole bh) {
        int half = orderCount / 2;
        for (int i = 0; i < half; i++) {
            bh.consume(matchingEngine.submitOrder(batchBuyOrders[i]));
            bh.consume(matchingEngine.submitOrder(batchSellOrders[i]));
        }
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(MatchingEngineBatchBenchmark.class.getSimpleName())
                .forks(0)
                .warmupIterations(2)
                .measurementIterations(3)
                .build();

        new Runner(opt).run();
    }
}
