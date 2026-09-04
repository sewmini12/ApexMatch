package com.apexmatch.repository;

import com.apexmatch.entity.TradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TradeRepository extends JpaRepository<TradeEntity, Long> {

    Optional<TradeEntity> findByTradeId(String tradeId);

    List<TradeEntity> findBySymbol(String symbol);
}
