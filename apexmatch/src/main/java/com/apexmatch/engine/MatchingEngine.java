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

    private final OrderBook orderBook;
    private final ReentrantLock lock;
    private final AtomicLong tradeCounter = new AtomicLong(1);

    @Autowired
    public MatchingEngine(OrderBook orderBook) {
        this(orderBook, new ReentrantLock());
    }

    public MatchingEngine(OrderBook orderBook, ReentrantLock lock) {
        this.orderBook = orderBook;
        this.lock = lock;
    }

    public List<Trade> submitOrder(Order incomingOrder) {
        lock.lock();
        try {
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

                // Different stock
                if (!incomingOrder.getSymbol()
                        .equals(oppositeOrder.getSymbol())) {
                    break;
                }

                // Price check for LIMIT orders
                if (incomingOrder.getType() == OrderType.LIMIT &&
                        oppositeOrder.getType() == OrderType.LIMIT) {

                    if (incomingOrder.getSide() == OrderSide.BUY &&
                            incomingOrder.getPrice()
                                    .compareTo(oppositeOrder.getPrice()) < 0) {
                        break;
                    }

                    if (incomingOrder.getSide() == OrderSide.SELL &&
                            incomingOrder.getPrice()
                                    .compareTo(oppositeOrder.getPrice()) > 0) {
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
                        incomingOrder.getSymbol(),
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

            // Remaining incoming order goes into the book
            if (incomingOrder.getQuantity() > 0) {

                if (incomingOrder.getType() == OrderType.LIMIT) {
                    orderBook.addOrder(incomingOrder);
                }
            }

            return trades;
        } finally {
            lock.unlock();
        }
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public OrderBook getOrderBook() {
        return orderBook;
    }
}