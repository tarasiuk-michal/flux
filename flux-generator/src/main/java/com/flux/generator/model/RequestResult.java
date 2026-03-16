package com.flux.generator.model;

import java.time.Instant;

public record RequestResult(
    int httpStatus,
    long latencyMs,
    Instant timestamp,
    String symbol,
    String market,
    boolean success,
    String errorType  // null if success, otherwise: TIMEOUT, CONNECTION, 4XX, 5XX
) {}
