package com.apexmatch.engine;

import com.apexmatch.model.Order;
import com.apexmatch.model.OrderSide;
import com.apexmatch.model.OrderType;
import com.apexmatch.model.Trade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class MatchingEngine {

    private final OrderBookManager orderBookManager;
    private final ReentrantLock customLock;
    private final AtomicLong tradeCounter = new AtomicLong(1);
    private volatile ReentrantLock lastAcquiredLock;

    @Autowired
    public MatchingEngine(OrderBookManager orderBookManager) {
        this(orderBookManager, null);
    }

    public MatchingEngine(OrderBookManager orderBookManager, ReentrantLock customLock) {
        this.orderBookManager = orderBookManager;
        this.customLock = customLock;
        if (customLock != null) {
            this.lastAcquiredLock = customLock;
        }
    }

    public MatchingEngine(OrderBook orderBook) {
        this(new OrderBookManager(orderBook), new ReentrantLock());
    }

    public MatchingEngine(OrderBook orderBook, ReentrantLock customLock) {
        this(new OrderBookManager(orderBook), customLock);
    }

    public List<Trade> submitOrder(Order incomingOrder) {
        if (incomingOrder == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        if (incomingOrder.getSymbol() == null || incomingOrder.getSymbol().isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be empty");
        }

        String symbol = OrderBookManager.normalizeSymbol(incomingOrder.getSymbol());
        ReentrantLock lockToUse = (customLock != null) ? customLock : orderBookManager.getLockForSymbol(symbol);

        lockToUse.lock();
        this.lastAcquiredLock = lockToUse;
        try {
            OrderBook orderBook = orderBookManager.getOrCreateOrderBook(symbol);
            List<Trade> trades = new ArrayList<>();

            while (incomingOrder.getQuantity() > 0) {
                Order oppositeOrder;

                if (incomingOrder.getSide() == OrderSide.BUY) {
                    oppositeOrder = orderBook.getBestSell();
                } else {
                    oppositeOrder = orderBook.getBestBuy();
                }

                // No opposite order
                if (oppositeOrder == null) {
                    break;
                }

                // Defensive symbol check for legacy shared books
                if (!symbol.equals(OrderBookManager.normalizeSymbol(oppositeOrder.getSymbol()))) {
                    break;
                }

                // Price check for LIMIT orders
                if (incomingOrder.getType() == OrderType.LIMIT &&
                        oppositeOrder.getType() == OrderType.LIMIT) {

                    if (incomingOrder.getSide() == OrderSide.BUY &&
                            incomingOrder.getPrice().compareTo(oppositeOrder.getPrice()) < 0) {
                        break;
                    }

                    if (incomingOrder.getSide() == OrderSide.SELL &&
                            incomingOrder.getPrice().compareTo(oppositeOrder.getPrice()) > 0) {
                        break;
                    }
                }

                long tradeQuantity = Math.min(
                        incomingOrder.getQuantity(),
                        oppositeOrder.getQuantity()
                );

                BigDecimal tradePrice = oppositeOrder.getPrice();

                String buyer;
                String seller;

                if (incomingOrder.getSide() == OrderSide.BUY) {
                    buyer = incomingOrder.getUserId();
                    seller = oppositeOrder.getUserId();
                } else {
                    buyer = oppositeOrder.getUserId();
                    seller = incomingOrder.getUserId();
                }

                Trade trade = new Trade(
                        "TRD-" + tradeCounter.getAndIncrement(),
                        symbol,
                        buyer,
                        seller,
                        tradePrice,
                        tradeQuantity
                );

                trades.add(trade);

                incomingOrder.setQuantity(
                        incomingOrder.getQuantity() - tradeQuantity
                );

                oppositeOrder.setQuantity(
                        oppositeOrder.getQuantity() - tradeQuantity
                );

                if (oppositeOrder.getQuantity() == 0) {
                    if (oppositeOrder.getSide() == OrderSide.BUY) {
                        orderBook.removeBestBuy();
                    } else {
                        orderBook.removeBestSell();
                    }
                }
            }

            // Remaining incoming order goes into the symbol's order book
            if (incomingOrder.getQuantity() > 0) {
                if (incomingOrder.getType() == OrderType.LIMIT) {
                    orderBook.addOrder(incomingOrder);
                }
            }

            return trades;
        } finally {
            lockToUse.unlock();
        }
    }

    public OrderBookManager getOrderBookManager() {
        return orderBookManager;
    }

    public OrderBook getOrderBook(String symbol) {
        return orderBookManager.getOrderBook(symbol);
    }

    public OrderBook getOrderBook() {
        if (orderBookManager.getDefaultBook() != null) {
            return orderBookManager.getDefaultBook();
        }
        if (orderBookManager.hasOrderBook("AAPL")) {
            return orderBookManager.getOrderBook("AAPL");
        }
        return orderBookManager.getActiveSymbols().isEmpty()
                ? null
                : orderBookManager.getOrderBook(orderBookManager.getActiveSymbols().iterator().next());
    }

    public ReentrantLock getLock(String symbol) {
        return (customLock != null) ? customLock : orderBookManager.getLockForSymbol(symbol);
    }

    public ReentrantLock getLock() {
        if (lastAcquiredLock != null) {
            return lastAcquiredLock;
        }
        if (customLock != null) {
            return customLock;
        }
        return orderBookManager.getLockForSymbol("AAPL");
    }
}