package com.apexmatch.engine;

import com.apexmatch.model.Order;
import com.apexmatch.model.OrderSide;
import com.apexmatch.model.OrderType;
import com.apexmatch.model.Trade;

import java.util.ArrayList;
import java.util.List;
import com.apexmatch.validation.OrderValidator;

public class MatchingEngine {

    private final OrderBook orderBook;

    public MatchingEngine(OrderBook orderBook) {
        this.orderBook = orderBook;
    }

    public List<Trade> submitOrder(Order incomingOrder) {
        
        OrderValidator.validate(incomingOrder);
        List<Trade> trades = new ArrayList<>();

        while (true) {

            Order bestOppositeOrder;

            // Find the best order on the opposite side
            if (incomingOrder.getSide() == OrderSide.BUY) {
                bestOppositeOrder = orderBook.getBestSell();
            } else {
                bestOppositeOrder = orderBook.getBestBuy();
            }

            // No order available to match
            if (bestOppositeOrder == null) {
                break;
            }

            // Different stocks cannot be matched
            if (!incomingOrder.getSymbol()
                    .equals(bestOppositeOrder.getSymbol())) {
                break;
            }

            boolean canMatch;

            // Market orders can match with any available opposite order
            if (incomingOrder.getType() == OrderType.MARKET) {

                canMatch = true;

            } else {

                // Limit order price comparison
                if (incomingOrder.getSide() == OrderSide.BUY) {

                    canMatch =
                            incomingOrder.getPrice()
                                    .compareTo(bestOppositeOrder.getPrice()) >= 0;

                } else {

                    canMatch =
                            incomingOrder.getPrice()
                                    .compareTo(bestOppositeOrder.getPrice()) <= 0;
                }
            }

            // Prices don't allow a match
            if (!canMatch) {
                break;
            }

            // Trade the smaller available quantity
            long tradeQuantity = Math.min(
                    incomingOrder.getQuantity(),
                    bestOppositeOrder.getQuantity()
            );

            // Determine buyer and seller
            String buyerId;
            String sellerId;

            if (incomingOrder.getSide() == OrderSide.BUY) {

                buyerId = incomingOrder.getUserId();
                sellerId = bestOppositeOrder.getUserId();

            } else {

                buyerId = bestOppositeOrder.getUserId();
                sellerId = incomingOrder.getUserId();
            }

            // Reduce quantities
            incomingOrder.reduceQuantity(tradeQuantity);
            bestOppositeOrder.reduceQuantity(tradeQuantity);

            // Create trade using the resting order's price
            Trade trade = new Trade(
                    "TRD-" + (trades.size() + 1),
                    incomingOrder.getSymbol(),
                    buyerId,
                    sellerId,
                    bestOppositeOrder.getPrice(),
                    tradeQuantity
            );

            trades.add(trade);

            // Remove completely filled opposite order
            if (bestOppositeOrder.getQuantity() == 0) {

                if (incomingOrder.getSide() == OrderSide.BUY) {
                    orderBook.removeBestSell();
                } else {
                    orderBook.removeBestBuy();
                }
            }

            // Incoming order completely filled
            if (incomingOrder.getQuantity() == 0) {
                break;
            }
        }

        // Only LIMIT orders can remain in the order book
        if (incomingOrder.getQuantity() > 0
                && incomingOrder.getType() == OrderType.LIMIT) {

            orderBook.addOrder(incomingOrder);
        }

        return trades;
    }
}