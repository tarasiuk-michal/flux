package com.flux.generator.service;

import com.flux.generator.model.MarketData;
import com.flux.generator.model.RequestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Service
public class RequestExecutor {

    private static final Logger log = LoggerFactory.getLogger(RequestExecutor.class);
    private final WebClient webClient;

    public RequestExecutor(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<RequestResult> sendPayload(MarketData payload) {
        Instant startTime = Instant.now();

        return webClient.post()
            .uri("/data")
            .bodyValue(payload)
            .retrieve()
            .toBodilessEntity()
            .map(response -> {
                long latencyMs = Duration.between(startTime, Instant.now()).toMillis();
                int status = response.getStatusCode().value();
                boolean success = status >= 200 && status < 300;

                if (success) {
                    log.debug("Sent {} from {} — {}ms", payload.symbol(), payload.market(), latencyMs);
                }

                return new RequestResult(
                    status,
                    latencyMs,
                    Instant.now(),
                    payload.symbol(),
                    payload.market(),
                    success,
                    null
                );
            })
            .onErrorResume(e -> {
                long latencyMs = Duration.between(startTime, Instant.now()).toMillis();
                String errorType = categorizeError(e);

                return Mono.just(new RequestResult(
                    0,
                    latencyMs,
                    Instant.now(),
                    payload.symbol(),
                    payload.market(),
                    false,
                    errorType
                ));
            })
            .timeout(Duration.ofSeconds(15));
    }

    private String categorizeError(Throwable e) {
        if (e instanceof io.netty.handler.timeout.ReadTimeoutException ||
            e instanceof io.netty.handler.timeout.WriteTimeoutException) {
            return "TIMEOUT";
        }
        if (e instanceof WebClientResponseException) {
            WebClientResponseException wce = (WebClientResponseException) e;
            int status = wce.getStatusCode().value();
            if (status >= 400 && status < 500) {
                return "4XX";
            } else if (status >= 500) {
                return "5XX";
            }
        }
        if (e.getCause() instanceof java.net.ConnectException ||
            e.getCause() instanceof java.net.UnknownHostException) {
            return "CONNECTION";
        }
        if (e instanceof java.util.concurrent.TimeoutException) {
            return "TIMEOUT";
        }
        if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
            return "CONNECTION";
        }

        return "CONNECTION";
    }
}
