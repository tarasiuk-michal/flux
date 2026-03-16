package com.flux.generator.service;

import com.flux.generator.model.MarketData;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class DataGenerator {

    private static final Random random = new Random();
    private static final Map<String, List<String>> symbolsByMarket = new HashMap<>();
    private static final Map<String, Map<String, Double>> basePrices = new HashMap<>();
    private static final List<String> markets;

    static {
        // Warsaw stock exchange
        symbolsByMarket.put("warsaw", List.of("PKO", "PKOBP", "ASSECOPOL", "GPWT", "KGHM", "PKNORLEN", "PGEPL", "TAURONPE"));
        basePrices.put("warsaw", Map.of(
            "PKO", 50.0,
            "PKOBP", 75.0,
            "ASSECOPOL", 100.0,
            "GPWT", 120.0,
            "KGHM", 180.0,
            "PKNORLEN", 95.0,
            "PGEPL", 140.0,
            "TAURONPE", 65.0
        ));

        // NYSE
        symbolsByMarket.put("nyse", List.of("AAPL", "MSFT", "TSLA", "GOOGL", "AMZN", "META", "NVDA", "JPM"));
        basePrices.put("nyse", Map.of(
            "AAPL", 180.0,
            "MSFT", 380.0,
            "TSLA", 250.0,
            "GOOGL", 140.0,
            "AMZN", 175.0,
            "META", 500.0,
            "NVDA", 870.0,
            "JPM", 190.0
        ));

        // TSE (Tokyo Stock Exchange)
        symbolsByMarket.put("tse", List.of("9984", "6758", "7203", "8031"));
        basePrices.put("tse", Map.of(
            "9984", 25000.0,  // SoftBank
            "6758", 18000.0,  // Sony
            "7203", 22000.0,  // Toyota
            "8031", 8000.0    // Mitsui
        ));

        // HKEX (Hong Kong)
        symbolsByMarket.put("hkex", List.of("700", "3988", "1000"));
        basePrices.put("hkex", Map.of(
            "700", 180.0,   // Tencent
            "3988", 8.5,    // Bank of China
            "1000", 25.0    // HK Property
        ));

        markets = List.of("warsaw", "nyse", "tse", "hkex");
    }

    public MarketData generatePayload() {
        String market = markets.get(random.nextInt(markets.size()));
        List<String> symbols = symbolsByMarket.get(market);
        String symbol = symbols.get(random.nextInt(symbols.size()));

        double basePrice = basePrices.get(market).get(symbol);
        double variance = (random.nextDouble() - 0.5) * 0.01 * basePrice; // ±0.5%
        double price = basePrice + variance;

        long volume = 100_000L + random.nextLong(9_900_000L); // 100k-10M

        return new MarketData(
            symbol,
            price,
            volume,
            Instant.now(),
            market
        );
    }
}
