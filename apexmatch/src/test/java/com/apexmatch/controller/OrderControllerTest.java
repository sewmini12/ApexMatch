package com.apexmatch.controller;

import com.apexmatch.dto.OrderRequest;
import com.apexmatch.model.Trade;
import com.apexmatch.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class OrderControllerTest {

    private OrderService orderService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        orderService = Mockito.mock(OrderService.class);
        OrderController controller = new OrderController(orderService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // -------------------------------------------------------------------------
    // 1. Valid Requests
    // -------------------------------------------------------------------------

    @Test
    void createOrderReturnsExecutedTrades() throws Exception {
        Trade trade = new Trade(
                "TRD-1",
                "AAPL",
                "ALICE",
                "BOB",
                new BigDecimal("145.00"),
                60
        );

        when(orderService.processOrder(any(OrderRequest.class)))
                .thenReturn(List.of(trade));

        String json = """
                {
                  "userId": "ALICE",
                  "symbol": "AAPL",
                  "side": "BUY",
                  "type": "LIMIT",
                  "price": 150.00,
                  "quantity": 100
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tradeId").value("TRD-1"))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].buyerId").value("ALICE"))
                .andExpect(jsonPath("$[0].sellerId").value("BOB"))
                .andExpect(jsonPath("$[0].price").value(145.00))
                .andExpect(jsonPath("$[0].quantity").value(60));
    }

    @Test
    void createOrderWhenNoMatchReturnsEmptyArray() throws Exception {
        when(orderService.processOrder(any(OrderRequest.class)))
                .thenReturn(List.of());

        String json = """
                {
                  "userId": "BOB",
                  "symbol": "AAPL",
                  "side": "SELL",
                  "type": "LIMIT",
                  "price": 145.00,
                  "quantity": 60
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void validMarketOrderShouldSucceed() throws Exception {
        Trade trade = new Trade("TRD-2", "AAPL", "CHARLIE", "BOB", new BigDecimal("145.00"), 50);
        when(orderService.processOrder(any(OrderRequest.class)))
                .thenReturn(List.of(trade));

        String json = """
                {
                  "userId": "CHARLIE",
                  "symbol": "AAPL",
                  "side": "BUY",
                  "type": "MARKET",
                  "quantity": 50
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tradeId").value("TRD-2"))
                .andExpect(jsonPath("$[0].quantity").value(50));
    }

    // -------------------------------------------------------------------------
    // 2. DTO Bean Validation Errors (HTTP 400 with FieldErrors)
    // -------------------------------------------------------------------------

    @Test
    void blankUserIdShouldReturn400BadRequest() throws Exception {
        String json = """
                {
                  "userId": "   ",
                  "symbol": "AAPL",
                  "side": "BUY",
                  "type": "LIMIT",
                  "price": 150.00,
                  "quantity": 100
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.fieldErrors.userId").value("User ID cannot be empty"));
    }

    @Test
    void blankSymbolShouldReturn400BadRequest() throws Exception {
        String json = """
                {
                  "userId": "ALICE",
                  "symbol": "",
                  "side": "BUY",
                  "type": "LIMIT",
                  "price": 150.00,
                  "quantity": 100
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.fieldErrors.symbol").value("Symbol cannot be empty"));
    }

    @Test
    void nullSideShouldReturn400BadRequest() throws Exception {
        String json = """
                {
                  "userId": "ALICE",
                  "symbol": "AAPL",
                  "type": "LIMIT",
                  "price": 150.00,
                  "quantity": 100
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.fieldErrors.side").value("Order side cannot be null"));
    }

    @Test
    void nullTypeShouldReturn400BadRequest() throws Exception {
        String json = """
                {
                  "userId": "ALICE",
                  "symbol": "AAPL",
                  "side": "BUY",
                  "price": 150.00,
                  "quantity": 100
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.fieldErrors.type").value("Order type cannot be null"));
    }

    @Test
    void zeroQuantityShouldReturn400BadRequest() throws Exception {
        String json = """
                {
                  "userId": "ALICE",
                  "symbol": "AAPL",
                  "side": "BUY",
                  "type": "LIMIT",
                  "price": 150.00,
                  "quantity": 0
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.fieldErrors.quantity").value("Quantity must be greater than zero"));
    }

    @Test
    void negativeQuantityShouldReturn400BadRequest() throws Exception {
        String json = """
                {
                  "userId": "ALICE",
                  "symbol": "AAPL",
                  "side": "BUY",
                  "type": "LIMIT",
                  "price": 150.00,
                  "quantity": -25
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.fieldErrors.quantity").value("Quantity must be greater than zero"));
    }

    @Test
    void limitOrderWithZeroPriceShouldReturn400BadRequest() throws Exception {
        String json = """
                {
                  "userId": "ALICE",
                  "symbol": "AAPL",
                  "side": "BUY",
                  "type": "LIMIT",
                  "price": 0.00,
                  "quantity": 100
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.fieldErrors.price").value("Limit order price must be greater than zero"));
    }

    @Test
    void limitOrderWithNegativePriceShouldReturn400BadRequest() throws Exception {
        String json = """
                {
                  "userId": "ALICE",
                  "symbol": "AAPL",
                  "side": "BUY",
                  "type": "LIMIT",
                  "price": -150.00,
                  "quantity": 100
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.fieldErrors.price").value("Limit order price must be greater than zero"));
    }

    // -------------------------------------------------------------------------
    // 3. Domain Business Rule Validation Failures (HTTP 400 via OrderValidator)
    // -------------------------------------------------------------------------

    @Test
    void limitOrderWithoutPriceShouldReturn400BadRequest() throws Exception {
        when(orderService.processOrder(any(OrderRequest.class)))
                .thenThrow(new IllegalArgumentException("Limit order must have a price"));

        String json = """
                {
                  "userId": "ALICE",
                  "symbol": "AAPL",
                  "side": "BUY",
                  "type": "LIMIT",
                  "quantity": 100
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Limit order must have a price"));
    }

    @Test
    void marketOrderWithPriceShouldReturn400BadRequest() throws Exception {
        when(orderService.processOrder(any(OrderRequest.class)))
                .thenThrow(new IllegalArgumentException("Market order should not have a price"));

        String json = """
                {
                  "userId": "ALICE",
                  "symbol": "AAPL",
                  "side": "BUY",
                  "type": "MARKET",
                  "price": 150.00,
                  "quantity": 100
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Market order should not have a price"));
    }

    @Test
    void malformedJsonOrInvalidEnumShouldReturn400BadRequest() throws Exception {
        String json = """
                {
                  "userId": "ALICE",
                  "symbol": "AAPL",
                  "side": "INVALID_SIDE",
                  "type": "LIMIT",
                  "price": 150.00,
                  "quantity": 100
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Malformed JSON request or invalid field value"));
    }
}
