package com.flux.gateway.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class DataPayload {

    @NotBlank(message = "symbol is required")
    private String symbol;

    @NotNull(message = "price is required")
    private Double price;

    @NotNull(message = "volume is required")
    private Long volume;

    @NotBlank(message = "timestamp is required")
    private String timestamp;

    @NotBlank(message = "market is required")
    private String market;

    public DataPayload() {
    }

    public DataPayload(String symbol, Double price, Long volume, String timestamp, String market) {
        this.symbol = symbol;
        this.price = price;
        this.volume = volume;
        this.timestamp = timestamp;
        this.market = market;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Long getVolume() {
        return volume;
    }

    public void setVolume(Long volume) {
        this.volume = volume;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }
}
