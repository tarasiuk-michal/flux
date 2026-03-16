package com.flux.generator.model;

import java.time.Instant;

public record MarketData(
    String symbol,
    double price,
    long volume,
    Instant timestamp,
    String market
) {}
