package com.flux.generator.service;

import com.flux.generator.model.LoadTestResult;
import com.flux.generator.model.RequestResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsAggregatorTest {

    private final MetricsAggregator aggregator = new MetricsAggregator();

    @Test
    void aggregateResults_emptyList_returnsEmptyResult() {
        LoadTestResult result = aggregator.aggregateResults("t1", List.of(), 10, 100);

        assertThat(result).isNotNull();
        assertThat(result.totalRequests).isEqualTo(0);
    }

    @Test
    void aggregateResults_withSuccessAndFailure_computesCorrectly() {
        List<RequestResult> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            results.add(new RequestResult(202, 10L + i, Instant.now(), "PKO", "warsaw", true, null));
        }
        results.add(new RequestResult(0, 500L, Instant.now(), "PKO", "warsaw", false, "TIMEOUT"));
        results.add(new RequestResult(0, 600L, Instant.now(), "PKO", "warsaw", false, "CONNECTION"));

        LoadTestResult result = aggregator.aggregateResults("t1", results, 10, 100);

        assertThat(result.totalRequests).isEqualTo(12);
        assertThat(result.successCount).isEqualTo(10);
        assertThat(result.failureCount).isEqualTo(2);
        assertThat(result.errorBreakdown).containsEntry("TIMEOUT", 1L);
        assertThat(result.errorBreakdown).containsEntry("CONNECTION", 1L);
    }

    @Test
    void aggregateResults_withZeroDuration_setsZeroRps() {
        List<RequestResult> results = List.of(
            new RequestResult(202, 10L, Instant.now(), "PKO", "warsaw", true, null)
        );

        LoadTestResult result = aggregator.aggregateResults("t1", results, 0, 100);

        assertThat(result.actualRps).isEqualTo(0.0);
    }

    @Test
    void aggregateResults_withDuration_computesActualRps() {
        List<RequestResult> results = List.of(
            new RequestResult(202, 10L, Instant.now(), "PKO", "warsaw", true, null),
            new RequestResult(202, 20L, Instant.now(), "PKO", "warsaw", true, null)
        );

        LoadTestResult result = aggregator.aggregateResults("t1", results, 2, 100);

        assertThat(result.actualRps).isEqualTo(1.0);
    }

    @Test
    void aggregateResults_failureWithNullErrorType_notAddedToBreakdown() {
        List<RequestResult> results = List.of(
            new RequestResult(0, 100L, Instant.now(), "PKO", "warsaw", false, null)
        );

        LoadTestResult result = aggregator.aggregateResults("t1", results, 1, 100);

        assertThat(result.errorBreakdown).isEmpty();
    }
}
