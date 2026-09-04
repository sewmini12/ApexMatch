package com.apexmatch.service;

import com.apexmatch.dto.OrderRequest;
import com.apexmatch.engine.MatchingEngine;
import com.apexmatch.entity.TradeEntity;
import com.apexmatch.model.Order;
import com.apexmatch.model.Trade;
import com.apexmatch.repository.TradeRepository;
import com.apexmatch.validation.OrderValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderService {

    private final MatchingEngine matchingEngine;
    private final TradeRepository tradeRepository;

    private final AtomicLong orderCounter = new AtomicLong(1);
    private final AtomicLong sequenceCounter = new AtomicLong(1);

    @Autowired
    public OrderService(MatchingEngine matchingEngine, TradeRepository tradeRepository) {
        this.matchingEngine = matchingEngine;
        this.tradeRepository = tradeRepository;
    }

    public OrderService(MatchingEngine matchingEngine) {
        this(matchingEngine, null);
    }

    public List<Trade> processOrder(OrderRequest request) {

        String orderId = "ORD-" + orderCounter.getAndIncrement();
        long sequenceNumber = sequenceCounter.getAndIncrement();

        Order order = new Order(
                orderId,
                request.getUserId(),
                request.getSymbol(),
                request.getSide(),
                request.getType(),
                request.getPrice(),
                request.getQuantity(),
                sequenceNumber
        );

        OrderValidator.validate(order);

        List<Trade> trades = matchingEngine.submitOrder(order);

        if (tradeRepository != null && !trades.isEmpty()) {
            List<TradeEntity> entities = trades.stream()
                    .map(trade -> new TradeEntity(
                            trade.getTradeId(),
                            trade.getSymbol(),
                            trade.getBuyerId(),
                            trade.getSellerId(),
                            trade.getPrice(),
                            trade.getQuantity(),
                            LocalDateTime.now()
                    ))
                    .toList();
            tradeRepository.saveAll(entities);
        }

        return trades;
    }
}