package com.flux.generator.service;

import com.flux.generator.model.MarketData;
import com.flux.generator.model.RequestResult;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.WriteTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestExecutorTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    @SuppressWarnings("rawtypes")
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private RequestExecutor executor;
    private MarketData testPayload;

    @BeforeEach
    void setUp() {
        executor = new RequestExecutor(webClient);
        testPayload = new MarketData("PKO", 50.0, 1000L, Instant.now(), "warsaw");
    }

    @SuppressWarnings("unchecked")
    private void setupChain(Mono<?> responseMono) {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn((Mono) responseMono);
    }

    @Test
    void sendPayload_202response_returnsSuccess() {
        setupChain(Mono.just(ResponseEntity.status(202).build()));

        RequestResult result = executor.sendPayload(testPayload).block();

        assertThat(result.success()).isTrue();
        assertThat(result.httpStatus()).isEqualTo(202);
        assertThat(result.symbol()).isEqualTo("PKO");
    }

    @Test
    void sendPayload_300response_returnsNonSuccess() {
        setupChain(Mono.just(ResponseEntity.status(300).build()));

        RequestResult result = executor.sendPayload(testPayload).block();

        assertThat(result.success()).isFalse();
        assertThat(result.httpStatus()).isEqualTo(300);
    }

    @Test
    void sendPayload_readTimeout_returnsTimeout() {
        setupChain(Mono.error(ReadTimeoutException.INSTANCE));

        RequestResult result = executor.sendPayload(testPayload).block();

        assertThat(result.success()).isFalse();
        assertThat(result.errorType()).isEqualTo("TIMEOUT");
    }

    @Test
    void sendPayload_writeTimeout_returnsTimeout() {
        setupChain(Mono.error(WriteTimeoutException.INSTANCE));

        RequestResult result = executor.sendPayload(testPayload).block();

        assertThat(result.success()).isFalse();
        assertThat(result.errorType()).isEqualTo("TIMEOUT");
    }

    @Test
    void sendPayload_4xxException_returns4xx() {
        setupChain(Mono.error(WebClientResponseException.create(400, "Bad Request", null, null, null)));

        RequestResult result = executor.sendPayload(testPayload).block();

        assertThat(result.success()).isFalse();
        assertThat(result.errorType()).isEqualTo("4XX");
    }

    @Test
    void sendPayload_5xxException_returns5xx() {
        setupChain(Mono.error(WebClientResponseException.create(503, "Service Unavailable", null, null, null)));

        RequestResult result = executor.sendPayload(testPayload).block();

        assertThat(result.success()).isFalse();
        assertThat(result.errorType()).isEqualTo("5XX");
    }

    @Test
    void sendPayload_3xxWebClientException_fallsThroughToConnection() {
        setupChain(Mono.error(WebClientResponseException.create(302, "Found", null, null, null)));

        RequestResult result = executor.sendPayload(testPayload).block();

        assertThat(result.success()).isFalse();
        assertThat(result.errorType()).isEqualTo("CONNECTION");
    }

    @Test
    void sendPayload_connectExceptionCause_returnsConnection() {
        RuntimeException ex = new RuntimeException("connect failed", new ConnectException("refused"));
        setupChain(Mono.error(ex));

        RequestResult result = executor.sendPayload(testPayload).block();

        assertThat(result.success()).isFalse();
        assertThat(result.errorType()).isEqualTo("CONNECTION");
    }

    @Test
    void sendPayload_unknownHostCause_returnsConnection() {
        RuntimeException ex = new RuntimeException("unknown host", new UnknownHostException("host"));
        setupChain(Mono.error(ex));

        RequestResult result = executor.sendPayload(testPayload).block();

        assertThat(result.success()).isFalse();
        assertThat(result.errorType()).isEqualTo("CONNECTION");
    }

    @Test
    void sendPayload_javaTimeoutException_returnsTimeout() {
        setupChain(Mono.error(new TimeoutException("operation timed out")));

        RequestResult result = executor.sendPayload(testPayload).block();

        assertThat(result.success()).isFalse();
        assertThat(result.errorType()).isEqualTo("TIMEOUT");
    }

    @Test
    void sendPayload_connectionRefusedMessage_returnsConnection() {
        setupChain(Mono.error(new RuntimeException("Connection refused: localhost:8881")));

        RequestResult result = executor.sendPayload(testPayload).block();

        assertThat(result.success()).isFalse();
        assertThat(result.errorType()).isEqualTo("CONNECTION");
    }

    @Test
    void sendPayload_nullMessageException_returnsConnection() {
        setupChain(Mono.error(new RuntimeException((String) null)));

        RequestResult result = executor.sendPayload(testPayload).block();

        assertThat(result.success()).isFalse();
        assertThat(result.errorType()).isEqualTo("CONNECTION");
    }
}
