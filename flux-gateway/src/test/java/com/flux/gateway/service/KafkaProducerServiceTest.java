package com.flux.gateway.service;

import com.flux.gateway.model.DataPayload;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaProducerServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private KafkaProducerService kafkaProducerService;

    @BeforeEach
    void setUp() {
        kafkaProducerService = new KafkaProducerService(kafkaTemplate, objectMapper);
    }

    @Test
    void publish_success_completesNormally() throws Exception {
        DataPayload payload = new DataPayload("PKO", 50.0, 1000L, "2025-03-15T10:00:00Z", "warsaw");
        when(objectMapper.writeValueAsString(payload)).thenReturn("{\"symbol\":\"PKO\"}");

        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        RecordMetadata metadata = new RecordMetadata(new TopicPartition("data-stream", 0), 0, 0, 0, 0, 0);
        ProducerRecord<String, String> record = new ProducerRecord<>("data-stream", "{\"symbol\":\"PKO\"}");
        SendResult<String, String> sendResult = new SendResult<>(record, metadata);
        future.complete(sendResult);

        when(kafkaTemplate.send(eq("data-stream"), anyString())).thenReturn(future);

        kafkaProducerService.publish(payload).block();
    }

    @Test
    void publish_kafkaFailure_propagatesError() throws Exception {
        DataPayload payload = new DataPayload("PKO", 50.0, 1000L, "2025-03-15T10:00:00Z", "warsaw");
        when(objectMapper.writeValueAsString(payload)).thenReturn("{\"symbol\":\"PKO\"}");

        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka broker unavailable"));

        when(kafkaTemplate.send(eq("data-stream"), anyString())).thenReturn(future);

        assertThrows(RuntimeException.class, () -> kafkaProducerService.publish(payload).block());
    }

    @Test
    void publish_serializationFailure_returnsError() throws Exception {
        DataPayload payload = new DataPayload("PKO", 50.0, 1000L, "2025-03-15T10:00:00Z", "warsaw");
        when(objectMapper.writeValueAsString(payload))
            .thenThrow(new JacksonException("serialization failed") {});

        assertThrows(JacksonException.class, () -> kafkaProducerService.publish(payload).block());
    }
}
