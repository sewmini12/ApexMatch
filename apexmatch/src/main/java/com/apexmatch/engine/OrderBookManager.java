package com.apexmatch.engine;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class OrderBookManager {

    private final ConcurrentMap<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ReentrantLock> symbolLocks = new ConcurrentHashMap<>();
    private final OrderBook defaultBook;

    public OrderBookManager() {
        this.defaultBook = null;
    }

    public OrderBookManager(OrderBook defaultBook) {
        this.defaultBook = defaultBook;
    }

    public static String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be empty");
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    public OrderBook getOrCreateOrderBook(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (defaultBook != null) {
            return orderBooks.computeIfAbsent(normalized, s -> defaultBook);
        }
        return orderBooks.computeIfAbsent(normalized, OrderBook::new);
    }

    public OrderBook getOrderBook(String symbol) {
        String normalized = normalizeSymbol(symbol);
        return orderBooks.get(normalized);
    }

    public Optional<OrderBook> findOrderBook(String symbol) {
        return Optional.ofNullable(getOrderBook(symbol));
    }

    public boolean hasOrderBook(String symbol) {
        String normalized = normalizeSymbol(symbol);
        return orderBooks.containsKey(normalized);
    }

    public int getActiveBookCount() {
        return orderBooks.size();
    }

    public Set<String> getActiveSymbols() {
        return Collections.unmodifiableSet(orderBooks.keySet());
    }

    public ReentrantLock getLockForSymbol(String symbol) {
        String normalized = normalizeSymbol(symbol);
        return symbolLocks.computeIfAbsent(normalized, s -> new ReentrantLock());
    }

    public OrderBook getDefaultBook() {
        return defaultBook;
    }

    public void clear() {
        orderBooks.clear();
        symbolLocks.clear();
    }
}
