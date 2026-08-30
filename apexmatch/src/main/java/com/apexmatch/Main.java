package com.apexmatch;

import com.apexmatch.model.Order;
import com.apexmatch.model.OrderSide;

public class Main {

    public static void main(String[] args) {

        Order aliceOrder = new Order(
                "ORD-001",
                "USER-101",
                "AAPL",
                OrderSide.BUY,
                150.00,
                100,
                1
        );

        System.out.println("Order ID: " + aliceOrder.getOrderId());
        System.out.println("User: " + aliceOrder.getUserId());
        System.out.println("Symbol: " + aliceOrder.getSymbol());
        System.out.println("Side: " + aliceOrder.getSide());
        System.out.println("Price: $" + aliceOrder.getPrice());
        System.out.println("Quantity: " + aliceOrder.getQuantity());

        aliceOrder.reduceQuantity(40);

        System.out.println("Remaining quantity: " + aliceOrder.getQuantity());
    }
}