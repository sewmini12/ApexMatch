package com.apexmatch.model;
import java.math.BigDecimal;
public class Trade {

    private final String tradeId;
    private final String symbol;
    private final String buyerId;
    private final String sellerId;
    private final BigDecimal price;
    private final long quantity;

    public Trade(
            String tradeId,
            String symbol,
            String buyerId,
            String sellerId,
            BigDecimal price,
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

    public BigDecimal getPrice() {
        return price;
    }

    public long getQuantity() {
        return quantity;
    }
}