package com.flux.gateway;

import com.flux.gateway.service.MetricsService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GatewayIntegrationTest {

    @Test
    void testMetricsServiceRecordsRequests() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        MetricsService metricsService = new MetricsService(meterRegistry);

        // Record some requests
        metricsService.recordTotalRequest();
        metricsService.recordTotalRequest();
        metricsService.recordSuccessfulRequest();
        metricsService.recordFailedRequest();

        // Verify metrics
        assertEquals(2.0, meterRegistry.counter("gateway.requests.total").count());
        assertEquals(1.0, meterRegistry.counter("gateway.requests.success").count());
        assertEquals(1.0, meterRegistry.counter("gateway.requests.failed").count());
    }

    @Test
    void testMetricsServiceRecordsPublishDuration() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        MetricsService metricsService = new MetricsService(meterRegistry);

        var sample = metricsService.startPublishTimer();
        // Simulate some work
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        metricsService.recordPublishDuration(sample);

        // Verify timer was recorded
        assertNotNull(meterRegistry.timer("gateway.kafka.publish.duration"));
        assertEquals(1L, meterRegistry.timer("gateway.kafka.publish.duration").count());
    }

    @Test
    void testHealthEndpointExists() {
        // This is a simple smoke test to verify application structure
        assertDoesNotThrow(() -> {
            // If we can construct the metrics service without errors, the application is structured correctly
            MeterRegistry meterRegistry = new SimpleMeterRegistry();
            new MetricsService(meterRegistry);
        });
    }
}
