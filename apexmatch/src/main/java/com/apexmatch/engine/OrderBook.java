package com.apexmatch.engine;

import com.apexmatch.model.Order;
import com.apexmatch.model.OrderSide;

import java.util.PriorityQueue;

public class OrderBook {

    private final PriorityQueue<Order> buyOrders;
    private final PriorityQueue<Order> sellOrders;

    public OrderBook() {

        buyOrders = new PriorityQueue<>(
                (order1, order2) -> {
                    int priceComparison =
                            Double.compare(order2.getPrice(), order1.getPrice());

                    if (priceComparison != 0) {
                        return priceComparison;
                    }

                    return Long.compare(
                            order1.getSequenceNumber(),
                            order2.getSequenceNumber()
                    );
                }
        );

        sellOrders = new PriorityQueue<>(
                (order1, order2) -> {
                    int priceComparison =
                            Double.compare(order1.getPrice(), order2.getPrice());

                    if (priceComparison != 0) {
                        return priceComparison;
                    }

                    return Long.compare(
                            order1.getSequenceNumber(),
                            order2.getSequenceNumber()
                    );
                }
        );
    }

    public void addOrder(Order order) {

        if (order.getSide() == OrderSide.BUY) {
            buyOrders.add(order);
        } else {
            sellOrders.add(order);
        }
    }

    public Order getBestBuy() {
        return buyOrders.peek();
    }

    public Order getBestSell() {
        return sellOrders.peek();
    }
    public Order removeBestBuy() {
    return buyOrders.poll();
    }

    public Order removeBestSell() {
        return sellOrders.poll();
    }
}