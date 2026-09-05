package com.apexmatch.dto;

import com.apexmatch.model.OrderSide;
import com.apexmatch.model.OrderType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Order submission request payload")
public class OrderRequest {

    @Schema(
            description = "Unique user or account identifier placing the order",
            example = "USER-101",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String userId;

    @Schema(
            description = "Stock ticker symbol to trade (e.g. AAPL, TSLA, MSFT)",
            example = "AAPL",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String symbol;

    @Schema(
            description = "Order side (BUY to purchase, SELL to liquidate)",
            example = "BUY",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private OrderSide side;

    @Schema(
            description = "Order execution type: LIMIT (requires limit price) or MARKET (executes at best available resting price)",
            example = "LIMIT",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private OrderType type;

    @Schema(
            description = "Limit price per share. Required and must be positive for LIMIT orders; must be omitted or null for MARKET orders.",
            example = "150.00"
    )
    private BigDecimal price;

    @Schema(
            description = "Number of shares to trade. Must be a strictly positive integer.",
            example = "100",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private long quantity;

    public OrderRequest() {
    }

    public OrderRequest(String userId, String symbol, OrderSide side, OrderType type, BigDecimal price, long quantity) {
        this.userId = userId;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }

    public OrderType getType() {
        return type;
    }

    public void setType(OrderType type) {
        this.type = type;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }
}