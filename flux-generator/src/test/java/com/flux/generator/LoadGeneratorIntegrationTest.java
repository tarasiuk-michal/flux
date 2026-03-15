package com.flux.generator;

import com.flux.generator.model.LoadTestResult;
import com.flux.generator.model.MarketData;
import com.flux.generator.service.DataGenerator;
import com.flux.generator.service.LoadTestService;
import com.flux.generator.service.MetricsAggregator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LoadGeneratorIntegrationTest {

    @Autowired
    private DataGenerator dataGenerator;

    @Autowired
    private LoadTestService loadTestService;

    @Autowired
    private MetricsAggregator metricsAggregator;

    @Autowired
    private WebClient webClient;

    @Test
    void testDataGeneratorWorks() {
        assertNotNull(dataGenerator);
        MarketData data = dataGenerator.generatePayload();
        assertNotNull(data);
        assertNotNull(data.symbol());
        assertNotNull(data.market());
        assertTrue(data.price() > 0);
        assertTrue(data.volume() > 0);
    }

    @Test
    void testLoadTestServiceBeanCreation() {
        assertNotNull(loadTestService);
    }

    @Test
    void testMetricsAggregatorCalculatesPercentiles() {
        assertNotNull(metricsAggregator);

        // Create mock RequestResult objects
        List<com.flux.generator.model.RequestResult> results = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            results.add(new com.flux.generator.model.RequestResult(
                202,
                10L + i,  // latencies from 10 to 109 ms
                Instant.now(),
                "TEST",
                "test-market",
                true,
                null
            ));
        }

        LoadTestResult result = metricsAggregator.aggregateResults("test-id", results, 10, 100);

        assertNotNull(result);
        assertEquals(100, result.totalRequests);
        assertEquals(100, result.successCount);
        assertEquals(0, result.failureCount);
        assertTrue(result.p99LatencyMs > 0);
        assertTrue(result.p95LatencyMs > 0);
        assertTrue(result.p50LatencyMs > 0);
        assertTrue(result.p99LatencyMs >= result.p95LatencyMs);
        assertTrue(result.p95LatencyMs >= result.p50LatencyMs);
    }

    @Test
    void testWebClientBeanCreation() {
        assertNotNull(webClient);
    }

    @Test
    void testApplicationStartsSuccessfully() {
        // If we get here, the application started successfully
        assertTrue(true);
    }
}
