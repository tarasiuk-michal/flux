package com.flux.gateway.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataPayloadTest {

    @Test
    void constructorAndGetters_setAllFields() {
        DataPayload payload = new DataPayload("PKO", 50.0, 1000L, "2025-03-15T10:00:00Z", "warsaw");

        assertThat(payload.getSymbol()).isEqualTo("PKO");
        assertThat(payload.getPrice()).isEqualTo(50.0);
        assertThat(payload.getVolume()).isEqualTo(1000L);
        assertThat(payload.getTimestamp()).isEqualTo("2025-03-15T10:00:00Z");
        assertThat(payload.getMarket()).isEqualTo("warsaw");
    }

    @Test
    void defaultConstructorAndSetters_setAllFields() {
        DataPayload payload = new DataPayload();
        payload.setSymbol("AAPL");
        payload.setPrice(150.0);
        payload.setVolume(5000L);
        payload.setTimestamp("2025-03-15T12:00:00Z");
        payload.setMarket("nasdaq");

        assertThat(payload.getSymbol()).isEqualTo("AAPL");
        assertThat(payload.getPrice()).isEqualTo(150.0);
        assertThat(payload.getVolume()).isEqualTo(5000L);
        assertThat(payload.getTimestamp()).isEqualTo("2025-03-15T12:00:00Z");
        assertThat(payload.getMarket()).isEqualTo("nasdaq");
    }

    @Test
    void defaultConstructor_fieldsAreNull() {
        DataPayload payload = new DataPayload();

        assertThat(payload.getSymbol()).isNull();
        assertThat(payload.getPrice()).isNull();
        assertThat(payload.getVolume()).isNull();
        assertThat(payload.getTimestamp()).isNull();
        assertThat(payload.getMarket()).isNull();
    }
}
