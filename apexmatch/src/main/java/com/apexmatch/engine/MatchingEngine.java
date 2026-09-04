package com.apexmatch.engine;

import com.apexmatch.model.Order;
import com.apexmatch.model.OrderSide;
import com.apexmatch.model.OrderType;
import com.apexmatch.model.Trade;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MatchingEngine {

    private final OrderBook orderBook;

    private final AtomicLong tradeCounter = new AtomicLong(1);

    public MatchingEngine(OrderBook orderBook) {
        this.orderBook = orderBook;
    }

    public synchronized List<Trade> submitOrder(Order incomingOrder) {

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
    }
}