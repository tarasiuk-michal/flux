package com.flux.generator.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleValidationError_returns400() {
        WebExchangeBindException ex = mock(WebExchangeBindException.class);
        when(ex.getMessage()).thenReturn("field error");

        ResponseEntity<Map<String, String>> response = handler.handleValidationError(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("error");
    }

    @Test
    void handleTimeout_returns503() {
        TimeoutException ex = new TimeoutException("timeout");

        ResponseEntity<Map<String, String>> response = handler.handleTimeout(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("error", "Request timeout");
    }

    @Test
    void handleGenericException_returns500() {
        Exception ex = new RuntimeException("unexpected error");

        ResponseEntity<Map<String, String>> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "Internal server error");
    }
}
