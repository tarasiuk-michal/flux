package com.flux.gateway.filter;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class CorrelationIdWebFilter implements WebFilter {

    public static final String CORRELATION_ID_ATTR = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Generate or extract correlation ID
        String correlationId = exchange.getRequest()
            .getHeaders()
            .getFirst(CORRELATION_ID_ATTR);

        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        final String finalCorrelationId = correlationId;

        // Add to exchange attributes for access in handlers
        exchange.getAttributes().put(CORRELATION_ID_ATTR, finalCorrelationId);

        // Add to response headers
        exchange.getResponse().getHeaders().add(CORRELATION_ID_ATTR, correlationId);

        // Add to MDC for logging
        MDC.put(CORRELATION_ID_ATTR, finalCorrelationId);

        // Process request
        return chain.filter(exchange)
            .doFinally(signalType -> MDC.clear());
    }
}
