

package com.apexmatch.engine;

import com.apexmatch.model.Order;
import com.apexmatch.model.Trade;
import java.util.ArrayList;
import java.util.List;

public class MatchingEngine {

    private final OrderBook orderBook;

    public MatchingEngine(OrderBook orderBook) {
        this.orderBook = orderBook;
    }
    
    public List<Trade> match() {

    List<Trade> trades = new ArrayList<>();

    while (true) {

        Order bestBuy = orderBook.getBestBuy();
        Order bestSell = orderBook.getBestSell();

        // No orders on one side
        if (bestBuy == null || bestSell == null) {
            break;
        }

        // Different stocks cannot match
        if (!bestBuy.getSymbol().equals(bestSell.getSymbol())) {
            break;
        }

        // Prices don't overlap
        if (bestBuy.getPrice() < bestSell.getPrice()) {
            break;
        }

        // Determine how many shares can be traded
        long tradeQuantity = Math.min(
                bestBuy.getQuantity(),
                bestSell.getQuantity()
        );

        // Reduce quantities
        bestBuy.reduceQuantity(tradeQuantity);
        bestSell.reduceQuantity(tradeQuantity);

        // Create trade
        Trade trade = new Trade(
                "TRD-" + (trades.size() + 1),
                bestBuy.getSymbol(),
                bestBuy.getUserId(),
                bestSell.getUserId(),
                bestSell.getPrice(),
                tradeQuantity
        );

        trades.add(trade);

        // Remove completely filled orders
        if (bestBuy.getQuantity() == 0) {
            orderBook.removeBestBuy();
        }

        if (bestSell.getQuantity() == 0) {
            orderBook.removeBestSell();
        }
    }

    return trades;
}
}