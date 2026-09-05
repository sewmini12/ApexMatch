package com.apexmatch.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Executed trade execution details")
public class Trade {

    @Schema(description = "Unique trade execution identifier", example = "TRD-1")
    private String tradeId;

    @Schema(description = "Stock ticker symbol traded", example = "AAPL")
    private String symbol;

    @Schema(description = "User identifier of the buyer", example = "ALICE")
    private String buyer;

    @Schema(description = "User identifier of the seller", example = "BOB")
    private String seller;

    @Schema(description = "Execution price per unit", example = "145.00")
    private BigDecimal price;

    @Schema(description = "Number of shares executed in this trade", example = "60")
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