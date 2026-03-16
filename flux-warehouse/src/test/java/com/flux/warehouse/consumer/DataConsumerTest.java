package com.flux.warehouse.consumer;

import com.flux.warehouse.model.DataMessage;
import com.flux.warehouse.service.DataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataConsumerTest {

    @Mock
    private DataService dataService;

    @InjectMocks
    private DataConsumer dataConsumer;

    @Test
    void consume_nullMessage_skips() {
        dataConsumer.consume(null);
        verify(dataService, never()).processMessage(any());
    }

    @Test
    void consume_validMessage_delegates() {
        DataMessage msg = new DataMessage("PKO", 50.0, 1000L, "2025-03-15T10:00:00Z", "warsaw");
        dataConsumer.consume(msg);
        verify(dataService).processMessage(msg);
    }

    @Test
    void consume_illegalArgumentException_logsAndContinues() {
        DataMessage msg = new DataMessage("PKO", 50.0, 1000L, "2025-03-15T10:00:00Z", "warsaw");
        doThrow(new IllegalArgumentException("bad data")).when(dataService).processMessage(msg);
        dataConsumer.consume(msg);
        verify(dataService).processMessage(msg);
    }

    @Test
    void consume_dataAccessException_logsAndContinues() {
        DataMessage msg = new DataMessage("PKO", 50.0, 1000L, "2025-03-15T10:00:00Z", "warsaw");
        DataAccessException ex = new DataIntegrityViolationException("db locked");
        doThrow(ex).when(dataService).processMessage(msg);
        dataConsumer.consume(msg);
        verify(dataService).processMessage(msg);
    }

    @Test
    void consume_runtimeException_logsAndContinues() {
        DataMessage msg = new DataMessage("PKO", 50.0, 1000L, "2025-03-15T10:00:00Z", "warsaw");
        doThrow(new RuntimeException("unexpected")).when(dataService).processMessage(msg);
        dataConsumer.consume(msg);
        verify(dataService).processMessage(msg);
    }
}
