package com.flux.generator.service;

import com.flux.generator.model.MarketData;
import com.flux.generator.model.RequestResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class RequestExecutorTest {

    @Test
    void testRequestExecutorBeanCreation() {
        // Simple test to verify bean can be created
        // Full integration tests require WireMock which has dependency issues
        WebClient webClient = WebClient.create("http://localhost:8081");
        RequestExecutor executor = new RequestExecutor(webClient);
        assertNotNull(executor);
    }

    @Test
    void testRequestResultStructure() {
        // Verify RequestResult record can be created and accessed
        RequestResult result = new RequestResult(
            202,
            150L,
            Instant.now(),
            "TEST",
            "test-market",
            true,
            null
        );

        assertTrue(result.success());
        assertTrue(result.httpStatus() == 202);
        assertTrue(result.latencyMs() == 150L);
    }

    @Test
    void testErrorCategorization() {
        // Verify error categorization works for connection errors
        WebClient webClient = WebClient.create("http://localhost:9999"); // Non-existent port
        RequestExecutor executor = new RequestExecutor(webClient);

        MarketData data = new MarketData("TEST", 100.0, 1000000, Instant.now(), "test");

        StepVerifier.create(executor.sendPayload(data))
            .assertNext(result -> {
                assertNotNull(result);
                assertTrue(!result.success());
                assertNotNull(result.errorType());
            })
            .verifyComplete();
    }
}
