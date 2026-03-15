package com.flux.warehouse.service;

import com.flux.warehouse.model.Company;
import com.flux.warehouse.model.DataMessage;
import com.flux.warehouse.model.Market;
import com.flux.warehouse.model.Price;
import com.flux.warehouse.repository.CompanyRepository;
import com.flux.warehouse.repository.PriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataServiceTest {

    @Mock
    private PriceRepository priceRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private MetricsService metricsService;

    private DataService dataService;

    @BeforeEach
    void setUp() {
        // Setup mock companies
        Market market = new Market("Warsaw Stock Exchange", "warsaw");
        market.setId(1L);

        Company company = new Company("PKO", "PKO Bank Polski", market);
        company.setId(1L);

        List<Company> companies = new ArrayList<>();
        companies.add(company);

        when(companyRepository.findAll()).thenReturn(companies);

        dataService = new DataService(priceRepository, companyRepository, metricsService);
    }

    @Test
    void testValidMessage() {
        DataMessage message = new DataMessage();
        message.setSymbol("PKO");
        message.setPrice(50.0);
        message.setVolume(1000L);
        message.setTimestamp("2025-03-15T10:00:00Z");
        message.setMarket("warsaw");

        dataService.processMessage(message);

        // Verify price was saved
        ArgumentCaptor<Price> priceCaptor = ArgumentCaptor.forClass(Price.class);
        verify(priceRepository).save(priceCaptor.capture());
        Price savedPrice = priceCaptor.getValue();

        assertEquals(50.0, savedPrice.getPrice());
        assertEquals(1000L, savedPrice.getVolume());

        // Verify metrics were updated
        verify(metricsService).incrementConsumed();
        verify(metricsService).incrementSaved();
    }

    @Test
    void testMissingSymbol() {
        DataMessage message = new DataMessage();
        message.setSymbol(null);
        message.setPrice(50.0);
        message.setVolume(1000L);
        message.setTimestamp("2025-03-15T10:00:00Z");
        message.setMarket("warsaw");

        dataService.processMessage(message);

        // Verify nothing was saved
        verify(priceRepository, never()).save(any());
        verify(metricsService).incrementFailed();
    }

    @Test
    void testUnknownCompany() {
        DataMessage message = new DataMessage();
        message.setSymbol("UNKNOWN");
        message.setPrice(50.0);
        message.setVolume(1000L);
        message.setTimestamp("2025-03-15T10:00:00Z");
        message.setMarket("warsaw");

        dataService.processMessage(message);

        // Verify nothing was saved
        verify(priceRepository, never()).save(any());
        verify(metricsService).incrementFailed();
    }

    @Test
    void testMissingPrice() {
        DataMessage message = new DataMessage();
        message.setSymbol("PKO");
        message.setPrice(null);
        message.setVolume(1000L);
        message.setTimestamp("2025-03-15T10:00:00Z");
        message.setMarket("warsaw");

        dataService.processMessage(message);

        verify(priceRepository, never()).save(any());
        verify(metricsService).incrementFailed();
    }

    @Test
    void testMissingVolume() {
        DataMessage message = new DataMessage();
        message.setSymbol("PKO");
        message.setPrice(50.0);
        message.setVolume(null);
        message.setTimestamp("2025-03-15T10:00:00Z");
        message.setMarket("warsaw");

        dataService.processMessage(message);

        verify(priceRepository, never()).save(any());
        verify(metricsService).incrementFailed();
    }

    @Test
    void testMissingTimestamp() {
        DataMessage message = new DataMessage();
        message.setSymbol("PKO");
        message.setPrice(50.0);
        message.setVolume(1000L);
        message.setTimestamp(null);
        message.setMarket("warsaw");

        dataService.processMessage(message);

        verify(priceRepository, never()).save(any());
        verify(metricsService).incrementFailed();
    }

    @Test
    void testMissingMarket() {
        DataMessage message = new DataMessage();
        message.setSymbol("PKO");
        message.setPrice(50.0);
        message.setVolume(1000L);
        message.setTimestamp("2025-03-15T10:00:00Z");
        message.setMarket(null);

        dataService.processMessage(message);

        verify(priceRepository, never()).save(any());
        verify(metricsService).incrementFailed();
    }
}
