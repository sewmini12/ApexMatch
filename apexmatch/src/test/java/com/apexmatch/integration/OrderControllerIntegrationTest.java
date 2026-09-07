package com.apexmatch.integration;

import com.apexmatch.engine.OrderBookManager;
import com.apexmatch.entity.TradeEntity;
import com.apexmatch.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
public class OrderControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private OrderBookManager orderBookManager;

    @BeforeEach
    void setUp() {
        tradeRepository.deleteAll();
        orderBookManager.clear();
    }

    @Test
    @DisplayName("End-to-end Controller -> MatchingEngine -> PostgreSQL integration test")
    void fullOrderLifecycleThroughHttpToPostgres() throws Exception {
        String buyJson = """
                {
                  "userId": "ALICE",
                  "symbol": "AAPL",
                  "side": "BUY",
                  "type": "LIMIT",
                  "price": 150.00,
                  "quantity": 50
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertThat(tradeRepository.count()).isEqualTo(0);

        String sellJson = """
                {
                  "userId": "BOB",
                  "symbol": "AAPL",
                  "side": "SELL",
                  "type": "LIMIT",
                  "price": 150.00,
                  "quantity": 50
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sellJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].buyerId").value("ALICE"))
                .andExpect(jsonPath("$[0].sellerId").value("BOB"))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].price").value(150.00))
                .andExpect(jsonPath("$[0].quantity").value(50));

        List<TradeEntity> persistedTrades = tradeRepository.findAll();
        assertThat(persistedTrades).hasSize(1);

        TradeEntity entity = persistedTrades.getFirst();
        assertThat(entity.getBuyer()).isEqualTo("ALICE");
        assertThat(entity.getSeller()).isEqualTo("BOB");
        assertThat(entity.getSymbol()).isEqualTo("AAPL");
        assertThat(entity.getPrice()).isEqualByComparingTo("150.00");
        assertThat(entity.getQuantity()).isEqualTo(50L);
        assertThat(entity.getExecutedAt()).isNotNull();
        assertThat(entity.getTradeId()).isNotBlank();
    }
}
