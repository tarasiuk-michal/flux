package com.flux.warehouse.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void incrementConsumed() {
        meterRegistry.counter("warehouse.records.consumed").increment();
    }

    public void incrementSaved() {
        meterRegistry.counter("warehouse.records.saved").increment();
    }

    public void incrementFailed() {
        meterRegistry.counter("warehouse.records.failed").increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopTimer(Timer.Sample sample) {
        sample.stop(Timer.builder("warehouse.save.duration").register(meterRegistry));
    }
}
