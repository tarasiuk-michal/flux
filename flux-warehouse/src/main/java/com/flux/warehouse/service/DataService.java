package com.flux.warehouse.service;

import com.flux.warehouse.model.Company;
import com.flux.warehouse.model.DataMessage;
import com.flux.warehouse.model.Price;
import com.flux.warehouse.repository.CompanyRepository;
import com.flux.warehouse.repository.PriceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DataService {

    private static final Logger log = LoggerFactory.getLogger(DataService.class);
    private final PriceRepository priceRepository;
    private final CompanyRepository companyRepository;
    private final MetricsService metricsService;
    private Map<String, Company> companyCache;

    public DataService(PriceRepository priceRepository, CompanyRepository companyRepository, MetricsService metricsService) {
        this.priceRepository = priceRepository;
        this.companyRepository = companyRepository;
        this.metricsService = metricsService;
        initializeCompanyCache();
    }

    private void initializeCompanyCache() {
        companyCache = new HashMap<>();
        List<Company> allCompanies = companyRepository.findAllWithMarket();
        for (Company company : allCompanies) {
            String key = buildCacheKey(company.getSymbol(), company.getMarket().getCode());
            companyCache.put(key, company);
        }
        log.debug("Company cache initialized with {} entries", companyCache.size());
    }

    private String buildCacheKey(String symbol, String marketCode) {
        return symbol + ":" + marketCode;
    }

    public void processMessage(DataMessage message) {
        metricsService.incrementConsumed();

        try {
            // Validate required fields
            if (!validateMessage(message)) {
                metricsService.incrementFailed();
                return;
            }

            // Lookup company from cache
            String cacheKey = buildCacheKey(message.getSymbol(), message.getMarket());
            Company company = companyCache.get(cacheKey);

            if (company == null) {
                log.warn("Unknown company: symbol={}, market={}", message.getSymbol(), message.getMarket());
                metricsService.incrementFailed();
                return;
            }

            // Save price record
            io.micrometer.core.instrument.Timer.Sample sample = metricsService.startTimer();
            Price price = new Price(company, message.getPrice(), message.getVolume(), message.getTimestamp());
            priceRepository.save(price);
            metricsService.stopTimer(sample);

            metricsService.incrementSaved();
        } catch (Exception e) {
            log.error("Error processing message: {}", message, e);
            metricsService.incrementFailed();
        }
    }

    private boolean validateMessage(DataMessage message) {
        if (message.getSymbol() == null || message.getSymbol().isEmpty()) {
            log.warn("Invalid message: missing symbol");
            return false;
        }
        if (message.getPrice() == null) {
            log.warn("Invalid message: missing price");
            return false;
        }
        if (message.getVolume() == null) {
            log.warn("Invalid message: missing volume");
            return false;
        }
        if (message.getTimestamp() == null || message.getTimestamp().isEmpty()) {
            log.warn("Invalid message: missing timestamp");
            return false;
        }
        if (message.getMarket() == null || message.getMarket().isEmpty()) {
            log.warn("Invalid message: missing market");
            return false;
        }
        return true;
    }

}
