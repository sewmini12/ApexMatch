package com.apexmatch.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

public class OrderBookManagerTest {

    private OrderBookManager manager;

    @BeforeEach
    void setUp() {
        manager = new OrderBookManager();
    }

    @Test
    void normalizeSymbolShouldHandleWhitespaceAndLowercase() {
        assertEquals("AAPL", OrderBookManager.normalizeSymbol("aapl"));
        assertEquals("AAPL", OrderBookManager.normalizeSymbol(" AAPL "));
        assertEquals("GOOG", OrderBookManager.normalizeSymbol("  goog  "));
        assertEquals("TSLA", OrderBookManager.normalizeSymbol("tsla"));
    }

    @Test
    void normalizeSymbolShouldThrowOnNullOrBlank() {
        assertThrows(IllegalArgumentException.class, () -> OrderBookManager.normalizeSymbol(null));
        assertThrows(IllegalArgumentException.class, () -> OrderBookManager.normalizeSymbol(""));
        assertThrows(IllegalArgumentException.class, () -> OrderBookManager.normalizeSymbol("   "));
    }

    @Test
    void getOrCreateOrderBookShouldCreateNewBooksAndNormalizeKeys() {
        assertEquals(0, manager.getActiveBookCount());
        assertFalse(manager.hasOrderBook("AAPL"));

        OrderBook book1 = manager.getOrCreateOrderBook("aapl");
        assertNotNull(book1);
        assertEquals(1, manager.getActiveBookCount());
        assertTrue(manager.hasOrderBook("AAPL"));
        assertTrue(manager.hasOrderBook("aapl"));

        // Case-insensitive retrieval maps to the exact same instance
        OrderBook book2 = manager.getOrCreateOrderBook("AAPL");
        assertSame(book1, book2);

        OrderBook bookGoog = manager.getOrCreateOrderBook("GOOG");
        assertNotSame(book1, bookGoog);
        assertEquals(2, manager.getActiveBookCount());
    }

    @Test
    void getOrderBookAndFindOrderBook() {
        assertNull(manager.getOrderBook("TSLA"));
        assertEquals(Optional.empty(), manager.findOrderBook("TSLA"));

        manager.getOrCreateOrderBook("TSLA");

        assertNotNull(manager.getOrderBook("tsla"));
        assertTrue(manager.findOrderBook("tsla").isPresent());
    }

    @Test
    void getActiveSymbolsShouldReturnAllRegisteredSymbols() {
        manager.getOrCreateOrderBook("AAPL");
        manager.getOrCreateOrderBook("GOOG");
        manager.getOrCreateOrderBook("TSLA");

        Set<String> symbols = manager.getActiveSymbols();
        assertEquals(3, symbols.size());
        assertTrue(symbols.contains("AAPL"));
        assertTrue(symbols.contains("GOOG"));
        assertTrue(symbols.contains("TSLA"));
    }

    @Test
    void getLockForSymbolShouldReturnDedicatedLocks() {
        ReentrantLock lockAapl = manager.getLockForSymbol("aapl");
        ReentrantLock lockAapl2 = manager.getLockForSymbol("AAPL");
        ReentrantLock lockGoog = manager.getLockForSymbol("GOOG");

        assertNotNull(lockAapl);
        assertNotNull(lockGoog);
        assertSame(lockAapl, lockAapl2, "Same symbol must return identical lock");
        assertNotSame(lockAapl, lockGoog, "Different symbols must have independent locks");
    }

    @Test
    void clearShouldResetAllBooksAndLocks() {
        manager.getOrCreateOrderBook("AAPL");
        manager.getOrCreateOrderBook("GOOG");
        assertEquals(2, manager.getActiveBookCount());

        manager.clear();
        assertEquals(0, manager.getActiveBookCount());
        assertFalse(manager.hasOrderBook("AAPL"));
        assertFalse(manager.hasOrderBook("GOOG"));
    }
}
