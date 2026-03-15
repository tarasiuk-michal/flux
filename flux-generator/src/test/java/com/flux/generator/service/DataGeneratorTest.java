package com.flux.generator.service;

import com.flux.generator.model.MarketData;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DataGeneratorTest {

    private static final List<String> VALID_WARSAW = List.of("PKO", "PKOBP", "ASSECOPOL", "GPWT", "KGHM", "PKNORLEN", "PGEPL", "TAURONPE");
    private static final List<String> VALID_NYSE = List.of("AAPL", "MSFT", "TSLA", "GOOGL", "AMZN", "META", "NVDA", "JPM");
    private static final List<String> VALID_TSE = List.of("9984", "6758", "7203", "8031");
    private static final List<String> VALID_HKEX = List.of("700", "3988", "1000");

    @Test
    void testGeneratesValidPayloads() {
        DataGenerator generator = new DataGenerator();

        for (int i = 0; i < 100; i++) {
            MarketData data = generator.generatePayload();

            assertNotNull(data.symbol());
            assertNotNull(data.market());
            assertNotNull(data.timestamp());
            assertTrue(data.price() > 0, "Price must be positive");
            assertTrue(data.volume() >= 100_000 && data.volume() <= 10_000_000, "Volume must be in range");
        }
    }

    @Test
    void testMarketSymbolMapping() {
        DataGenerator generator = new DataGenerator();

        Set<String> warsawSymbols = new HashSet<>();
        Set<String> nyseSymbols = new HashSet<>();
        Set<String> tseSymbols = new HashSet<>();
        Set<String> hkexSymbols = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            MarketData data = generator.generatePayload();

            switch (data.market()) {
                case "warsaw" -> {
                    assertTrue(VALID_WARSAW.contains(data.symbol()), "Invalid warsaw symbol: " + data.symbol());
                    warsawSymbols.add(data.symbol());
                }
                case "nyse" -> {
                    assertTrue(VALID_NYSE.contains(data.symbol()), "Invalid nyse symbol: " + data.symbol());
                    nyseSymbols.add(data.symbol());
                }
                case "tse" -> {
                    assertTrue(VALID_TSE.contains(data.symbol()), "Invalid tse symbol: " + data.symbol());
                    tseSymbols.add(data.symbol());
                }
                case "hkex" -> {
                    assertTrue(VALID_HKEX.contains(data.symbol()), "Invalid hkex symbol: " + data.symbol());
                    hkexSymbols.add(data.symbol());
                }
                default -> fail("Invalid market: " + data.market());
            }
        }

        // Verify we got diversity of symbols
        assertFalse(warsawSymbols.isEmpty(), "Should have generated warsaw symbols");
        assertFalse(nyseSymbols.isEmpty(), "Should have generated nyse symbols");
    }

    @Test
    void testGenerationSpeed() {
        DataGenerator generator = new DataGenerator();

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            generator.generatePayload();
        }
        long elapsedTime = System.currentTimeMillis() - startTime;

        assertTrue(elapsedTime < 100, "10000 generations should take < 100ms, took " + elapsedTime + "ms");
    }
}
