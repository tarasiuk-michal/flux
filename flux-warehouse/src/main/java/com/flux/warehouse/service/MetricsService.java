package com.flux.warehouse.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {

    private final Counter consumedCounter;
    private final Counter savedCounter;
    private final Counter failedCounter;
    private final Timer saveDuration;

    public MetricsService(MeterRegistry meterRegistry) {
        this.consumedCounter = Counter.builder("warehouse.records.consumed")
            .description("Records consumed from Kafka")
            .register(meterRegistry);
        this.savedCounter = Counter.builder("warehouse.records.saved")
            .description("Records saved to database")
            .register(meterRegistry);
        this.failedCounter = Counter.builder("warehouse.records.failed")
            .description("Records that failed processing")
            .register(meterRegistry);
        this.saveDuration = Timer.builder("warehouse.save.duration")
            .description("Time taken to save a record")
            .register(meterRegistry);
    }

    public void incrementConsumed() {
        consumedCounter.increment();
    }

    public void incrementSaved() {
        savedCounter.increment();
    }

    public void incrementFailed() {
        failedCounter.increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start();
    }

    public void stopTimer(Timer.Sample sample) {
        sample.stop(saveDuration);
    }
}
