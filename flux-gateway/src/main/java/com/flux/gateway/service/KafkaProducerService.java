package com.flux.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flux.gateway.model.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);
    private static final String TOPIC = "data-stream";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public Mono<Void> publish(DataPayload payload) {
        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }

        return Mono.<Void>create(sink -> {
            kafkaTemplate.send(TOPIC, jsonPayload).whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Published {} from {}", payload.getSymbol(), payload.getMarket());
                    sink.success();
                } else {
                    log.error("Failed to publish message: {}", payload, ex);
                    sink.error(ex);
                }
            });
        }).timeout(TIMEOUT);
    }

}
