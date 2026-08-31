package com.apexmatch;

import com.apexmatch.engine.MatchingEngine;
import com.apexmatch.engine.OrderBook;
import com.apexmatch.model.Order;
import com.apexmatch.model.OrderSide;
import com.apexmatch.model.OrderType;
import com.apexmatch.model.Trade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MatchingEngineTest {

    // ---------------------------------------------------------
    // 1. MARKET BUY
    // ---------------------------------------------------------

    @Test
    void marketBuyShouldMatchWithSellOrder() {

        OrderBook orderBook = new OrderBook();
        MatchingEngine engine = new MatchingEngine(orderBook);

        Order bob = createSellOrder(
                "ORD-001",
                "BOB",
                "145.00",
                60,
                1
        );

        orderBook.addOrder(bob);

        Order alice = createMarketOrder(
                "ORD-002",
                "ALICE",
                OrderSide.BUY,
                100,
                2
        );

        List<Trade> trades = engine.submitOrder(alice);

        assertEquals(1, trades.size());

        Trade trade = trades.get(0);

        assertEquals("ALICE", trade.getBuyerId());
        assertEquals("BOB", trade.getSellerId());
        assertEquals("AAPL", trade.getSymbol());
        assertEquals(new BigDecimal("145.00"), trade.getPrice());
        assertEquals(60, trade.getQuantity());

        assertEquals(40, alice.getQuantity());
        assertEquals(0, bob.getQuantity());
    }


    // ---------------------------------------------------------
    // 2. LIMIT BUY - SHOULD MATCH
    // ---------------------------------------------------------

    @Test
    void limitBuyShouldMatchWhenPriceIsHighEnough() {

        OrderBook orderBook = new OrderBook();
        MatchingEngine engine = new MatchingEngine(orderBook);

        Order bob = createSellOrder(
                "ORD-001",
                "BOB",
                "145.00",
                60,
                1
        );

        orderBook.addOrder(bob);

        Order alice = createLimitOrder(
                "ORD-002",
                "ALICE",
                OrderSide.BUY,
                "150.00",
                100,
                2
        );

        List<Trade> trades = engine.submitOrder(alice);

        assertEquals(1, trades.size());

        Trade trade = trades.get(0);

        assertEquals("ALICE", trade.getBuyerId());
        assertEquals("BOB", trade.getSellerId());
        assertEquals(new BigDecimal("145.00"), trade.getPrice());
        assertEquals(60, trade.getQuantity());

        assertEquals(40, alice.getQuantity());
        assertEquals(0, bob.getQuantity());
    }


    // ---------------------------------------------------------
    // 3. LIMIT BUY - SHOULD NOT MATCH
    // ---------------------------------------------------------

    @Test
    void limitBuyShouldNotMatchWhenPriceIsTooLow() {

        OrderBook orderBook = new OrderBook();
        MatchingEngine engine = new MatchingEngine(orderBook);

        Order bob = createSellOrder(
                "ORD-001",
                "BOB",
                "145.00",
                60,
                1
        );

        orderBook.addOrder(bob);

        Order alice = createLimitOrder(
                "ORD-002",
                "ALICE",
                OrderSide.BUY,
                "140.00",
                100,
                2
        );

        List<Trade> trades = engine.submitOrder(alice);

        assertEquals(0, trades.size());

        assertEquals(100, alice.getQuantity());
        assertEquals(60, bob.getQuantity());

        assertEquals(alice, orderBook.getBestBuy());
        assertEquals(bob, orderBook.getBestSell());
    }


    // ---------------------------------------------------------
    // 4. LIMIT SELL - SHOULD MATCH
    // ---------------------------------------------------------

    @Test
    void limitSellShouldMatchWhenPriceIsLowEnough() {

        OrderBook orderBook = new OrderBook();
        MatchingEngine engine = new MatchingEngine(orderBook);

        Order alice = createLimitOrder(
                "ORD-001",
                "ALICE",
                OrderSide.BUY,
                "150.00",
                100,
                1
        );

        orderBook.addOrder(alice);

        Order bob = createLimitOrder(
                "ORD-002",
                "BOB",
                OrderSide.SELL,
                "145.00",
                60,
                2
        );

        List<Trade> trades = engine.submitOrder(bob);

        assertEquals(1, trades.size());

        Trade trade = trades.get(0);

        assertEquals("ALICE", trade.getBuyerId());
        assertEquals("BOB", trade.getSellerId());
        assertEquals(new BigDecimal("150.00"), trade.getPrice());
        assertEquals(60, trade.getQuantity());

        assertEquals(40, alice.getQuantity());
        assertEquals(0, bob.getQuantity());
    }


    // ---------------------------------------------------------
    // 5. LIMIT SELL - SHOULD NOT MATCH
    // ---------------------------------------------------------

    @Test
    void limitSellShouldNotMatchWhenPriceIsTooHigh() {

        OrderBook orderBook = new OrderBook();
        MatchingEngine engine = new MatchingEngine(orderBook);

        Order alice = createLimitOrder(
                "ORD-001",
                "ALICE",
                OrderSide.BUY,
                "140.00",
                100,
                1
        );

        orderBook.addOrder(alice);

        Order bob = createLimitOrder(
                "ORD-002",
                "BOB",
                OrderSide.SELL,
                "145.00",
                60,
                2
        );

        List<Trade> trades = engine.submitOrder(bob);

        assertEquals(0, trades.size());

        assertEquals(100, alice.getQuantity());
        assertEquals(60, bob.getQuantity());

        assertEquals(alice, orderBook.getBestBuy());
        assertEquals(bob, orderBook.getBestSell());
    }


    // ---------------------------------------------------------
    // 6. HIGHEST BUY GETS PRIORITY
    // ---------------------------------------------------------

    @Test
    void highestBuyPriceShouldGetPriority() {

        OrderBook orderBook = new OrderBook();
        MatchingEngine engine = new MatchingEngine(orderBook);

        Order alice = createLimitOrder(
                "ORD-001",
                "ALICE",
                OrderSide.BUY,
                "145.00",
                50,
                1
        );

        Order bob = createLimitOrder(
                "ORD-002",
                "BOB",
                OrderSide.BUY,
                "150.00",
                50,
                2
        );

        orderBook.addOrder(alice);
        orderBook.addOrder(bob);

        Order seller = createLimitOrder(
                "ORD-003",
                "CHARLIE",
                OrderSide.SELL,
                "140.00",
                50,
                3
        );

        List<Trade> trades = engine.submitOrder(seller);

        assertEquals(1, trades.size());

        Trade trade = trades.get(0);

        assertEquals("BOB", trade.getBuyerId());
        assertEquals("CHARLIE", trade.getSellerId());
    }


    // ---------------------------------------------------------
    // 7. LOWEST SELL GETS PRIORITY
    // ---------------------------------------------------------

    @Test
    void lowestSellPriceShouldGetPriority() {

        OrderBook orderBook = new OrderBook();
        MatchingEngine engine = new MatchingEngine(orderBook);

        Order alice = createSellOrder(
                "ORD-001",
                "ALICE",
                "150.00",
                50,
                1
        );

        Order bob = createSellOrder(
                "ORD-002",
                "BOB",
                "145.00",
                50,
                2
        );

        orderBook.addOrder(alice);
        orderBook.addOrder(bob);

        Order buyer = createLimitOrder(
                "ORD-003",
                "CHARLIE",
                OrderSide.BUY,
                "155.00",
                50,
                3
        );

        List<Trade> trades = engine.submitOrder(buyer);

        assertEquals(1, trades.size());

        Trade trade = trades.get(0);

        assertEquals("CHARLIE", trade.getBuyerId());
        assertEquals("BOB", trade.getSellerId());
    }


    // ---------------------------------------------------------
    // 8. FIFO
    // ---------------------------------------------------------

    @Test
    void olderOrderShouldGetPriorityWhenPricesAreEqual() {

        OrderBook orderBook = new OrderBook();
        MatchingEngine engine = new MatchingEngine(orderBook);

        Order alice = createSellOrder(
                "ORD-001",
                "ALICE",
                "145.00",
                50,
                1
        );

        Order bob = createSellOrder(
                "ORD-002",
                "BOB",
                "145.00",
                50,
                2
        );

        orderBook.addOrder(alice);
        orderBook.addOrder(bob);

        Order buyer = createLimitOrder(
                "ORD-003",
                "CHARLIE",
                OrderSide.BUY,
                "150.00",
                50,
                3
        );

        List<Trade> trades = engine.submitOrder(buyer);

        assertEquals(1, trades.size());

        Trade trade = trades.get(0);

        assertEquals("ALICE", trade.getSellerId());
    }


    // ---------------------------------------------------------
    // 9. PARTIAL FILL
    // ---------------------------------------------------------

    @Test
    void partialFillShouldLeaveRemainingQuantity() {

        OrderBook orderBook = new OrderBook();
        MatchingEngine engine = new MatchingEngine(orderBook);

        Order bob = createSellOrder(
                "ORD-001",
                "BOB",
                "145.00",
                40,
                1
        );

        orderBook.addOrder(bob);

        Order alice = createLimitOrder(
                "ORD-002",
                "ALICE",
                OrderSide.BUY,
                "150.00",
                100,
                2
        );

        List<Trade> trades = engine.submitOrder(alice);

        assertEquals(1, trades.size());

        assertEquals(40, trades.get(0).getQuantity());

        assertEquals(60, alice.getQuantity());
        assertEquals(0, bob.getQuantity());

        assertEquals(alice, orderBook.getBestBuy());
    }


    // ---------------------------------------------------------
    // 10. MULTIPLE MATCHES
    // ---------------------------------------------------------

    @Test
    void oneOrderShouldMatchMultipleOppositeOrders() {

        OrderBook orderBook = new OrderBook();
        MatchingEngine engine = new MatchingEngine(orderBook);

        Order bob = createSellOrder(
                "ORD-001",
                "BOB",
                "145.00",
                30,
                1
        );

        Order david = createSellOrder(
                "ORD-002",
                "DAVID",
                "146.00",
                40,
                2
        );

        Order emma = createSellOrder(
                "ORD-003",
                "EMMA",
                "148.00",
                50,
                3
        );

        orderBook.addOrder(bob);
        orderBook.addOrder(david);
        orderBook.addOrder(emma);

        Order alice = createLimitOrder(
                "ORD-004",
                "ALICE",
                OrderSide.BUY,
                "150.00",
                100,
                4
        );

        List<Trade> trades = engine.submitOrder(alice);

        assertEquals(3, trades.size());

        assertEquals("BOB", trades.get(0).getSellerId());
        assertEquals(30, trades.get(0).getQuantity());

        assertEquals("DAVID", trades.get(1).getSellerId());
        assertEquals(40, trades.get(1).getQuantity());

        assertEquals("EMMA", trades.get(2).getSellerId());
        assertEquals(30, trades.get(2).getQuantity());

        assertEquals(20, emma.getQuantity());
        assertEquals(0, alice.getQuantity());
    }


    // ---------------------------------------------------------
    // 11. DIFFERENT SYMBOLS SHOULD NOT MATCH
    // ---------------------------------------------------------

    @Test
    void differentSymbolsShouldNotMatch() {

        OrderBook orderBook = new OrderBook();
        MatchingEngine engine = new MatchingEngine(orderBook);

        Order bob = new Order(
                "ORD-001",
                "BOB",
                "TSLA",
                OrderSide.SELL,
                OrderType.LIMIT,
                new BigDecimal("145.00"),
                60,
                1
        );

        orderBook.addOrder(bob);

        Order alice = new Order(
                "ORD-002",
                "ALICE",
                "AAPL",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("150.00"),
                100,
                2
        );

        List<Trade> trades = engine.submitOrder(alice);

        assertEquals(0, trades.size());
        assertEquals(100, alice.getQuantity());
        assertEquals(60, bob.getQuantity());
    }


    // ---------------------------------------------------------
    // 12. EMPTY ORDER BOOK
    // ---------------------------------------------------------

    @Test
    void orderShouldBeStoredWhenNoOppositeOrderExists() {

        OrderBook orderBook = new OrderBook();
        MatchingEngine engine = new MatchingEngine(orderBook);

        Order alice = createLimitOrder(
                "ORD-001",
                "ALICE",
                OrderSide.BUY,
                "150.00",
                100,
                1
        );

        List<Trade> trades = engine.submitOrder(alice);

        assertEquals(0, trades.size());

        assertEquals(100, alice.getQuantity());
        assertEquals(alice, orderBook.getBestBuy());
    }


    // ---------------------------------------------------------
    // HELPER METHODS
    // ---------------------------------------------------------

    private Order createLimitOrder(
            String orderId,
            String userId,
            OrderSide side,
            String price,
            long quantity,
            long sequenceNumber
    ) {

        return new Order(
                orderId,
                userId,
                "AAPL",
                side,
                OrderType.LIMIT,
                new BigDecimal(price),
                quantity,
                sequenceNumber
        );
    }


    private Order createMarketOrder(
            String orderId,
            String userId,
            OrderSide side,
            long quantity,
            long sequenceNumber
    ) {

        return new Order(
                orderId,
                userId,
                "AAPL",
                side,
                OrderType.MARKET,
                null,
                quantity,
                sequenceNumber
        );
    }


    private Order createSellOrder(
            String orderId,
            String userId,
            String price,
            long quantity,
            long sequenceNumber
    ) {

        return createLimitOrder(
                orderId,
                userId,
                OrderSide.SELL,
                price,
                quantity,
                sequenceNumber
        );
    }
}