package com.flux.warehouse.dto;

public class DataDTO {
    private String symbol;
    private String market;
    private Double price;
    private Long volume;
    private String timestamp;

    public DataDTO(String symbol, String market, Double price, Long volume, String timestamp) {
        this.symbol = symbol;
        this.market = market;
        this.price = price;
        this.volume = volume;
        this.timestamp = timestamp;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getMarket() {
        return market;
    }

    public Double getPrice() {
        return price;
    }

    public Long getVolume() {
        return volume;
    }

    public String getTimestamp() {
        return timestamp;
    }
}
