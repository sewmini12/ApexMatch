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
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

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
}
