

package com.apexmatch.engine;

import com.apexmatch.model.Order;
import com.apexmatch.model.Trade;

public class MatchingEngine {

    private final OrderBook orderBook;

    public MatchingEngine(OrderBook orderBook) {
        this.orderBook = orderBook;
    }
    public Trade match() {

    Order bestBuy = orderBook.getBestBuy();
    Order bestSell = orderBook.getBestSell();

    if (bestBuy == null || bestSell == null) {
        return null;
    }

    if (!bestBuy.getSymbol().equals(bestSell.getSymbol())) {
        return null;
    }

    if (bestBuy.getPrice() < bestSell.getPrice()) {
        return null;
    }

    long tradeQuantity = Math.min(
            bestBuy.getQuantity(),
            bestSell.getQuantity()
    );

    bestBuy.reduceQuantity(tradeQuantity);
    bestSell.reduceQuantity(tradeQuantity);

    return new Trade(
            "TRD-001",
            bestBuy.getSymbol(),
            bestBuy.getUserId(),
            bestSell.getUserId(),
            bestSell.getPrice(),
            tradeQuantity
    );
}
}