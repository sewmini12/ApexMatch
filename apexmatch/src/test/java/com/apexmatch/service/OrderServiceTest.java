package com.apexmatch.service;

import com.apexmatch.dto.OrderRequest;
import com.apexmatch.engine.MatchingEngine;
import com.apexmatch.engine.OrderBook;
import com.apexmatch.entity.TradeEntity;
import com.apexmatch.model.OrderSide;
import com.apexmatch.model.OrderType;
import com.apexmatch.model.Trade;
import com.apexmatch.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class OrderServiceTest {

    private OrderBook orderBook;
    private MatchingEngine matchingEngine;
    private TradeRepository tradeRepository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook();
        matchingEngine = new MatchingEngine(orderBook);
        tradeRepository = mock(TradeRepository.class);
        orderService = new OrderService(matchingEngine, tradeRepository);
    }

    @Test
    void bobAndAliceMatchingScenarioWithPersistence() {
        // Step 1: Bob submits LIMIT SELL 60 AAPL @ $145.00
        OrderRequest bobRequest = new OrderRequest();
        bobRequest.setUserId("BOB");
        bobRequest.setSymbol("AAPL");
        bobRequest.setSide(OrderSide.SELL);
        bobRequest.setType(OrderType.LIMIT);
        bobRequest.setPrice(new BigDecimal("145.00"));
        bobRequest.setQuantity(60);

        List<Trade> bobTrades = orderService.processOrder(bobRequest);

        assertTrue(bobTrades.isEmpty(), "Bob's order should rest in book with no immediate trades");
        verify(tradeRepository, never()).saveAll(anyList());

        // Step 2: Alice submits LIMIT BUY 100 AAPL @ $150.00
        OrderRequest aliceRequest = new OrderRequest();
        aliceRequest.setUserId("ALICE");
        aliceRequest.setSymbol("AAPL");
        aliceRequest.setSide(OrderSide.BUY);
        aliceRequest.setType(OrderType.LIMIT);
        aliceRequest.setPrice(new BigDecimal("150.00"));
        aliceRequest.setQuantity(100);

        List<Trade> aliceTrades = orderService.processOrder(aliceRequest);

        assertEquals(1, aliceTrades.size(), "Alice's order should generate 1 trade against Bob");
        Trade trade = aliceTrades.get(0);
        assertEquals("ALICE", trade.getBuyerId());
        assertEquals("BOB", trade.getSellerId());
        assertEquals("AAPL", trade.getSymbol());
        assertEquals(new BigDecimal("145.00"), trade.getPrice());
        assertEquals(60, trade.getQuantity());

        // Verify trade persistence
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TradeEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(tradeRepository, times(1)).saveAll(captor.capture());

        List<TradeEntity> savedEntities = captor.getValue();
        assertEquals(1, savedEntities.size());
        TradeEntity savedTrade = savedEntities.get(0);
        assertEquals(trade.getTradeId(), savedTrade.getTradeId());
        assertEquals("AAPL", savedTrade.getSymbol());
        assertEquals("ALICE", savedTrade.getBuyer());
        assertEquals("BOB", savedTrade.getSeller());
        assertEquals(new BigDecimal("145.00"), savedTrade.getPrice());
        assertEquals(60, savedTrade.getQuantity());
        assertNotNull(savedTrade.getExecutedAt());
    }

    @Test
    void invalidOrderShouldThrowExceptionAndNotPersist() {
        OrderRequest invalidRequest = new OrderRequest();
        invalidRequest.setUserId("ALICE");
        invalidRequest.setSymbol("AAPL");
        invalidRequest.setSide(OrderSide.BUY);
        invalidRequest.setType(OrderType.LIMIT);
        invalidRequest.setPrice(null); // Missing price for LIMIT
        invalidRequest.setQuantity(100);

        assertThrows(IllegalArgumentException.class, () -> orderService.processOrder(invalidRequest));
        verify(tradeRepository, never()).saveAll(anyList());
    }

    @Test
    void concurrentOrderProcessingShouldPersistAllExecutedTradesSafely() throws Exception {
        int threads = 20;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<List<Trade>>> futures = new java.util.ArrayList<>();

        // 10 BUY orders and 10 SELL orders at matching price
        for (int i = 1; i <= threads; i++) {
            final int id = i;
            futures.add(pool.submit(() -> {
                latch.await();
                OrderRequest req = new OrderRequest();
                req.setUserId("USER_" + id);
                req.setSymbol("AAPL");
                req.setSide((id % 2 == 0) ? OrderSide.BUY : OrderSide.SELL);
                req.setType(OrderType.LIMIT);
                req.setPrice(new BigDecimal("100.00"));
                req.setQuantity(10);
                return orderService.processOrder(req);
            }));
        }

        latch.countDown();
        for (java.util.concurrent.Future<List<Trade>> f : futures) {
            f.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertTrue(orderBook.isEmpty(), "Order book should be empty after symmetric concurrent matching");
        verify(tradeRepository, atLeastOnce()).saveAll(anyList());
    }
}
