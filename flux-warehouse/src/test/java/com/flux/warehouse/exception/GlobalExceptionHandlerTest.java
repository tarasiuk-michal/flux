package com.flux.warehouse.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleMissingParameter_returns400() {
        WebExchangeBindException ex = mock(WebExchangeBindException.class);
        when(ex.getMessage()).thenReturn("symbol is required");

        ResponseEntity<ErrorResponse> response = handler.handleMissingParameter(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("Missing or invalid parameters");
    }

    @Test
    void handleMissingInput_returns400() {
        ServerWebInputException ex = mock(ServerWebInputException.class);
        when(ex.getMessage()).thenReturn("Required param missing");

        ResponseEntity<ErrorResponse> response = handler.handleMissingInput(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("Missing or invalid parameters");
    }

    @Test
    void handleIllegalArgument_returns400WithMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("Unknown market: fake");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("Unknown market: fake");
    }

    @Test
    void handleGeneralException_returns500() {
        Exception ex = new RuntimeException("unexpected");

        ResponseEntity<ErrorResponse> response = handler.handleGeneralException(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getMessage()).isEqualTo("Internal server error");
    }
}
