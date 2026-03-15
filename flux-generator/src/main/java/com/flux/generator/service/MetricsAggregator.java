package com.flux.generator.service;

import com.flux.generator.model.LoadTestResult;
import com.flux.generator.model.RequestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MetricsAggregator {

    private static final Logger log = LoggerFactory.getLogger(MetricsAggregator.class);

    public LoadTestResult aggregateResults(
        String testId,
        List<RequestResult> results,
        long durationSeconds,
        int targetRps
    ) {
        LoadTestResult result = new LoadTestResult(testId);

        if (results.isEmpty()) {
            return result;
        }

        result.totalRequests = results.size();
        result.durationSeconds = durationSeconds;
        result.successCount = results.stream().filter(RequestResult::success).count();
        result.failureCount = result.totalRequests - result.successCount;
        result.successRate = (double) result.successCount / result.totalRequests * 100;
        result.actualRps = durationSeconds > 0 ? (double) result.totalRequests / durationSeconds : 0.0;

        // Calculate latency percentiles
        List<Long> latencies = new ArrayList<>();
        for (RequestResult r : results) {
            latencies.add(r.latencyMs());
        }
        Collections.sort(latencies);

        result.minLatencyMs = latencies.get(0);
        result.maxLatencyMs = latencies.get(latencies.size() - 1);
        result.avgLatencyMs = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        result.p50LatencyMs = calculatePercentile(latencies, 50);
        result.p95LatencyMs = calculatePercentile(latencies, 95);
        result.p99LatencyMs = calculatePercentile(latencies, 99);

        // Calculate error breakdown
        for (RequestResult r : results) {
            if (!r.success() && r.errorType() != null) {
                result.errorBreakdown.merge(r.errorType(), 1L, Long::sum);
            }
        }

        log.info("RPS: {}/{}, Success: {:.2f}%, P99: {:.2f}ms",
            String.format("%.0f", result.actualRps),
            targetRps,
            String.format("%.2f", result.successRate),
            String.format("%.2f", result.p99LatencyMs)
        );

        return result;
    }

    private double calculatePercentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil((percentile / 100.0) * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }
}
