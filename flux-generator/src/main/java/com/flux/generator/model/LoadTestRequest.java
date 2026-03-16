package com.flux.generator.model;

public record LoadTestRequest(
    int requestsPerSecond,
    int durationSeconds,
    int concurrency
) {}
