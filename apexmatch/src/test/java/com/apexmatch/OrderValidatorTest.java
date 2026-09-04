package com.apexmatch;

import com.apexmatch.model.Order;
import com.apexmatch.model.OrderSide;
import com.apexmatch.model.OrderType;
import com.apexmatch.validation.OrderValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class OrderValidatorTest {

    // 1. Valid LIMIT order
    @Test
    void validLimitOrderShouldPass() {

        Order order = new Order(
                "ORD-001",
                "ALICE",
                "AAPL",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("150.00"),
                100,
                1
        );

        assertDoesNotThrow(() -> OrderValidator.validate(order));
    }


    // 2. Valid MARKET order
    @Test
    void validMarketOrderShouldPass() {

        Order order = new Order(
                "ORD-002",
                "ALICE",
                "AAPL",
                OrderSide.BUY,
                OrderType.MARKET,
                null,
                100,
                2
        );

        assertDoesNotThrow(() -> OrderValidator.validate(order));
    }


    // 3. Null order
    @Test
    void nullOrderShouldBeRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () -> OrderValidator.validate(null)
        );
    }


    // 4. Empty order ID
    @Test
    void emptyOrderIdShouldBeRejected() {

        Order order = new Order(
                "",
                "ALICE",
                "AAPL",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("150.00"),
                100,
                1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> OrderValidator.validate(order)
        );
    }


    // 5. Empty user ID
    @Test
    void emptyUserIdShouldBeRejected() {

        Order order = new Order(
                "ORD-001",
                "",
                "AAPL",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("150.00"),
                100,
                1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> OrderValidator.validate(order)
        );
    }


    // 6. Empty symbol
    @Test
    void emptySymbolShouldBeRejected() {

        Order order = new Order(
                "ORD-001",
                "ALICE",
                "",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("150.00"),
                100,
                1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> OrderValidator.validate(order)
        );
    }


    // 7. Zero quantity
    @Test
    void zeroQuantityShouldBeRejected() {

        Order order = new Order(
                "ORD-001",
                "ALICE",
                "AAPL",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("150.00"),
                0,
                1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> OrderValidator.validate(order)
        );
    }


    // 8. Negative quantity
    @Test
    void negativeQuantityShouldBeRejected() {

        Order order = new Order(
                "ORD-001",
                "ALICE",
                "AAPL",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("150.00"),
                -10,
                1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> OrderValidator.validate(order)
        );
    }


    // 9. LIMIT order without price
    @Test
    void limitOrderWithoutPriceShouldBeRejected() {

        Order order = new Order(
                "ORD-001",
                "ALICE",
                "AAPL",
                OrderSide.BUY,
                OrderType.LIMIT,
                null,
                100,
                1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> OrderValidator.validate(order)
        );
    }


    // 10. LIMIT order with zero price
    @Test
    void limitOrderWithZeroPriceShouldBeRejected() {

        Order order = new Order(
                "ORD-001",
                "ALICE",
                "AAPL",
                OrderSide.BUY,
                OrderType.LIMIT,
                BigDecimal.ZERO,
                100,
                1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> OrderValidator.validate(order)
        );
    }


    // 11. LIMIT order with negative price
    @Test
    void limitOrderWithNegativePriceShouldBeRejected() {

        Order order = new Order(
                "ORD-001",
                "ALICE",
                "AAPL",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("-10.00"),
                100,
                1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> OrderValidator.validate(order)
        );
    }


    // 12. MARKET order with price
    @Test
    void marketOrderWithPriceShouldBeRejected() {

        Order order = new Order(
                "ORD-001",
                "ALICE",
                "AAPL",
                OrderSide.BUY,
                OrderType.MARKET,
                new BigDecimal("150.00"),
                100,
                1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> OrderValidator.validate(order)
        );
    }
}