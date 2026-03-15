package com.flux.warehouse.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite:memory:test-db",
    "spring.jpa.hibernate.ddl-auto=none"
})
class DatabaseInitializerTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // Schema will be created by DatabaseInitializer @PostConstruct
    }

    @Test
    void testMarketTableExists() {
        String query = "SELECT name FROM sqlite_master WHERE type='table' AND name='market'";
        var result = jdbcTemplate.queryForList(query);
        assertFalse(result.isEmpty(), "Market table should exist");
    }

    @Test
    void testCompanyTableExists() {
        String query = "SELECT name FROM sqlite_master WHERE type='table' AND name='company'";
        var result = jdbcTemplate.queryForList(query);
        assertFalse(result.isEmpty(), "Company table should exist");
    }

    @Test
    void testPricesTableExists() {
        String query = "SELECT name FROM sqlite_master WHERE type='table' AND name='prices'";
        var result = jdbcTemplate.queryForList(query);
        assertFalse(result.isEmpty(), "Prices table should exist");
    }

    @Test
    void testMarketSeedData() {
        String query = "SELECT COUNT(*) FROM market";
        Integer count = jdbcTemplate.queryForObject(query, Integer.class);
        assertEquals(4, count, "Should have 4 markets seeded");
    }

    @Test
    void testCompanySeedData() {
        String query = "SELECT COUNT(*) FROM company";
        Integer count = jdbcTemplate.queryForObject(query, Integer.class);
        assertEquals(23, count, "Should have 23 companies seeded");
    }

    @Test
    void testMarketCodesPresent() {
        String query = "SELECT code FROM market ORDER BY code";
        var codes = jdbcTemplate.queryForList(query, String.class);
        assertTrue(codes.contains("warsaw"));
        assertTrue(codes.contains("nyse"));
        assertTrue(codes.contains("tse"));
        assertTrue(codes.contains("hkex"));
    }

    @Test
    void testPragmaJournalMode() {
        String query = "PRAGMA journal_mode";
        String journalMode = jdbcTemplate.queryForObject(query, String.class);
        assertEquals("wal", journalMode, "Journal mode should be WAL");
    }

    @Test
    void testIndexesExist() {
        String query = "SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'idx_prices%'";
        var indexes = jdbcTemplate.queryForList(query);
        assertEquals(2, indexes.size(), "Should have 2 indexes on prices table");
    }

    @Test
    void testIdempotency() {
        // Run initialization again (manually)
        new DatabaseInitializer(jdbcTemplate).initialize();

        // Counts should remain the same
        Integer marketCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM market", Integer.class);
        Integer companyCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM company", Integer.class);

        assertEquals(4, marketCount, "Market count should remain 4 after second init");
        assertEquals(23, companyCount, "Company count should remain 23 after second init");
    }
}
