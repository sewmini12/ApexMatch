package com.apexmatch;

import com.apexmatch.engine.OrderBook;
import com.apexmatch.model.Order;
import com.apexmatch.model.OrderSide;

public class Main {

    public static void main(String[] args) {

        OrderBook orderBook = new OrderBook();

        // BUY orders
        Order alice = new Order(
                "ORD-001",
                "ALICE",
                "AAPL",
                OrderSide.BUY,
                150.00,
                100,
                1
        );

        Order bob = new Order(
                "ORD-002",
                "BOB",
                "AAPL",
                OrderSide.BUY,
                145.00,
                100,
                2
        );

        Order charlie = new Order(
                "ORD-003",
                "CHARLIE",
                "AAPL",
                OrderSide.BUY,
                155.00,
                100,
                3
        );

        orderBook.addOrder(alice);
        orderBook.addOrder(bob);
        orderBook.addOrder(charlie);

        // Check best BUY
        Order bestBuy = orderBook.getBestBuy();

        System.out.println("Best BUY:");
        System.out.println(
                bestBuy.getUserId() +
                " - $" +
                bestBuy.getPrice()
        );
    }
    
}