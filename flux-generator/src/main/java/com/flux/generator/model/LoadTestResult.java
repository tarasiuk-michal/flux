package com.flux.generator.model;

import java.util.HashMap;
import java.util.Map;

public class LoadTestResult {
    public String testId;
    public long totalRequests;
    public long successCount;
    public long failureCount;
    public long durationSeconds;
    public double actualRps;
    public double minLatencyMs;
    public double maxLatencyMs;
    public double avgLatencyMs;
    public double p50LatencyMs;
    public double p95LatencyMs;
    public double p99LatencyMs;
    public double successRate;
    public Map<String, Long> errorBreakdown = new HashMap<>();

    public LoadTestResult(String testId) {
        this.testId = testId;
    }

    @Override
    public String toString() {
        return "LoadTestResult{" +
            "testId='" + testId + '\'' +
            ", totalRequests=" + totalRequests +
            ", successCount=" + successCount +
            ", failureCount=" + failureCount +
            ", durationSeconds=" + durationSeconds +
            ", actualRps=" + String.format("%.2f", actualRps) +
            ", p99LatencyMs=" + String.format("%.2f", p99LatencyMs) +
            ", successRate=" + String.format("%.2f", successRate) + "%" +
            '}';
    }
}
