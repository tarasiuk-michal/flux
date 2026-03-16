package com.flux.gateway.exception;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(WebExchangeBindException e) {
        String correlationId = MDC.get(CORRELATION_ID_HEADER);
        ErrorResponse response = new ErrorResponse(400, "Validation error: " + e.getMessage(), correlationId);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        String correlationId = MDC.get(CORRELATION_ID_HEADER);
        ErrorResponse response = new ErrorResponse(500, "Internal server error", correlationId);
        return ResponseEntity.status(500).body(response);
    }
}
