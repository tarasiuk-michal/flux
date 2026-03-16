package com.flux.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdWebFilterTest {

    private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

    @Test
    void filter_noCorrelationIdHeader_generatesOne() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebFilterChain chain = filterExchange -> Mono.empty();

        filter.filter(exchange, chain).block();

        String correlationId = (String) exchange.getAttribute("X-Correlation-Id");
        assertThat(correlationId).isNotNull().isNotEmpty();

        String responseHeader = exchange.getResponse().getHeaders().getFirst("X-Correlation-Id");
        assertThat(responseHeader).isEqualTo(correlationId);
    }

    @Test
    void filter_existingCorrelationIdHeader_usesProvided() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/health")
            .header("X-Correlation-Id", "provided-id")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebFilterChain chain = filterExchange -> Mono.empty();

        filter.filter(exchange, chain).block();

        String correlationId = (String) exchange.getAttribute("X-Correlation-Id");
        assertThat(correlationId).isEqualTo("provided-id");
    }

    @Test
    void filter_emptyCorrelationIdHeader_generatesNew() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/health")
            .header("X-Correlation-Id", "")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebFilterChain chain = filterExchange -> Mono.empty();

        filter.filter(exchange, chain).block();

        String correlationId = (String) exchange.getAttribute("X-Correlation-Id");
        assertThat(correlationId).isNotEmpty();
        assertThat(correlationId).isNotEqualTo("");
    }
}
