package com.apexmatch;

import com.apexmatch.engine.MatchingEngine;
import com.apexmatch.engine.OrderBook;
import com.apexmatch.model.Order;
import com.apexmatch.model.OrderSide;
import com.apexmatch.model.OrderType;
import com.apexmatch.model.Trade;

import java.math.BigDecimal;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        // Create order book and matching engine
        OrderBook orderBook = new OrderBook();
        MatchingEngine engine = new MatchingEngine(orderBook);

        // Bob places a LIMIT SELL order
        Order bob = new Order(
                "ORD-001",
                "BOB",
                "AAPL",
                OrderSide.SELL,
                OrderType.LIMIT,
                new BigDecimal("145.00"),
                60,
                1
        );

       

        // Submit Alice's order to the matching engine
        List<Trade> trades = engine.submitOrder(alice);

        // Display executed trades
        for (Trade trade : trades) {

            System.out.println("TRADE EXECUTED");
            System.out.println("----------------");
            System.out.println("Trade ID: " + trade.getTradeId());
            System.out.println("Stock: " + trade.getSymbol());
            System.out.println("Buyer: " + trade.getBuyerId());
            System.out.println("Seller: " + trade.getSellerId());
            System.out.println("Price: $" + trade.getPrice());
            System.out.println("Quantity: " + trade.getQuantity());
        }

        // Display remaining quantity
        System.out.println();
        System.out.println(
                "Alice remaining quantity: "
                        + alice.getQuantity()
        );
    }
}