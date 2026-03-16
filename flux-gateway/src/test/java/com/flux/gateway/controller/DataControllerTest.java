package com.flux.gateway.controller;

import com.flux.gateway.exception.ErrorResponse;
import com.flux.gateway.model.DataPayload;
import com.flux.gateway.service.KafkaProducerService;
import com.flux.gateway.service.MetricsService;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataControllerTest {

    @Mock
    private KafkaProducerService kafkaProducerService;

    @Mock
    private MetricsService metricsService;

    @Mock
    private ServerWebExchange exchange;

    @InjectMocks
    private DataController dataController;

    private DataPayload validPayload;

    @BeforeEach
    void setUp() throws Exception {
        validPayload = new DataPayload("PKO", 50.0, 1000L, "2025-03-15T10:00:00Z", "warsaw");

        Field apiKeyField = DataController.class.getDeclaredField("apiKey");
        apiKeyField.setAccessible(true);
        apiKeyField.set(dataController, "test-api-key");

        lenient().when(exchange.getAttribute("X-Correlation-Id")).thenReturn("test-correlation-id");
    }

    @Test
    void ingestData_missingApiKey_returns401() {
        ResponseEntity<Object> response = dataController.ingestData(null, validPayload, exchange).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);
        ErrorResponse error = (ErrorResponse) response.getBody();
        assertThat(error.getMessage()).isEqualTo("Missing X-API-Key header");
        assertThat(error.getStatus()).isEqualTo(401);

        verify(metricsService).recordTotalRequest();
        verify(metricsService).recordFailedRequest();
    }

    @Test
    void ingestData_emptyApiKey_returns401() {
        ResponseEntity<Object> response = dataController.ingestData("", validPayload, exchange).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ErrorResponse error = (ErrorResponse) response.getBody();
        assertThat(error.getMessage()).isEqualTo("Missing X-API-Key header");

        verify(metricsService).recordFailedRequest();
    }

    @Test
    void ingestData_invalidApiKey_returns401() {
        ResponseEntity<Object> response = dataController.ingestData("wrong-key", validPayload, exchange).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ErrorResponse error = (ErrorResponse) response.getBody();
        assertThat(error.getMessage()).isEqualTo("Invalid API key");

        verify(metricsService).recordFailedRequest();
    }

    @Test
    void ingestData_validApiKey_publishSuccess_returns202() {
        Timer.Sample mockSample = mock(Timer.Sample.class);
        when(metricsService.startPublishTimer()).thenReturn(mockSample);
        when(kafkaProducerService.publish(any(DataPayload.class))).thenReturn(Mono.empty());

        ResponseEntity<Object> response = dataController.ingestData("test-api-key", validPayload, exchange).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        verify(metricsService).recordTotalRequest();
        verify(metricsService).recordSuccessfulRequest();
        verify(metricsService).recordPublishDuration(mockSample);
        verify(kafkaProducerService).publish(validPayload);
    }

    @Test
    void ingestData_validApiKey_publishFailure_returns503() {
        Timer.Sample mockSample = mock(Timer.Sample.class);
        when(metricsService.startPublishTimer()).thenReturn(mockSample);
        when(kafkaProducerService.publish(any(DataPayload.class)))
            .thenReturn(Mono.error(new RuntimeException("Kafka down")));

        ResponseEntity<Object> response = dataController.ingestData("test-api-key", validPayload, exchange).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        ErrorResponse error = (ErrorResponse) response.getBody();
        assertThat(error.getMessage()).isEqualTo("Failed to publish message");
        assertThat(error.getStatus()).isEqualTo(503);

        verify(metricsService).recordFailedRequest();
        verify(metricsService).recordPublishDuration(mockSample);
    }

    @Test
    void ingestData_noCorrelationIdInExchange_fallsBackToMdc() {
        when(exchange.getAttribute("X-Correlation-Id")).thenReturn(null);

        ResponseEntity<Object> response = dataController.ingestData(null, validPayload, exchange).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
