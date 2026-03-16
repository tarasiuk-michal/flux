package com.flux.gateway.controller;

import com.flux.gateway.exception.ErrorResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Validated
@RestController
@RequestMapping("/query")
public class QueryController {

    private final WebClient webClient;

    public QueryController(WebClient webClient) {
        this.webClient = webClient;
    }

    @GetMapping
    public Mono<ResponseEntity<Object>> query(
            @RequestParam @Size(min = 1, max = 20) String market,
            @RequestParam(required = false) @Pattern(regexp = "[A-Z0-9]{1,10}") String symbol,
            @RequestParam(required = false) @Positive @Max(1000) Integer limit,
            org.springframework.web.server.ServerWebExchange exchange) {

        String correlationId = (String) exchange.getAttribute("X-Correlation-Id");
        if (correlationId == null) {
            correlationId = MDC.get("X-Correlation-Id");
        }
        final String finalCorrelationId = correlationId;

        StringBuilder params = new StringBuilder("market=").append(market);
        if (symbol != null) {
            params.append("&symbol=").append(symbol);
        }
        if (limit != null) {
            params.append("&limit=").append(limit);
        }
        String warehouseQuery = "/api/query?" + params;

        return webClient.get()
            .uri(warehouseQuery)
            .header("X-Correlation-Id", finalCorrelationId)
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(10))
            .map(body -> ResponseEntity.status(HttpStatus.OK).body((Object) body))
            .onErrorResume(e -> {
                if (e instanceof java.util.concurrent.TimeoutException) {
                    ErrorResponse error = new ErrorResponse(504, "Warehouse query timeout", finalCorrelationId);
                    return Mono.just(ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body((Object) error));
                }
                ErrorResponse error = new ErrorResponse(503, "Warehouse unreachable", finalCorrelationId);
                return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body((Object) error));
            });
    }
}
