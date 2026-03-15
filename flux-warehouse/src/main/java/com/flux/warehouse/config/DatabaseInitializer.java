package com.flux.warehouse.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        log.info("Initializing database schema and seed data");

        // Set SQLite PRAGMAs
        jdbcTemplate.execute("PRAGMA journal_mode=WAL");
        jdbcTemplate.execute("PRAGMA synchronous=NORMAL");
        log.debug("PRAGMA settings applied");

        // Create market table
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS market (" +
            "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  name TEXT NOT NULL," +
            "  code TEXT NOT NULL UNIQUE," +
            "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")"
        );

        // Create company table
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS company (" +
            "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  symbol TEXT NOT NULL," +
            "  name TEXT," +
            "  market_id INTEGER NOT NULL," +
            "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "  FOREIGN KEY (market_id) REFERENCES market(id)," +
            "  UNIQUE(symbol, market_id)" +
            ")"
        );

        // Create prices table
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS prices (" +
            "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  company_id INTEGER NOT NULL," +
            "  price REAL NOT NULL," +
            "  volume BIGINT NOT NULL," +
            "  timestamp TEXT NOT NULL," +
            "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "  FOREIGN KEY (company_id) REFERENCES company(id)" +
            ")"
        );

        // Create indexes
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_prices_company_id ON prices(company_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_prices_timestamp ON prices(timestamp)");

        log.debug("Tables created successfully");

        // Seed markets
        seedMarkets();

        // Seed companies
        seedCompanies();

        log.info("Database initialization complete");
    }

    private void seedMarkets() {
        String[] markets = {
            "warsaw,Warsaw Stock Exchange",
            "nyse,New York Stock Exchange",
            "tse,Tokyo Stock Exchange",
            "hkex,Hong Kong Exchanges and Clearing"
        };

        for (String market : markets) {
            String[] parts = market.split(",");
            String code = parts[0];
            String name = parts[1];

            jdbcTemplate.update(
                "INSERT OR IGNORE INTO market (code, name, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                code, name
            );
        }
        log.debug("Markets seeded");
    }

    private void seedCompanies() {
        String[][] companies = {
            {"warsaw", "PKO", "PKO Bank Polski"},
            {"warsaw", "PKOBP", "PKO Bank Polski"},
            {"warsaw", "ASSECOPOL", "Asseco Poland"},
            {"warsaw", "GPWT", "GPW Trgoviste"},
            {"warsaw", "KGHM", "KGHM Polska Miedź"},
            {"warsaw", "PKNORLEN", "PKN Orlen"},
            {"warsaw", "PGEPL", "PGE"},
            {"warsaw", "TAURONPE", "Tauron Polska Energia"},
            {"nyse", "AAPL", "Apple Inc."},
            {"nyse", "MSFT", "Microsoft Corporation"},
            {"nyse", "TSLA", "Tesla Inc."},
            {"nyse", "GOOGL", "Alphabet Inc."},
            {"nyse", "AMZN", "Amazon.com Inc."},
            {"nyse", "META", "Meta Platforms Inc."},
            {"nyse", "NVDA", "NVIDIA Corporation"},
            {"nyse", "JPM", "JPMorgan Chase & Co."},
            {"tse", "9984", "SoftBank Group"},
            {"tse", "6758", "Sony Group Corporation"},
            {"tse", "7203", "Toyota Motor"},
            {"tse", "8031", "Mitsui & Co."},
            {"hkex", "700", "Tencent Holdings"},
            {"hkex", "3988", "Bank of China"},
            {"hkex", "1000", "HK Property Development"}
        };

        for (String[] company : companies) {
            String marketCode = company[0];
            String symbol = company[1];
            String name = company[2];

            jdbcTemplate.update(
                "INSERT OR IGNORE INTO company (symbol, name, market_id, created_at) " +
                "SELECT ?, ?, id, CURRENT_TIMESTAMP FROM market WHERE code = ?",
                symbol, name, marketCode
            );
        }
        log.debug("Companies seeded");
    }
}
