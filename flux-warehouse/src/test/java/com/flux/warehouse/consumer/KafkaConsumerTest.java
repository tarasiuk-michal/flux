package com.flux.warehouse.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite:memory:test-db-kafka",
    "spring.jpa.hibernate.ddl-auto=none"
})
class KafkaConsumerTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private MarketRepository marketRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PriceRepository priceRepository;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Clear repositories
        priceRepository.deleteAll();
        companyRepository.deleteAll();
        marketRepository.deleteAll();

        // Create test market and company
        Market market = new Market("Warsaw Stock Exchange", "warsaw");
        market = marketRepository.save(market);

        Company company = new Company("PKO", "PKO Bank Polski", market);
        companyRepository.save(company);
    }

    @Test
    void testValidMessageIsConsumedAndSaved() throws JsonProcessingException, InterruptedException {
        String messageJson = objectMapper.writeValueAsString(
            new DataMessage("PKO", 50.0, 1000L, "2025-03-15T10:00:00Z", "warsaw")
        );

        kafkaTemplate.send("data-stream", messageJson);
        kafkaTemplate.flush();

        // Wait for message to be processed
        Thread.sleep(2000);

        // Verify price was saved
        List<Price> prices = priceRepository.findAll();
        assertFalse(prices.isEmpty(), "Price record should be saved");
        assertEquals(1, prices.size());
        assertEquals(50.0, prices.get(0).getPrice());
    }

    @Test
    void testMalformedMessageIsSkipped() throws InterruptedException {
        String malformedJson = "not json at all";

        kafkaTemplate.send("data-stream", malformedJson);
        kafkaTemplate.flush();

        // Wait for message to be processed
        Thread.sleep(2000);

        // Verify nothing was saved and app is still running (no crash)
        List<Price> prices = priceRepository.findAll();
        assertEquals(0, prices.size(), "No price should be saved for malformed message");
    }

    @Test
    void testUnknownSymbolIsSkipped() throws JsonProcessingException, InterruptedException {
        String messageJson = objectMapper.writeValueAsString(
            new DataMessage("UNKNOWN", 50.0, 1000L, "2025-03-15T10:00:00Z", "warsaw")
        );

        kafkaTemplate.send("data-stream", messageJson);
        kafkaTemplate.flush();

        // Wait for message to be processed
        Thread.sleep(2000);

        // Verify nothing was saved
        List<Price> prices = priceRepository.findAll();
        assertEquals(0, prices.size(), "No price should be saved for unknown symbol");
    }

    // Inner class for JSON serialization in tests
    public static class DataMessage {
        public String symbol;
        public Double price;
        public Long volume;
        public String timestamp;
        public String market;

        public DataMessage(String symbol, Double price, Long volume, String timestamp, String market) {
            this.symbol = symbol;
            this.price = price;
            this.volume = volume;
            this.timestamp = timestamp;
            this.market = market;
        }
    }
}
