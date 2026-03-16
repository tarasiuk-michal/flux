package com.flux.gateway.controller;

import com.flux.gateway.exception.ErrorResponse;
import com.flux.gateway.model.DataPayload;
import com.flux.gateway.service.KafkaProducerService;
import com.flux.gateway.service.MetricsService;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/data")
public class DataController {

    @Value("${app.api-key:changeme}")
    private String apiKey;

    private final KafkaProducerService kafkaProducerService;
    private final MetricsService metricsService;

    public DataController(KafkaProducerService kafkaProducerService, MetricsService metricsService) {
        this.kafkaProducerService = kafkaProducerService;
        this.metricsService = metricsService;
    }

    @PostMapping
    public Mono<ResponseEntity<Object>> ingestData(
            @RequestHeader(value = "X-API-Key", required = false) String providedKey,
            @Valid @RequestBody DataPayload payload,
            org.springframework.web.server.ServerWebExchange exchange) {

        metricsService.recordTotalRequest();

        String correlationId = (String) exchange.getAttribute("X-Correlation-Id");
        if (correlationId == null) {
            correlationId = MDC.get("X-Correlation-Id");
        }
        final String finalCorrelationId = correlationId;

        // Fail fast on API key validation
        if (providedKey == null || providedKey.isEmpty()) {
            metricsService.recordFailedRequest();
            ErrorResponse error = new ErrorResponse(401, "Missing X-API-Key header", finalCorrelationId);
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body((Object) error));
        }

        if (!MessageDigest.isEqual(
                providedKey.getBytes(StandardCharsets.UTF_8),
                apiKey.getBytes(StandardCharsets.UTF_8))) {
            metricsService.recordFailedRequest();
            ErrorResponse error = new ErrorResponse(401, "Invalid API key", finalCorrelationId);
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body((Object) error));
        }

        // Publish to Kafka and handle response
        Timer.Sample sample = metricsService.startPublishTimer();
        return kafkaProducerService.publish(payload)
            .doOnSuccess(v -> {
                metricsService.recordPublishDuration(sample);
                metricsService.recordSuccessfulRequest();
            })
            .thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED).body((Object) null))
            .onErrorResume(e -> {
                metricsService.recordPublishDuration(sample);
                metricsService.recordFailedRequest();
                ErrorResponse error = new ErrorResponse(503, "Failed to publish message", finalCorrelationId);
                return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body((Object) error));
            });
    }
}
