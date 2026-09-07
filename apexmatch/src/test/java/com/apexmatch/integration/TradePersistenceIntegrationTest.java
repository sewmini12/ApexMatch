package com.apexmatch.integration;

import com.apexmatch.dto.OrderRequest;
import com.apexmatch.engine.OrderBookManager;
import com.apexmatch.entity.TradeEntity;
import com.apexmatch.model.OrderSide;
import com.apexmatch.model.OrderType;
import com.apexmatch.model.Trade;
import com.apexmatch.repository.TradeRepository;
import com.apexmatch.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
public class TradePersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrderService orderService;

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
    @DisplayName("Test 1 — Trade persistence: Submit matching orders and verify persisted TradeEntity fields")
    void test1_TradePersistence() {
        OrderRequest buyOrder = new OrderRequest("ALICE", "AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("150.00"), 100);
        OrderRequest sellOrder = new OrderRequest("BOB", "AAPL", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("150.00"), 100);

        List<Trade> restingTrades = orderService.processOrder(buyOrder);
        assertThat(restingTrades).isEmpty();
        assertThat(tradeRepository.count()).isEqualTo(0);

        List<Trade> executedTrades = orderService.processOrder(sellOrder);
        assertThat(executedTrades).hasSize(1);

        Trade memoryTrade = executedTrades.getFirst();
        assertThat(memoryTrade.getBuyerId()).isEqualTo("ALICE");
        assertThat(memoryTrade.getSellerId()).isEqualTo("BOB");
        assertThat(memoryTrade.getSymbol()).isEqualTo("AAPL");
        assertThat(memoryTrade.getPrice()).isEqualByComparingTo("150.00");
        assertThat(memoryTrade.getQuantity()).isEqualTo(100);

        List<TradeEntity> persistedTrades = tradeRepository.findAll();
        assertThat(persistedTrades).hasSize(1);

        TradeEntity entity = persistedTrades.getFirst();
        assertThat(entity.getTradeId()).isEqualTo(memoryTrade.getTradeId());
        assertThat(entity.getSymbol()).isEqualTo("AAPL");
        assertThat(entity.getBuyer()).isEqualTo("ALICE");
        assertThat(entity.getSeller()).isEqualTo("BOB");
        assertThat(entity.getPrice()).isEqualByComparingTo("150.00");
        assertThat(entity.getQuantity()).isEqualTo(100L);
        assertThat(entity.getExecutedAt()).isNotNull();

        assertThat(tradeRepository.findByTradeId(memoryTrade.getTradeId())).isPresent();
    }

    @Test
    @DisplayName("Test 2 — Partial fill persistence: Verify correct trade quantity on partial match")
    void test2_PartialFillPersistence() {
        OrderRequest buyOrder = new OrderRequest("ALICE", "AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("150.00"), 100);
        OrderRequest sellOrder = new OrderRequest("BOB", "AAPL", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("150.00"), 40);

        orderService.processOrder(buyOrder);
        List<Trade> trades = orderService.processOrder(sellOrder);

        assertThat(trades).hasSize(1);
        assertThat(trades.getFirst().getQuantity()).isEqualTo(40);

        List<TradeEntity> persistedTrades = tradeRepository.findAll();
        assertThat(persistedTrades).hasSize(1);
        TradeEntity entity = persistedTrades.getFirst();
        assertThat(entity.getQuantity()).isEqualTo(40L);
        assertThat(entity.getPrice()).isEqualByComparingTo("150.00");

        assertThat(orderBookManager.getOrderBook("AAPL").getBuyOrderCount()).isEqualTo(1);
        assertThat(orderBookManager.getOrderBook("AAPL").getBestBuy().getPrice()).isEqualByComparingTo("150.00");
        assertThat(orderBookManager.getOrderBook("AAPL").getBestBuy().getQuantity()).isEqualTo(60L);
    }

    @Test
    @DisplayName("Test 3 — Multiple trades persistence: Order matching multiple resting orders persists all trades")
    void test3_MultipleTradesPersistence() {
        OrderRequest sell1 = new OrderRequest("BOB", "AAPL", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("148.00"), 40);
        OrderRequest sell2 = new OrderRequest("CHARLIE", "AAPL", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("149.00"), 30);
        OrderRequest buy = new OrderRequest("ALICE", "AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("150.00"), 100);

        orderService.processOrder(sell1);
        orderService.processOrder(sell2);
        assertThat(tradeRepository.count()).isEqualTo(0);

        List<Trade> trades = orderService.processOrder(buy);
        assertThat(trades).hasSize(2);

        List<TradeEntity> persistedTrades = tradeRepository.findAll();
        assertThat(persistedTrades).hasSize(2);

        TradeEntity firstPersisted = persistedTrades.stream()
                .filter(t -> t.getSeller().equals("BOB"))
                .findFirst().orElseThrow();
        assertThat(firstPersisted.getQuantity()).isEqualTo(40L);
        assertThat(firstPersisted.getPrice()).isEqualByComparingTo("148.00");

        TradeEntity secondPersisted = persistedTrades.stream()
                .filter(t -> t.getSeller().equals("CHARLIE"))
                .findFirst().orElseThrow();
        assertThat(secondPersisted.getQuantity()).isEqualTo(30L);
        assertThat(secondPersisted.getPrice()).isEqualByComparingTo("149.00");

        long totalQuantity = persistedTrades.stream().mapToLong(TradeEntity::getQuantity).sum();
        assertThat(totalQuantity).isEqualTo(70L);
    }

    @Test
    @DisplayName("Test 4 — Multi-symbol isolation: Cross-symbol orders never match; trades contain correct symbol")
    void test4_MultiSymbolIsolation() {
        OrderRequest buyAapl = new OrderRequest("ALICE", "AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("150.00"), 100);
        OrderRequest sellGoog = new OrderRequest("BOB", "GOOG", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("150.00"), 100);
        OrderRequest sellTsla = new OrderRequest("CHARLIE", "TSLA", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("150.00"), 100);
        OrderRequest sellAapl = new OrderRequest("DAVE", "AAPL", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("150.00"), 100);

        orderService.processOrder(buyAapl);
        orderService.processOrder(sellGoog);
        orderService.processOrder(sellTsla);

        assertThat(tradeRepository.count()).isEqualTo(0);

        List<Trade> aaplTrades = orderService.processOrder(sellAapl);
        assertThat(aaplTrades).hasSize(1);
        assertThat(aaplTrades.getFirst().getSymbol()).isEqualTo("AAPL");

        List<TradeEntity> aaplEntities = tradeRepository.findBySymbol("AAPL");
        assertThat(aaplEntities).hasSize(1);
        assertThat(aaplEntities.getFirst().getBuyer()).isEqualTo("ALICE");
        assertThat(aaplEntities.getFirst().getSeller()).isEqualTo("DAVE");

        assertThat(tradeRepository.findBySymbol("GOOG")).isEmpty();
        assertThat(tradeRepository.findBySymbol("TSLA")).isEmpty();
    }

    @Test
    @DisplayName("Test 5 — Transaction behavior: Service transaction atomically persists all trades from single order")
    void test5_TransactionBehavior() {
        OrderRequest sell1 = new OrderRequest("SELLER-1", "MSFT", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("400.00"), 50);
        OrderRequest sell2 = new OrderRequest("SELLER-2", "MSFT", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("405.00"), 50);
        OrderRequest buyAll = new OrderRequest("BUYER-1", "MSFT", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("410.00"), 100);

        orderService.processOrder(sell1);
        orderService.processOrder(sell2);

        List<Trade> execution = orderService.processOrder(buyAll);
        assertThat(execution).hasSize(2);

        List<TradeEntity> msftTrades = tradeRepository.findBySymbol("MSFT");
        assertThat(msftTrades).hasSize(2);
        assertThat(msftTrades).extracting(TradeEntity::getBuyer).containsOnly("BUYER-1");
        assertThat(msftTrades).extracting(TradeEntity::getSeller).containsExactlyInAnyOrder("SELLER-1", "SELLER-2");
    }
}
