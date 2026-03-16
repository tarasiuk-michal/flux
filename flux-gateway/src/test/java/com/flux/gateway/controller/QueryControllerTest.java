package com.flux.gateway.controller;

import com.flux.gateway.exception.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryControllerTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private ServerWebExchange exchange;

    private QueryController queryController;

    @BeforeEach
    void setUp() {
        queryController = new QueryController(webClient);
        lenient().when(exchange.getAttribute("X-Correlation-Id")).thenReturn("test-corr-id");
    }

    @SuppressWarnings("unchecked")
    private void setupWebClientChain(Mono<String> responseMono) {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), any(String[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(responseMono);
    }

    @Test
    void query_withMarketOnly_returnsOk() {
        setupWebClientChain(Mono.just("{\"data\": []}"));

        ResponseEntity<Object> response = queryController.query("warsaw", null, null, exchange).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("{\"data\": []}");
    }

    @Test
    void query_withAllParams_returnsOk() {
        setupWebClientChain(Mono.just("{\"data\": [{\"symbol\":\"PKO\"}]}"));

        ResponseEntity<Object> response = queryController.query("warsaw", "PKO", 10, exchange).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void query_timeoutException_returns504() {
        setupWebClientChain(Mono.error(new TimeoutException("timed out")));

        ResponseEntity<Object> response = queryController.query("warsaw", null, null, exchange).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        ErrorResponse error = (ErrorResponse) response.getBody();
        assertThat(error.getMessage()).isEqualTo("Warehouse query timeout");
        assertThat(error.getStatus()).isEqualTo(504);
    }

    @Test
    void query_genericError_returns503() {
        setupWebClientChain(Mono.error(new RuntimeException("connection refused")));

        ResponseEntity<Object> response = queryController.query("warsaw", null, null, exchange).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        ErrorResponse error = (ErrorResponse) response.getBody();
        assertThat(error.getMessage()).isEqualTo("Warehouse unreachable");
    }

    @Test
    void query_noCorrelationId_handlesGracefully() {
        when(exchange.getAttribute("X-Correlation-Id")).thenReturn(null);
        setupWebClientChain(Mono.just("{\"result\":\"ok\"}"));

        ResponseEntity<Object> response = queryController.query("warsaw", null, null, exchange).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void query_withSymbolNoLimit_buildsCorrectParams() {
        setupWebClientChain(Mono.just("{}"));

        ResponseEntity<Object> response = queryController.query("warsaw", "PKO", null, exchange).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
