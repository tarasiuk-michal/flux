package com.flux.gateway.service;

import com.flux.gateway.model.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);
    private final KafkaTemplate<String, String> kafkaTemplate;
    private static final String TOPIC = "data-stream";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public Mono<Void> publish(DataPayload payload) {
        // Build JSON string manually to avoid Jackson dependency
        String jsonPayload = buildJsonPayload(payload);

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

    private String buildJsonPayload(DataPayload payload) {
        return "{" +
            "\"symbol\":\"" + escapeJson(payload.getSymbol()) + "\"," +
            "\"price\":" + payload.getPrice() + "," +
            "\"volume\":" + payload.getVolume() + "," +
            "\"timestamp\":\"" + escapeJson(payload.getTimestamp()) + "\"," +
            "\"market\":\"" + escapeJson(payload.getMarket()) + "\"" +
            "}";
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
