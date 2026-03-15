package com.flux.gateway.controller;

import com.flux.gateway.model.DataPayload;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/data")
public class DataController {

    @Value("${app.api-key:changeme}")
    private String apiKey;

    @PostMapping
    public Mono<ResponseEntity<?>> ingestData(
            @RequestHeader(value = "X-API-Key", required = false) String providedKey,
            @Valid @RequestBody DataPayload payload) {

        // Fail fast on API key validation
        if (providedKey == null || providedKey.isEmpty()) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("{\"error\": \"Missing X-API-Key header\"}"));
        }

        if (!providedKey.equals(apiKey)) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("{\"error\": \"Invalid API key\"}"));
        }

        // Valid request - return 202 Accepted (Kafka publishing comes in next task)
        return Mono.just(ResponseEntity.status(HttpStatus.ACCEPTED).build());
    }
}
