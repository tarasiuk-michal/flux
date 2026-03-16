package com.flux.warehouse.controller;

import com.flux.warehouse.model.Company;
import com.flux.warehouse.model.Market;
import com.flux.warehouse.model.Price;
import com.flux.warehouse.repository.CompanyRepository;
import com.flux.warehouse.repository.MarketRepository;
import com.flux.warehouse.repository.PriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite:memory:test-db-query",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.kafka.bootstrap-servers=localhost:19092",
    "app.api-key=test-api-key-for-tests"
})
class QueryControllerTest {

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @Autowired
    private MarketRepository marketRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PriceRepository priceRepository;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();

        // Clear repositories
        priceRepository.deleteAll();
        companyRepository.deleteAll();
        marketRepository.deleteAll();

        // Create test market
        Market market = new Market("Warsaw Stock Exchange", "warsaw");
        market = marketRepository.save(market);

        // Create test company
        Company company = new Company("PKO", "PKO Bank Polski", market);
        company = companyRepository.save(company);

        // Create test prices
        for (int i = 1; i <= 3; i++) {
            Price price = new Price(company, 50.0 + i, (long) i * 1000, "2025-03-15T10:0" + i + ":00Z");
            priceRepository.save(price);
        }
    }

    @Test
    void testQueryWithMarketAndSymbol() {
        webTestClient.get()
            .uri("/api/query?market=warsaw&symbol=PKO&limit=10")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(Object.class).hasSize(3);
    }

    @Test
    void testQueryWithMarketOnly() {
        webTestClient.get()
            .uri("/api/query?market=warsaw")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(Object.class).hasSize(3);
    }

    @Test
    void testQueryWithUnknownMarket() {
        webTestClient.get()
            .uri("/api/query?market=fake")
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void testQueryWithValidMarketNoData() {
        // Create another market with no data
        Market market = new Market("NYSE", "nyse");
        marketRepository.save(market);

        webTestClient.get()
            .uri("/api/query?market=nyse")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(Object.class).hasSize(0);
    }

    @Test
    void testQueryMissingMarket() {
        webTestClient.get()
            .uri("/api/query")
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void testQueryWithLimit() {
        webTestClient.get()
            .uri("/api/query?market=warsaw&limit=2")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(Object.class).hasSize(2);
    }

    @Test
    void testQueryWithInvalidSymbol() {
        webTestClient.get()
            .uri("/api/query?market=warsaw&symbol=invalid!@#")
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void testQueryWithLimitExceedingMax() {
        webTestClient.get()
            .uri("/api/query?market=warsaw&limit=9999")
            .exchange()
            .expectStatus().isBadRequest();
    }
}
