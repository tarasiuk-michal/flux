package com.flux.generator.model;

import jakarta.validation.constraints.Positive;

public record LoadTestRequest(
    @Positive int requestsPerSecond,
    @Positive int durationSeconds,
    @Positive int concurrency
) {}
