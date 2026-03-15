package com.flux.warehouse.consumer;

import com.flux.warehouse.model.DataMessage;
import com.flux.warehouse.service.DataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        try {
            if (message != null) {
                log.debug("Received {} from {}", message.getSymbol(), message.getMarket());
                dataService.processMessage(message);
            }
        } catch (Exception e) {
            log.error("Error processing message: {}", message, e);
        }
    }
}
