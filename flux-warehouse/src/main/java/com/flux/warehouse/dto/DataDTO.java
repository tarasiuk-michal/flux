package com.flux.warehouse.dto;

public record DataDTO(
    String symbol,
    String market,
    Double price,
    Long volume,
    String timestamp
) {}
