package com.flux.generator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class LoadTestService {

    private static final Logger log = LoggerFactory.getLogger(LoadTestService.class);
    private final DataGenerator generator;
    private final RequestExecutor executor;
    private final ConcurrentHashMap<String, TestState> activeTests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TestState> completedTests = new ConcurrentHashMap<>();

    public LoadTestService(DataGenerator generator, RequestExecutor executor) {
        this.generator = generator;
        this.executor = executor;
    }

    public String startLoadTest(int requestsPerSecond, int durationSeconds, int concurrency) {
        String testId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();

        TestState state = new TestState(testId, startTime, requestsPerSecond, durationSeconds);
        activeTests.put(testId, state);

        // Calculate interval in nanoseconds for accurate rate control
        long intervalNanos = 1_000_000_000L / requestsPerSecond;

        // Run the load test asynchronously
        Disposable disposable = Flux.interval(Duration.ofNanos(intervalNanos))
            .take((long) requestsPerSecond * durationSeconds)
            .onBackpressureDrop(dropped ->
                log.warn("Load test {} backpressure: tick dropped", testId))
            .flatMap(
                i -> executor.sendPayload(generator.generatePayload()),
                concurrency
            )
            .doOnNext(result -> {
                state.totalRequests.incrementAndGet();
                if (result.success()) {
                    state.successRequests.incrementAndGet();
                } else {
                    state.failureRequests.incrementAndGet();
                }
            })
            .doOnError(e -> log.warn("Load test {} encountered error", testId, e))
            .doFinally(signalType -> {
                state.status = "COMPLETED";
                state.endTime = Instant.now();
                activeTests.remove(testId);
                completedTests.put(testId, state);
                log.info("Load test {} completed: {} total, {} success, {} failure",
                    testId, state.totalRequests.get(), state.successRequests.get(), state.failureRequests.get());
            })
            .subscribe(
                result -> {},
                error -> log.error("Load test {} failed", testId, error)
            );

        state.disposable = disposable;
        return testId;
    }

    public TestState getTestStatus(String testId) {
        return activeTests.getOrDefault(testId, completedTests.get(testId));
    }

    public TestState stopTest(String testId) {
        TestState state = activeTests.remove(testId);
        if (state != null) {
            if (state.disposable != null && !state.disposable.isDisposed()) {
                state.disposable.dispose();
            }
            state.status = "STOPPED";
            state.endTime = Instant.now();
            completedTests.put(testId, state);
        }
        return state;
    }

    public TestState getTestResults(String testId) {
        return completedTests.get(testId);
    }

    public static class TestState {
        public String testId;
        public String status = "RUNNING";
        public Instant startTime;
        public Instant endTime;
        public AtomicLong totalRequests = new AtomicLong(0);
        public AtomicLong successRequests = new AtomicLong(0);
        public AtomicLong failureRequests = new AtomicLong(0);
        public int targetRps;
        public int durationSeconds;
        public transient Disposable disposable;

        public TestState(String testId, Instant startTime, int targetRps, int durationSeconds) {
            this.testId = testId;
            this.startTime = startTime;
            this.targetRps = targetRps;
            this.durationSeconds = durationSeconds;
        }

        public long getElapsedSeconds() {
            Instant end = endTime != null ? endTime : Instant.now();
            return Duration.between(startTime, end).toSeconds();
        }

        public double getActualRps() {
            long elapsed = getElapsedSeconds();
            return elapsed > 0 ? (double) totalRequests.get() / elapsed : 0.0;
        }

        public double getSuccessRate() {
            long total = totalRequests.get();
            return total > 0 ? (double) successRequests.get() / total * 100 : 0.0;
        }
    }
}
