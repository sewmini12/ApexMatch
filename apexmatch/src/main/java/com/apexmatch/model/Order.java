package com.apexmatch.model;

public class Order { //encapsulation-other class can't directly modify because private
    private final String orderId;
    private final String userId;
    private final String symbol;
    private final OrderSide side;
    private final double price;
    private long quantity;
    private final long sequenceNumber;

    public Order(
            String orderId,
            String userId,
            String symbol,
            OrderSide side,
            double price,
            long quantity,
            long sequenceNumber
    ) {
        this.orderId = orderId;
        this.userId = userId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.sequenceNumber = sequenceNumber;
    }


public String getOrderId() {
    return orderId;
}

public String getUserId() {
    return userId;
}

public String getSymbol() {
    return symbol;
}

public OrderSide getSide() {
    return side;
}

public double getPrice() {
    return price;
}

public long getQuantity() {
    return quantity;
}

public long getSequenceNumber() {
    return sequenceNumber;
}
public void reduceQuantity(long amount) {
    if (amount <= 0) {
        throw new IllegalArgumentException("Amount must be positive");
    }

    if (amount > quantity) {
        throw new IllegalArgumentException("Cannot reduce quantity below zero");
    }

    quantity -= amount;
}
}
