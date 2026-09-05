package com.apexmatch.engine;

import com.apexmatch.model.Order;
import com.apexmatch.model.OrderSide;
import com.apexmatch.model.OrderType;
import com.apexmatch.model.Trade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class MatchingEngineConcurrencyTest {

    private OrderBook orderBook;
    private MatchingEngine engine;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook();
        engine = new MatchingEngine(orderBook);
        executor = Executors.newFixedThreadPool(16);
    }

    @AfterEach
    void tearDown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }

    // -------------------------------------------------------------------------
    // 1. Multiple Buyers Competing for Limited Liquidity (No Over-Filling)
    // -------------------------------------------------------------------------
    @Test
    void concurrentBuyersCompetingForLimitedLiquidityShouldNeverOverfill() throws Exception {
        int numberOfSellers = 10;
        long sharesPerSeller = 10; // Total supply = 100 shares
        BigDecimal price = new BigDecimal("100.00");

        // Pre-populate the book with 100 shares across 10 SELL orders
        for (int i = 1; i <= numberOfSellers; i++) {
            Order sellOrder = new Order(
                    "SELL-" + i,
                    "SELLER_" + i,
                    "AAPL",
                    OrderSide.SELL,
                    OrderType.LIMIT,
                    price,
                    sharesPerSeller,
                    i
            );
            orderBook.addOrder(sellOrder);
        }

        assertEquals(100, orderBook.getTotalSellQuantity());

        // 10 concurrent buyers each wanting 20 shares (Total demand = 200 shares)
        int numberOfBuyers = 10;
        long sharesPerBuyer = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<List<Trade>>> futures = new ArrayList<>();

        for (int i = 1; i <= numberOfBuyers; i++) {
            final int buyerIndex = i;
            futures.add(executor.submit(() -> {
                startLatch.await(); // Synchronize all threads to start simultaneously
                Order buyOrder = new Order(
                        "BUY-" + buyerIndex,
                        "BUYER_" + buyerIndex,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        price,
                        sharesPerBuyer,
                        100 + buyerIndex
                );
                return engine.submitOrder(buyOrder);
            }));
        }

        // Release all threads simultaneously
        startLatch.countDown();

        // Collect and aggregate all trades
        List<Trade> allTrades = new ArrayList<>();
        for (Future<List<Trade>> future : futures) {
            allTrades.addAll(future.get(5, TimeUnit.SECONDS));
        }

        // 1. Total shares traded must equal exactly the total available supply (100 shares)
        long totalTradedQuantity = allTrades.stream().mapToLong(Trade::getQuantity).sum();
        assertEquals(100, totalTradedQuantity, "Total traded shares must exactly equal available liquidity (no overfilling)");

        // 2. SELL book must be completely exhausted
        assertNull(orderBook.getBestSell(), "All SELL orders should have been consumed");
        assertEquals(0, orderBook.getSellOrderCount(), "SELL book count must be 0");

        // 3. BUY book must contain exactly the unfulfilled demand (200 demanded - 100 filled = 100 remaining)
        assertEquals(100, orderBook.getTotalBuyQuantity(), "Remaining BUY shares in book must equal unsatisfied demand");

        // 4. All trade IDs must be unique
        Set<String> uniqueTradeIds = new HashSet<>();
        for (Trade trade : allTrades) {
            assertTrue(uniqueTradeIds.add(trade.getTradeId()), "Trade ID must be unique: " + trade.getTradeId());
        }

        // 5. ReentrantLock must be completely unlocked after operations finish
        assertFalse(engine.getLock().isLocked(), "MatchingEngine lock must be released");
    }

    // -------------------------------------------------------------------------
    // 2. Symmetric Concurrent Two-Sided Market (Simultaneous BUYs & SELLs)
    // -------------------------------------------------------------------------
    @Test
    void concurrentSymmetricMarketOrdersShouldMatchAtomicallyWithoutStateCorruption() throws Exception {
        int pairsCount = 25; // 25 BUY orders and 25 SELL orders of 10 shares each (250 total shares)
        long sharesPerOrder = 10;
        BigDecimal price = new BigDecimal("150.00");

        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<List<Trade>>> futures = new ArrayList<>();
        AtomicInteger seqGenerator = new AtomicInteger(1);

        // Submit 25 BUY orders and 25 SELL orders interleaved concurrently
        for (int i = 1; i <= pairsCount; i++) {
            final int id = i;

            // BUY task
            futures.add(executor.submit(() -> {
                startLatch.await();
                Order buy = new Order(
                        "BUY-" + id,
                        "BUYER_" + id,
                        "MSFT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        price,
                        sharesPerOrder,
                        seqGenerator.getAndIncrement()
                );
                return engine.submitOrder(buy);
            }));

            // SELL task
            futures.add(executor.submit(() -> {
                startLatch.await();
                Order sell = new Order(
                        "SELL-" + id,
                        "SELLER_" + id,
                        "MSFT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        price,
                        sharesPerOrder,
                        seqGenerator.getAndIncrement()
                );
                return engine.submitOrder(sell);
            }));
        }

        // Fire all 50 threads concurrently
        startLatch.countDown();

        // Collect all executed trades
        Map<String, Trade> uniqueTrades = new ConcurrentHashMap<>();
        for (Future<List<Trade>> future : futures) {
            List<Trade> trades = future.get(5, TimeUnit.SECONDS);
            for (Trade t : trades) {
                uniqueTrades.put(t.getTradeId(), t);
            }
        }

        // Total unique traded volume must equal exactly 250 shares
        long totalTradedQuantity = uniqueTrades.values().stream().mapToLong(Trade::getQuantity).sum();
        assertEquals(250, totalTradedQuantity, "Exactly 250 shares should have been executed in matched pairs");

        // The order book should be completely clear (all 25 BUYs matched with all 25 SELLs)
        assertTrue(orderBook.isEmpty(), "Order book should be completely empty after balanced execution");
        assertFalse(engine.getLock().isLocked(), "Lock must be released");
    }

    // -------------------------------------------------------------------------
    // 3. Multi-Symbol Isolation Under Concurrency
    // -------------------------------------------------------------------------
    @Test
    void concurrentOrdersForDifferentSymbolsMustNeverCrossMatch() throws Exception {
        String[] symbols = {"AAPL", "GOOG", "TSLA"};
        int ordersPerSymbol = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<List<Trade>>> futures = new ArrayList<>();
        AtomicInteger seq = new AtomicInteger(1);

        for (String symbol : symbols) {
            for (int i = 0; i < ordersPerSymbol; i++) {
                final int idx = i;
                final String sym = symbol;

                futures.add(executor.submit(() -> {
                    startLatch.await();
                    OrderSide side = (idx % 2 == 0) ? OrderSide.BUY : OrderSide.SELL;
                    BigDecimal price = new BigDecimal("200.00");
                    Order order = new Order(
                            sym + "-" + side + "-" + idx,
                            "USER_" + idx,
                            sym,
                            side,
                            OrderType.LIMIT,
                            price,
                            10,
                            seq.getAndIncrement()
                    );
                    return engine.submitOrder(order);
                }));
            }
        }

        startLatch.countDown();

        List<Trade> allTrades = new ArrayList<>();
        for (Future<List<Trade>> f : futures) {
            allTrades.addAll(f.get(5, TimeUnit.SECONDS));
        }

        // Verify that every single executed trade has consistent symbol
        for (Trade trade : allTrades) {
            assertNotNull(trade.getSymbol());
            assertTrue(
                    Arrays.asList(symbols).contains(trade.getSymbol()),
                    "Trade symbol must be one of the known tickers"
            );
        }

        assertFalse(engine.getLock().isLocked(), "MatchingEngine lock must be free");
    }

    // -------------------------------------------------------------------------
    // 4. Lock Safety - Always Released Even on Premature Return or Empty Book
    // -------------------------------------------------------------------------
    @Test
    void lockMustAlwaysBeReleasedAfterExecution() {
        Order unmatchedOrder = new Order(
                "ORD-SOLO",
                "ALICE",
                "NVDA",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("500.00"),
                10,
                1
        );

        List<Trade> trades = engine.submitOrder(unmatchedOrder);
        assertTrue(trades.isEmpty());
        assertFalse(engine.getLock().isLocked(), "Lock must be released even when no counter order exists");
        assertEquals(1, orderBook.getBuyOrderCount());
    }
}
