package com.flux.gateway.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {

    private final Counter totalRequests;
    private final Counter successfulRequests;
    private final Counter failedRequests;
    private final Timer publishDuration;

    public MetricsService(MeterRegistry meterRegistry) {
        this.totalRequests = Counter.builder("gateway.requests.total")
            .description("Total number of requests")
            .register(meterRegistry);

        this.successfulRequests = Counter.builder("gateway.requests.success")
            .description("Number of successful requests")
            .register(meterRegistry);

        this.failedRequests = Counter.builder("gateway.requests.failed")
            .description("Number of failed requests")
            .register(meterRegistry);

        this.publishDuration = Timer.builder("gateway.kafka.publish.duration")
            .description("Time taken to publish to Kafka")
            .register(meterRegistry);
    }

    public void recordTotalRequest() {
        totalRequests.increment();
    }

    public void recordSuccessfulRequest() {
        successfulRequests.increment();
    }

    public void recordFailedRequest() {
        failedRequests.increment();
    }

    public Timer.Sample startPublishTimer() {
        return Timer.start();
    }

    public void recordPublishDuration(Timer.Sample sample) {
        sample.stop(publishDuration);
    }
}
