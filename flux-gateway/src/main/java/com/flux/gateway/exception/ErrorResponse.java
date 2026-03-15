package com.flux.gateway.exception;

import java.time.LocalDateTime;

public class ErrorResponse {

    private LocalDateTime timestamp;
    private int status;
    private String message;
    private String correlationId;

    public ErrorResponse(int status, String message, String correlationId) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.message = message;
        this.correlationId = correlationId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
