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

public class MultiSymbolOrderBookTest {

    private OrderBookManager manager;
    private MatchingEngine engine;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        manager = new OrderBookManager();
        engine = new MatchingEngine(manager);
        executor = Executors.newFixedThreadPool(16);
    }

    @AfterEach
    void tearDown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }

    // -------------------------------------------------------------------------
    // Test 1 — Same symbol matches
    // SELL AAPL @ 145, BUY AAPL @ 150 -> trade created with symbol AAPL
    // -------------------------------------------------------------------------
    @Test
    void test1_sameSymbolShouldMatchSuccessfully() {
        Order sellAapl = new Order(
                "ORD-S1",
                "BOB",
                "AAPL",
                OrderSide.SELL,
                OrderType.LIMIT,
                new BigDecimal("145.00"),
                60,
                1
        );

        Order buyAapl = new Order(
                "ORD-B1",
                "ALICE",
                "AAPL",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("150.00"),
                100,
                2
        );

        List<Trade> sellTrades = engine.submitOrder(sellAapl);
        assertTrue(sellTrades.isEmpty(), "Resting SELL order should not trade immediately");

        List<Trade> buyTrades = engine.submitOrder(buyAapl);
        assertEquals(1, buyTrades.size(), "Matching BUY order must produce exactly 1 trade");

        Trade trade = buyTrades.get(0);
        assertEquals("AAPL", trade.getSymbol(), "Trade symbol must be AAPL");
        assertEquals("ALICE", trade.getBuyerId());
        assertEquals("BOB", trade.getSellerId());
        assertEquals(new BigDecimal("145.00"), trade.getPrice());
        assertEquals(60, trade.getQuantity());

        // Unfilled remainder of buy order rests on AAPL book
        OrderBook aaplBook = manager.getOrderBook("AAPL");
        assertNotNull(aaplBook);
        assertEquals(0, aaplBook.getSellOrderCount());
        assertEquals(1, aaplBook.getBuyOrderCount());
        assertEquals(40, aaplBook.getTotalBuyQuantity());
    }

    // -------------------------------------------------------------------------
    // Test 2 — Different symbols do not match
    // SELL AAPL @ 145, BUY GOOG @ 150 -> no trade, both remain in respective books
    // -------------------------------------------------------------------------
    @Test
    void test2_differentSymbolsMustNeverMatch() {
        Order sellAapl = new Order(
                "ORD-AAPL-1",
                "BOB",
                "AAPL",
                OrderSide.SELL,
                OrderType.LIMIT,
                new BigDecimal("145.00"),
                60,
                1
        );

        Order buyGoog = new Order(
                "ORD-GOOG-1",
                "ALICE",
                "GOOG",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("150.00"),
                100,
                2
        );

        List<Trade> aaplTrades = engine.submitOrder(sellAapl);
        List<Trade> googTrades = engine.submitOrder(buyGoog);

        assertTrue(aaplTrades.isEmpty(), "No trade should occur for AAPL SELL");
        assertTrue(googTrades.isEmpty(), "No trade should occur for GOOG BUY against AAPL SELL");

        // Both orders must remain safely in their independent books
        OrderBook aaplBook = manager.getOrderBook("AAPL");
        OrderBook googBook = manager.getOrderBook("GOOG");

        assertNotNull(aaplBook, "AAPL book must exist");
        assertNotNull(googBook, "GOOG book must exist");
        assertNotSame(aaplBook, googBook, "AAPL and GOOG must have distinct OrderBook instances");

        assertEquals(1, aaplBook.getSellOrderCount());
        assertEquals(60, aaplBook.getTotalSellQuantity());
        assertEquals(0, aaplBook.getBuyOrderCount());

        assertEquals(1, googBook.getBuyOrderCount());
        assertEquals(100, googBook.getTotalBuyQuantity());
        assertEquals(0, googBook.getSellOrderCount());
    }

    // -------------------------------------------------------------------------
    // Test 3 — Multiple symbols have independent order books
    // Create orders for AAPL, GOOG, TSLA -> verify each has independent order book
    // -------------------------------------------------------------------------
    @Test
    void test3_multipleSymbolsHaveIndependentOrderBooks() {
        Order aaplOrder = new Order("A1", "U1", "AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("150.00"), 50, 1);
        Order googOrder = new Order("G1", "U2", "GOOG", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("2800.00"), 30, 2);
        Order tslaOrder = new Order("T1", "U3", "TSLA", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("700.00"), 20, 3);

        engine.submitOrder(aaplOrder);
        engine.submitOrder(googOrder);
        engine.submitOrder(tslaOrder);

        assertEquals(3, manager.getActiveBookCount(), "Manager should have exactly 3 active books");
        assertTrue(manager.hasOrderBook("AAPL"));
        assertTrue(manager.hasOrderBook("GOOG"));
        assertTrue(manager.hasOrderBook("TSLA"));

        OrderBook aaplBook = manager.getOrderBook("AAPL");
        OrderBook googBook = manager.getOrderBook("GOOG");
        OrderBook tslaBook = manager.getOrderBook("TSLA");

        assertNotSame(aaplBook, googBook);
        assertNotSame(googBook, tslaBook);
        assertNotSame(aaplBook, tslaBook);

        assertEquals(50, aaplBook.getTotalBuyQuantity());
        assertEquals(0, aaplBook.getTotalSellQuantity());

        assertEquals(30, googBook.getTotalBuyQuantity());
        assertEquals(0, googBook.getTotalSellQuantity());

        assertEquals(0, tslaBook.getTotalBuyQuantity());
        assertEquals(20, tslaBook.getTotalSellQuantity());
    }

    // -------------------------------------------------------------------------
    // Test 4 — Price-Time Priority within one symbol
    // Priority behavior still works independently for each symbol
    // -------------------------------------------------------------------------
    @Test
    void test4_priceTimePriorityWorksIndependentlyPerSymbol() {
        // AAPL SELLs: Bob @ 145 (seq 1), Charlie @ 140 (seq 2 - better price), David @ 145 (seq 3 - tie with Bob)
        Order bobAapl = new Order("B1", "BOB", "AAPL", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("145.00"), 50, 1);
        Order charlieAapl = new Order("C1", "CHARLIE", "AAPL", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("140.00"), 50, 2);
        Order davidAapl = new Order("D1", "DAVID", "AAPL", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("145.00"), 50, 3);

        // GOOG SELLs: Emma @ 2000 (seq 4), Frank @ 1950 (seq 5 - better price)
        Order emmaGoog = new Order("E1", "EMMA", "GOOG", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("2000.00"), 10, 4);
        Order frankGoog = new Order("F1", "FRANK", "GOOG", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("1950.00"), 10, 5);

        engine.submitOrder(bobAapl);
        engine.submitOrder(charlieAapl);
        engine.submitOrder(davidAapl);
        engine.submitOrder(emmaGoog);
        engine.submitOrder(frankGoog);

        // Buyer for AAPL: Alice buys 80 AAPL @ 150.00
        // Expected execution: Charlie first (50 @ 140.00 - best price), then Bob (30 @ 145.00 - earliest time)
        Order aliceAapl = new Order("A1", "ALICE", "AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("150.00"), 80, 6);
        List<Trade> aaplTrades = engine.submitOrder(aliceAapl);

        assertEquals(2, aaplTrades.size());
        assertEquals("CHARLIE", aaplTrades.get(0).getSellerId());
        assertEquals(50, aaplTrades.get(0).getQuantity());
        assertEquals(new BigDecimal("140.00"), aaplTrades.get(0).getPrice());

        assertEquals("BOB", aaplTrades.get(1).getSellerId());
        assertEquals(30, aaplTrades.get(1).getQuantity());
        assertEquals(new BigDecimal("145.00"), aaplTrades.get(1).getPrice());

        // GOOG book must remain completely untouched
        OrderBook googBook = manager.getOrderBook("GOOG");
        assertEquals(20, googBook.getTotalSellQuantity());
        assertEquals("FRANK", googBook.getBestSell().getUserId(), "Frank still holds best sell price on GOOG");
    }

    // -------------------------------------------------------------------------
    // Test 5 — Partial fills within correct symbol
    // -------------------------------------------------------------------------
    @Test
    void test5_partialFillsExecuteWithinCorrectSymbol() {
        Order restingSell = new Order("S1", "SELLER", "TSLA", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("700.00"), 100, 1);
        engine.submitOrder(restingSell);

        // Buyer wants 40 TSLA -> 40 executed, 60 TSLA rests on SELL book
        Order partialBuy = new Order("B1", "BUYER", "TSLA", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("700.00"), 40, 2);
        List<Trade> trades = engine.submitOrder(partialBuy);

        assertEquals(1, trades.size());
        assertEquals(40, trades.get(0).getQuantity());
        assertEquals("TSLA", trades.get(0).getSymbol());

        OrderBook tslaBook = manager.getOrderBook("TSLA");
        assertEquals(60, tslaBook.getTotalSellQuantity(), "60 shares should remain on TSLA sell book");
        assertEquals(0, tslaBook.getTotalBuyQuantity());
    }

    // -------------------------------------------------------------------------
    // Test 6 — Concurrent multi-symbol orders
    // Submit concurrent orders across multiple symbols (AAPL, GOOG, TSLA, MSFT)
    // Verify:
    // * no overfilling
    // * no duplicate trades
    // * no cross-symbol matching
    // * correct remaining quantities
    // -------------------------------------------------------------------------
    @Test
    void test6_concurrentMultiSymbolOrdersShouldMatchSafelyWithoutCrossMatchingOrOverfilling() throws Exception {
        String[] symbols = {"AAPL", "GOOG", "TSLA", "MSFT"};
        int pairsPerSymbol = 20; // 20 BUY and 20 SELL orders per symbol (4 symbols * 40 orders = 160 total orders)
        long sharesPerOrder = 10;
        BigDecimal basePrice = new BigDecimal("100.00");

        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<List<Trade>>> futures = new ArrayList<>();
        AtomicInteger seqGenerator = new AtomicInteger(1);

        // Interleave concurrent order submissions across all 4 symbols
        for (int i = 1; i <= pairsPerSymbol; i++) {
            final int id = i;
            for (String symbol : symbols) {
                final String sym = symbol;

                // Submit BUY
                futures.add(executor.submit(() -> {
                    startLatch.await();
                    Order buy = new Order(
                            "BUY-" + sym + "-" + id,
                            "BUYER_" + sym + "_" + id,
                            sym,
                            OrderSide.BUY,
                            OrderType.LIMIT,
                            basePrice,
                            sharesPerOrder,
                            seqGenerator.getAndIncrement()
                    );
                    return engine.submitOrder(buy);
                }));

                // Submit SELL
                futures.add(executor.submit(() -> {
                    startLatch.await();
                    Order sell = new Order(
                            "SELL-" + sym + "-" + id,
                            "SELLER_" + sym + "_" + id,
                            sym,
                            OrderSide.SELL,
                            OrderType.LIMIT,
                            basePrice,
                            sharesPerOrder,
                            seqGenerator.getAndIncrement()
                    );
                    return engine.submitOrder(sell);
                }));
            }
        }

        // Release all 160 concurrent threads simultaneously
        startLatch.countDown();

        // Collect all executed trades
        List<Trade> allTrades = new ArrayList<>();
        Map<String, List<Trade>> tradesBySymbol = new HashMap<>();
        for (String sym : symbols) {
            tradesBySymbol.put(sym, new ArrayList<>());
        }

        Set<String> tradeIds = new HashSet<>();

        for (Future<List<Trade>> future : futures) {
            List<Trade> trades = future.get(10, TimeUnit.SECONDS);
            for (Trade trade : trades) {
                allTrades.add(trade);
                assertTrue(tradeIds.add(trade.getTradeId()), "Trade ID must be globally unique: " + trade.getTradeId());
                assertTrue(tradesBySymbol.containsKey(trade.getSymbol()), "Trade must belong to one of our test symbols");
                tradesBySymbol.get(trade.getSymbol()).add(trade);
            }
        }

        // Verification 1: Exactly 20 pairs * 10 shares = 200 shares executed per symbol (800 shares total)
        long totalTradedQuantity = allTrades.stream().mapToLong(Trade::getQuantity).sum();
        assertEquals(800, totalTradedQuantity, "Total traded quantity across all 4 symbols must be exactly 800 shares");

        for (String sym : symbols) {
            long symTraded = tradesBySymbol.get(sym).stream().mapToLong(Trade::getQuantity).sum();
            assertEquals(200, symTraded, "Each symbol should have traded exactly 200 shares");

            // Verification 2: Each trade must only have buyer and seller from the same symbol
            for (Trade t : tradesBySymbol.get(sym)) {
                assertEquals(sym, t.getSymbol());
                assertTrue(t.getBuyerId().contains(sym), "Buyer must belong to symbol " + sym);
                assertTrue(t.getSellerId().contains(sym), "Seller must belong to symbol " + sym);
            }

            // Verification 3: All books must be completely balanced and empty
            OrderBook book = manager.getOrderBook(sym);
            assertNotNull(book, "Book for " + sym + " must exist");
            assertTrue(book.isEmpty(), "Order book for " + sym + " should be empty after symmetric matching");
        }

        // Verification 4: Exactly 4 active books in manager
        assertEquals(4, manager.getActiveBookCount());
    }
}
