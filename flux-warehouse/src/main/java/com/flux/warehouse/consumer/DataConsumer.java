package com.flux.warehouse.consumer;

import com.flux.warehouse.model.DataMessage;
import com.flux.warehouse.service.DataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DataConsumer {

    private static final Logger log = LoggerFactory.getLogger(DataConsumer.class);
    private final DataService dataService;

    public DataConsumer(DataService dataService) {
        this.dataService = dataService;
    }

    @KafkaListener(topics = "data-stream", groupId = "flux-warehouse", containerFactory = "kafkaListenerContainerFactory")
    public void consume(DataMessage message) {
        if (message == null) {
            log.warn("Received null message, skipping");
            return;
        }

        try {
            log.debug("Received {} from {}", message.getSymbol(), message.getMarket());
            dataService.processMessage(message);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid message data: {}", e.getMessage());
        } catch (DataAccessException e) {
            log.error("Database error processing message symbol={} market={}: {}",
                    message.getSymbol(), message.getMarket(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("Unexpected error processing message symbol={} market={}: {}",
                    message.getSymbol(), message.getMarket(), e.getMessage(), e);
        }
    }
}
