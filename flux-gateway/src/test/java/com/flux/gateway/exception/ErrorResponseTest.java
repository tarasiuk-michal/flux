package com.flux.gateway.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    @Test
    void constructor_setsAllFieldsCorrectly() {
        ErrorResponse response = new ErrorResponse(401, "Unauthorized", "corr-123");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getMessage()).isEqualTo("Unauthorized");
        assertThat(response.getCorrelationId()).isEqualTo("corr-123");
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    void constructor_withNullCorrelationId() {
        ErrorResponse response = new ErrorResponse(500, "Internal error", null);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).isEqualTo("Internal error");
        assertThat(response.getCorrelationId()).isNull();
        assertThat(response.getTimestamp()).isNotNull();
    }
}
