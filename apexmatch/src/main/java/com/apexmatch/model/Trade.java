package com.apexmatch.model;

import java.math.BigDecimal;

public class Trade {

    private String tradeId;
    private String symbol;
    private String buyer;
    private String seller;
    private BigDecimal price;
    private long quantity;

    public Trade(
            String tradeId,
            String symbol,
            String buyer,
            String seller,
            BigDecimal price,
            long quantity) {

        this.tradeId = tradeId;
        this.symbol = symbol;
        this.buyer = buyer;
        this.seller = seller;
        this.price = price;
        this.quantity = quantity;
    }

    public String getTradeId() {
        return tradeId;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getBuyer() {
        return buyer;
    }

    public String getBuyerId() {
        return buyer;
    }

    public String getSeller() {
        return seller;
    }

    public String getSellerId() {
        return seller;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public long getQuantity() {
        return quantity;
    }
}