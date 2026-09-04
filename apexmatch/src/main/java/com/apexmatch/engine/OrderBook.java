package com.apexmatch.engine;

import com.apexmatch.model.Order;
import com.apexmatch.model.OrderSide;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.PriorityQueue;

@Component
public class OrderBook {

    private final PriorityQueue<Order> buyOrders =
            new PriorityQueue<>(
                    Comparator.comparing(Order::getPrice)
                            .reversed()
                            .thenComparing(Order::getSequenceNumber)
            );

    private final PriorityQueue<Order> sellOrders =
            new PriorityQueue<>(
                    Comparator.comparing(Order::getPrice)
                            .thenComparing(Order::getSequenceNumber)
            );

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