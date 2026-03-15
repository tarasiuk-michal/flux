package com.flux.generator.service;

import com.flux.generator.model.RequestResult;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RequestExecutorTest {

    @Test
    void testRequestExecutorBeanCreation() {
        // Simple test to verify bean can be created
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
        assertEquals(202, result.httpStatus());
        assertEquals(150L, result.latencyMs());
    }

    @Test
    void testRequestResultWithError() {
        // Verify RequestResult can represent errors
        RequestResult result = new RequestResult(
            0,
            500L,
            Instant.now(),
            "TEST",
            "test-market",
            false,
            "CONNECTION"
        );

        assertFalse(result.success());
        assertEquals("CONNECTION", result.errorType());
    }
}
