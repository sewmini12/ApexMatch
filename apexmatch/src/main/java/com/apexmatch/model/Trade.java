package com.apexmatch.model;

public class Trade {

    private final String tradeId;
    private final String symbol;
    private final String buyerId;
    private final String sellerId;
    private final double price;
    private final long quantity;

    public Trade(
            String tradeId,
            String symbol,
            String buyerId,
            String sellerId,
            double price,
            long quantity
    ) {
        this.tradeId = tradeId;
        this.symbol = symbol;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.price = price;
        this.quantity = quantity;
    }

    public String getTradeId() {
        return tradeId;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getBuyerId() {
        return buyerId;
    }

    public String getSellerId() {
        return sellerId;
    }

    public double getPrice() {
        return price;
    }

    public long getQuantity() {
        return quantity;
    }
}