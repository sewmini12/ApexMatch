package com.apexmatch.stress;

import com.apexmatch.engine.MatchingEngine;
import com.apexmatch.engine.OrderBook;
import com.apexmatch.engine.OrderBookManager;
import com.apexmatch.model.Order;
import com.apexmatch.model.OrderSide;
import com.apexmatch.model.OrderType;
import com.apexmatch.model.Trade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 12 — Load & Stress Testing Suite
 *
 * Validates correctness, thread-safety, volume conservation, and data invariants
 * of the core MatchingEngine and OrderBookManager under heavy concurrent execution.
 */
public class MatchingEngineStressTest {

    private OrderBookManager orderBookManager;
    private MatchingEngine engine;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        orderBookManager = new OrderBookManager();
        engine = new MatchingEngine(orderBookManager);
    }

    @AfterEach
    void tearDown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }

    // =========================================================================
    // 1. High-Volume Single-Symbol Workload (Thousands of Concurrent Orders)
    // =========================================================================
    @Test
    @DisplayName("Stress 1: High-volume single-symbol concurrent BUY/SELL orders with volume conservation")
    void stressHighVolumeSingleSymbolWorkload() throws Exception {
        int threadCount = 8;
        int ordersPerThread = 250; // Total 2,000 orders (1,000 BUY, 1,000 SELL)
        executor = Executors.newFixedThreadPool(threadCount);

        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<List<Trade>>> futures = new ArrayList<>();
        AtomicLong seqGenerator = new AtomicLong(1);

        AtomicLong totalSubmittedBuyQty = new AtomicLong(0);
        AtomicLong totalSubmittedSellQty = new AtomicLong(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                startLatch.await();
                List<Trade> localTrades = new ArrayList<>();

                for (int i = 0; i < ordersPerThread; i++) {
                    boolean isBuy = (i % 2 == 0);
                    OrderSide side = isBuy ? OrderSide.BUY : OrderSide.SELL;
                    long qty = 10 + (i % 5) * 5; // 10, 15, 20, 25, 30 shares
                    BigDecimal price = new BigDecimal(145 + (i % 11)); // Prices between $145.00 and $155.00

                    if (isBuy) {
                        totalSubmittedBuyQty.addAndGet(qty);
                    } else {
                        totalSubmittedSellQty.addAndGet(qty);
                    }

                    Order order = new Order(
                            "ORD-" + threadId + "-" + i,
                            "TRADER-" + threadId,
                            "AAPL",
                            side,
                            OrderType.LIMIT,
                            price,
                            qty,
                            seqGenerator.getAndIncrement()
                    );

                    localTrades.addAll(engine.submitOrder(order));
                }
                return localTrades;
            }));
        }

        long startTime = System.currentTimeMillis();
        startLatch.countDown(); // Release all worker threads simultaneously

        List<Trade> allTrades = new ArrayList<>();
        for (Future<List<Trade>> future : futures) {
            allTrades.addAll(future.get(10, TimeUnit.SECONDS));
        }
        long durationMs = System.currentTimeMillis() - startTime;

        OrderBook book = engine.getOrderBook("AAPL");
        assertNotNull(book, "AAPL order book must exist");

        // Calculate trade volume
        long totalTradedQuantity = allTrades.stream().mapToLong(Trade::getQuantity).sum();
        long restingBuyQuantity = book.getTotalBuyQuantity();
        long restingSellQuantity = book.getTotalSellQuantity();

        // INVARIANT 1: Strict Conservation of Volume
        // Every submitted share must either be executed in a trade or resting in the book
        assertEquals(
                totalSubmittedBuyQty.get(),
                totalTradedQuantity + restingBuyQuantity,
                "BUY Volume conservation violated: Submitted BUY != Traded + Resting BUY"
        );
        assertEquals(
                totalSubmittedSellQty.get(),
                totalTradedQuantity + restingSellQuantity,
                "SELL Volume conservation violated: Submitted SELL != Traded + Resting SELL"
        );

        // INVARIANT 2: No Negative or Zero Quantities
        assertTrue(totalTradedQuantity > 0, "At least some trades must have executed");
        for (Trade trade : allTrades) {
            assertTrue(trade.getQuantity() > 0, "Trade quantity must be strictly positive: " + trade.getQuantity());
            assertTrue(trade.getPrice().compareTo(BigDecimal.ZERO) > 0, "Trade price must be strictly positive: " + trade.getPrice());
            assertEquals("AAPL", trade.getSymbol(), "Trade symbol must match AAPL");
        }

        // INVARIANT 3: Unique Trade IDs
        Set<String> uniqueIds = new HashSet<>();
        for (Trade trade : allTrades) {
            assertTrue(uniqueIds.add(trade.getTradeId()), "Duplicate trade ID detected: " + trade.getTradeId());
        }

        // INVARIANT 4: Lock Safety
        assertFalse(orderBookManager.getLockForSymbol("AAPL").isLocked(), "Symbol lock must be released after stress run");
        assertTrue(durationMs < 5000, "Stress test must complete rapidly (took " + durationMs + "ms)");
    }

    // =========================================================================
    // 2. High-Volume Multi-Symbol Partitioned Workload (16 Threads, 5 Symbols)
    // =========================================================================
    @Test
    @DisplayName("Stress 2: High-volume multi-symbol concurrent execution with strict symbol isolation")
    void stressHighVolumeMultiSymbolWorkload() throws Exception {
        int threadCount = 16;
        int ordersPerThread = 150; // Total 2,400 orders across 5 symbols
        executor = Executors.newFixedThreadPool(threadCount);

        String[] symbols = {"AAPL", "GOOG", "TSLA", "MSFT", "AMZN"};
        Map<String, BigDecimal> basePrices = Map.of(
                "AAPL", new BigDecimal("150.00"),
                "GOOG", new BigDecimal("2800.00"),
                "TSLA", new BigDecimal("200.00"),
                "MSFT", new BigDecimal("400.00"),
                "AMZN", new BigDecimal("180.00")
        );

        Map<String, AtomicLong> submittedBuyBySymbol = new ConcurrentHashMap<>();
        Map<String, AtomicLong> submittedSellBySymbol = new ConcurrentHashMap<>();
        for (String sym : symbols) {
            submittedBuyBySymbol.put(sym, new AtomicLong(0));
            submittedSellBySymbol.put(sym, new AtomicLong(0));
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<List<Trade>>> futures = new ArrayList<>();
        AtomicLong seqGenerator = new AtomicLong(1);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                startLatch.await();
                List<Trade> localTrades = new ArrayList<>();

                for (int i = 0; i < ordersPerThread; i++) {
                    String symbol = symbols[(threadId + i) % symbols.length];
                    boolean isBuy = (i % 2 == 0);
                    OrderSide side = isBuy ? OrderSide.BUY : OrderSide.SELL;
                    long qty = 10 + (i % 4) * 10; // 10, 20, 30, 40

                    BigDecimal basePrice = basePrices.get(symbol);
                    BigDecimal priceOffset = new BigDecimal((i % 5) - 2); // -2 to +2
                    BigDecimal price = basePrice.add(priceOffset);

                    if (isBuy) {
                        submittedBuyBySymbol.get(symbol).addAndGet(qty);
                    } else {
                        submittedSellBySymbol.get(symbol).addAndGet(qty);
                    }

                    Order order = new Order(
                            symbol + "-T" + threadId + "-" + i,
                            "USER-" + threadId,
                            symbol,
                            side,
                            OrderType.LIMIT,
                            price,
                            qty,
                            seqGenerator.getAndIncrement()
                    );

                    localTrades.addAll(engine.submitOrder(order));
                }
                return localTrades;
            }));
        }

        startLatch.countDown();

        List<Trade> allTrades = new ArrayList<>();
        for (Future<List<Trade>> future : futures) {
            allTrades.addAll(future.get(10, TimeUnit.SECONDS));
        }

        // Group trades by symbol
        Map<String, List<Trade>> tradesBySymbol = new HashMap<>();
        for (String sym : symbols) {
            tradesBySymbol.put(sym, new ArrayList<>());
        }
        for (Trade trade : allTrades) {
            assertNotNull(trade.getSymbol(), "Trade symbol must not be null");
            assertTrue(tradesBySymbol.containsKey(trade.getSymbol()), "Unknown symbol in trade: " + trade.getSymbol());
            tradesBySymbol.get(trade.getSymbol()).add(trade);
        }

        // INVARIANT 1: Strict Symbol Isolation & Independent Volume Conservation
        for (String sym : symbols) {
            OrderBook book = orderBookManager.getOrderBook(sym);
            assertNotNull(book, "Order book for " + sym + " must exist");

            long symbolTradedQty = tradesBySymbol.get(sym).stream().mapToLong(Trade::getQuantity).sum();
            long restingBuyQty = book.getTotalBuyQuantity();
            long restingSellQty = book.getTotalSellQuantity();

            assertEquals(
                    submittedBuyBySymbol.get(sym).get(),
                    symbolTradedQty + restingBuyQty,
                    "Volume conservation violated on BUY side for " + sym
            );
            assertEquals(
                    submittedSellBySymbol.get(sym).get(),
                    symbolTradedQty + restingSellQty,
                    "Volume conservation violated on SELL side for " + sym
            );

            // Verify prices are within valid range for that specific symbol
            BigDecimal base = basePrices.get(sym);
            for (Trade t : tradesBySymbol.get(sym)) {
                assertEquals(sym, t.getSymbol());
                assertTrue(
                        t.getPrice().subtract(base).abs().compareTo(new BigDecimal("10.00")) <= 0,
                        "Trade price " + t.getPrice() + " out of expected range for symbol " + sym
                );
            }

            assertFalse(orderBookManager.getLockForSymbol(sym).isLocked(), "Lock for " + sym + " must be free");
        }
    }

    // =========================================================================
    // 3. Concurrent Symmetric Matching (Simultaneous Matching Pairs)
    // =========================================================================
    @Test
    @DisplayName("Stress 3: Concurrent symmetric matching pairs execute fully leaving empty book")
    void stressConcurrentSymmetricMatching() throws Exception {
        int threadCount = 8;
        int pairsPerThread = 100; // 800 BUY and 800 SELL orders
        executor = Executors.newFixedThreadPool(threadCount);

        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<List<Trade>>> futures = new ArrayList<>();
        AtomicLong seq = new AtomicLong(1);
        BigDecimal matchPrice = new BigDecimal("100.00");
        long sharesPerOrder = 25;

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                startLatch.await();
                List<Trade> localTrades = new ArrayList<>();

                for (int i = 0; i < pairsPerThread; i++) {
                    Order buy = new Order(
                            "BUY-" + threadId + "-" + i,
                            "BUYER-" + threadId,
                            "TSLA",
                            OrderSide.BUY,
                            OrderType.LIMIT,
                            matchPrice,
                            sharesPerOrder,
                            seq.getAndIncrement()
                    );

                    Order sell = new Order(
                            "SELL-" + threadId + "-" + i,
                            "SELLER-" + threadId,
                            "TSLA",
                            OrderSide.SELL,
                            OrderType.LIMIT,
                            matchPrice,
                            sharesPerOrder,
                            seq.getAndIncrement()
                    );

                    localTrades.addAll(engine.submitOrder(buy));
                    localTrades.addAll(engine.submitOrder(sell));
                }
                return localTrades;
            }));
        }

        startLatch.countDown();

        List<Trade> allTrades = new ArrayList<>();
        for (Future<List<Trade>> f : futures) {
            allTrades.addAll(f.get(10, TimeUnit.SECONDS));
        }

        OrderBook book = engine.getOrderBook("TSLA");
        assertNotNull(book);

        long totalExecutedShares = allTrades.stream().mapToLong(Trade::getQuantity).sum();
        long expectedTotalShares = (long) threadCount * pairsPerThread * sharesPerOrder;

        // INVARIANT 1: Total executed shares must equal exactly 100% of demand
        assertEquals(expectedTotalShares, totalExecutedShares, "All symmetric pairs must fully execute");

        // INVARIANT 2: Book must be completely empty
        assertTrue(book.isEmpty(), "Order book must be completely empty after balanced execution");
        assertEquals(0, book.getBuyOrderCount());
        assertEquals(0, book.getSellOrderCount());
        assertEquals(0, book.getTotalBuyQuantity());
        assertEquals(0, book.getTotalSellQuantity());

        // INVARIANT 3: Every trade executes at exact price
        for (Trade trade : allTrades) {
            assertEquals(0, matchPrice.compareTo(trade.getPrice()), "Trade price must match $100.00");
            assertTrue(trade.getQuantity() > 0, "Trade quantity must be positive");
        }
    }

    // =========================================================================
    // 4. Concurrent Partial Matching with Irregular Quantities
    // =========================================================================
    @Test
    @DisplayName("Stress 4: Concurrent partial matching with irregular quantities preserves non-negativity")
    void stressConcurrentPartialMatchingIrregularQuantities() throws Exception {
        int threadCount = 8;
        int ordersPerThread = 100;
        executor = Executors.newFixedThreadPool(threadCount);

        long[] irregularSizes = {7, 13, 19, 23, 29, 31, 37, 41, 43, 47};
        BigDecimal matchPrice = new BigDecimal("250.00");

        AtomicLong totalBuyShares = new AtomicLong(0);
        AtomicLong totalSellShares = new AtomicLong(0);
        AtomicLong seq = new AtomicLong(1);

        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<List<Trade>>> futures = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                startLatch.await();
                List<Trade> localTrades = new ArrayList<>();

                for (int i = 0; i < ordersPerThread; i++) {
                    boolean isBuy = (i % 2 == 0);
                    long qty = irregularSizes[(threadId + i) % irregularSizes.length];

                    if (isBuy) {
                        totalBuyShares.addAndGet(qty);
                    } else {
                        totalSellShares.addAndGet(qty);
                    }

                    Order order = new Order(
                            "IRR-" + threadId + "-" + i,
                            "TRADER-" + threadId,
                            "GOOG",
                            isBuy ? OrderSide.BUY : OrderSide.SELL,
                            OrderType.LIMIT,
                            matchPrice,
                            qty,
                            seq.getAndIncrement()
                    );

                    localTrades.addAll(engine.submitOrder(order));
                }
                return localTrades;
            }));
        }

        startLatch.countDown();

        List<Trade> allTrades = new ArrayList<>();
        for (Future<List<Trade>> f : futures) {
            allTrades.addAll(f.get(10, TimeUnit.SECONDS));
        }

        OrderBook book = engine.getOrderBook("GOOG");
        assertNotNull(book);

        long totalTradedQuantity = allTrades.stream().mapToLong(Trade::getQuantity).sum();
        long restingBuy = book.getTotalBuyQuantity();
        long restingSell = book.getTotalSellQuantity();

        // INVARIANT 1: Conservation of Volume with Irregular Slices
        assertEquals(totalBuyShares.get(), totalTradedQuantity + restingBuy, "BUY volume must be conserved");
        assertEquals(totalSellShares.get(), totalTradedQuantity + restingSell, "SELL volume must be conserved");

        // INVARIANT 2: No Negative Quantities
        assertTrue(restingBuy >= 0, "Resting BUY quantity cannot be negative");
        assertTrue(restingSell >= 0, "Resting SELL quantity cannot be negative");

        // One side of the book must be empty when prices are identical
        assertTrue(restingBuy == 0 || restingSell == 0, "Opposite book sides cannot both rest at identical price");

        for (Trade trade : allTrades) {
            assertTrue(trade.getQuantity() > 0, "Trade quantity must be positive");
            assertTrue(trade.getQuantity() <= 47, "Trade quantity cannot exceed maximum single order size");
        }
    }

    // =========================================================================
    // 5. Scalability Across Thread Counts (4, 8, 16 Threads)
    // =========================================================================
    @ParameterizedTest(name = "Stress 5: Verify correctness across {0} concurrent threads")
    @ValueSource(ints = {4, 8, 16})
    @DisplayName("Stress 5: Invariant verification across varying thread counts (4, 8, 16)")
    void stressVaryingThreadCounts(int threadCount) throws Exception {
        executor = Executors.newFixedThreadPool(threadCount);
        int ordersPerThread = 100;
        String symbol = "MSFT";
        BigDecimal price = new BigDecimal("350.00");

        AtomicLong totalBuy = new AtomicLong(0);
        AtomicLong totalSell = new AtomicLong(0);
        AtomicLong seq = new AtomicLong(1);

        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<List<Trade>>> futures = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                startLatch.await();
                List<Trade> localTrades = new ArrayList<>();

                for (int i = 0; i < ordersPerThread; i++) {
                    boolean isBuy = (i % 2 == 0);
                    long qty = 20;

                    if (isBuy) {
                        totalBuy.addAndGet(qty);
                    } else {
                        totalSell.addAndGet(qty);
                    }

                    Order order = new Order(
                            "SC-" + threadCount + "-T" + threadId + "-" + i,
                            "USER-" + threadId,
                            symbol,
                            isBuy ? OrderSide.BUY : OrderSide.SELL,
                            OrderType.LIMIT,
                            price,
                            qty,
                            seq.getAndIncrement()
                    );

                    localTrades.addAll(engine.submitOrder(order));
                }
                return localTrades;
            }));
        }

        startLatch.countDown();

        List<Trade> allTrades = new ArrayList<>();
        for (Future<List<Trade>> f : futures) {
            allTrades.addAll(f.get(10, TimeUnit.SECONDS));
        }

        OrderBook book = engine.getOrderBook(symbol);
        assertNotNull(book);

        long totalTraded = allTrades.stream().mapToLong(Trade::getQuantity).sum();
        long restingBuy = book.getTotalBuyQuantity();
        long restingSell = book.getTotalSellQuantity();

        // Verify volume conservation under every thread pool size
        assertEquals(totalBuy.get(), totalTraded + restingBuy, "Volume conservation on BUY side failed for " + threadCount + " threads");
        assertEquals(totalSell.get(), totalTraded + restingSell, "Volume conservation on SELL side failed for " + threadCount + " threads");

        // Verify lock is cleanly released
        assertFalse(orderBookManager.getLockForSymbol(symbol).isLocked(), "Lock must be released for " + threadCount + " threads");
    }
}
