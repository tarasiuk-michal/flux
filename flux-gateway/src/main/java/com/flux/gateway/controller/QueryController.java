package com.flux.gateway.controller;

import com.flux.gateway.exception.ErrorResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestMapping("/query")
public class QueryController {

    private final WebClient webClient;

    public QueryController(WebClient webClient) {
        this.webClient = webClient;
    }

    @GetMapping
    public Mono<ResponseEntity<Object>> query(
            @RequestParam(required = false) String market,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) Integer limit,
            org.springframework.web.server.ServerWebExchange exchange) {

        String correlationId = (String) exchange.getAttribute("X-Correlation-Id");
        if (correlationId == null) {
            correlationId = MDC.get("X-Correlation-Id");
        }
        final String finalCorrelationId = correlationId;

        // Build the warehouse query URL
        String warehouseQuery = "/api/query";
        if (market != null || symbol != null || limit != null) {
            warehouseQuery += "?";
            StringBuilder params = new StringBuilder();
            if (market != null) {
                params.append("market=").append(market);
            }
            if (symbol != null) {
                if (params.length() > 0) params.append("&");
                params.append("symbol=").append(symbol);
            }
            if (limit != null) {
                if (params.length() > 0) params.append("&");
                params.append("limit=").append(limit);
            }
            warehouseQuery += params;
        }

        return webClient.get()
            .uri(warehouseQuery)
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(10))
            .map(body -> ResponseEntity.status(HttpStatus.OK).body((Object) body))
            .onErrorResume(e -> {
                // Check if timeout
                if (e instanceof java.util.concurrent.TimeoutException) {
                    ErrorResponse error = new ErrorResponse(504, "Warehouse query timeout", finalCorrelationId);
                    return Mono.just(ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body((Object) error));
                }
                // All other connection errors → 503
                ErrorResponse error = new ErrorResponse(503, "Warehouse unreachable", finalCorrelationId);
                return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body((Object) error));
            });
    }
}
