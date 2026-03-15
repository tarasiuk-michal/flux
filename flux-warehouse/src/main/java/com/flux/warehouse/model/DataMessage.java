package com.flux.warehouse.model;

public class DataMessage {
    private String symbol;
    private Double price;
    private Long volume;
    private String timestamp;
    private String market;

    public DataMessage() {
    }

    public DataMessage(String symbol, Double price, Long volume, String timestamp, String market) {
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
