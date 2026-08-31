package com.apexmatch.validation;

import com.apexmatch.model.Order;
import com.apexmatch.model.OrderType;

public class OrderValidator {

    public static void validate(Order order) {

        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }

        if (order.getOrderId() == null
                || order.getOrderId().isBlank()) {
            throw new IllegalArgumentException(
                    "Order ID cannot be empty"
            );
        }

        if (order.getUserId() == null
                || order.getUserId().isBlank()) {
            throw new IllegalArgumentException(
                    "User ID cannot be empty"
            );
        }

        if (order.getSymbol() == null
                || order.getSymbol().isBlank()) {
            throw new IllegalArgumentException(
                    "Symbol cannot be empty"
            );
        }

        if (order.getQuantity() <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero"
            );
        }

        // LIMIT orders must have a valid price
        if (order.getType() == OrderType.LIMIT) {

            if (order.getPrice() == null) {
                throw new IllegalArgumentException(
                        "Limit order must have a price"
                );
            }

            if (order.getPrice().signum() <= 0) {
                throw new IllegalArgumentException(
                        "Limit order price must be greater than zero"
                );
            }
        }

        // MARKET orders should not have a price
        if (order.getType() == OrderType.MARKET) {

            if (order.getPrice() != null) {
                throw new IllegalArgumentException(
                        "Market order should not have a price"
                );
            }
        }
    }
}