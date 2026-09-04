
package com.apexmatch.model;

import java.math.BigDecimal;

public class Order {

    private String orderId;
    private String userId;
    private String symbol;
    private OrderSide side;
    private OrderType type;
    private BigDecimal price;
    private long quantity;
    private long sequenceNumber;

    public Order(
            String orderId,
            String userId,
            String symbol,
            OrderSide side,
            OrderType type,
            BigDecimal price,
            long quantity,
            long sequenceNumber) {

        this.orderId = orderId;
        this.userId = userId;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
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

    public OrderType getType() {
        return type;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }
}
