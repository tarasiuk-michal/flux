package com.flux.warehouse.controller;

import com.flux.warehouse.dto.DataDTO;
import com.flux.warehouse.service.QueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Validated
@RestController
@RequestMapping("/query")
public class QueryController {

    private static final Logger log = LoggerFactory.getLogger(QueryController.class);

    private final QueryService queryService;
    private final Duration queryTimeout;

    public QueryController(QueryService queryService,
                           @Value("${app.query-timeout:5s}") Duration queryTimeout) {
        this.queryService = queryService;
        this.queryTimeout = queryTimeout;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<?>> query(
            @RequestParam @Size(min = 1, max = 20) String market,
            @RequestParam(required = false) @Pattern(regexp = "[A-Z0-9]{1,10}") String symbol,
            @RequestParam(required = false) @Positive @Max(1000) Integer limit) {

        return Mono.<ResponseEntity<?>>defer(() -> {
            try {
                List<DataDTO> results = queryService.queryPrices(market, symbol, limit);
                return Mono.just(ResponseEntity.ok(results));
            } catch (IllegalArgumentException e) {
                log.warn("Bad query request: {}", e.getMessage());
                return Mono.just(ResponseEntity.badRequest().body(e.getMessage()));
            } catch (DataAccessException e) {
                log.error("Database error during query: {}", e.getMessage());
                return Mono.just(ResponseEntity.status(503).body("Database unavailable"));
            }
        }).timeout(queryTimeout)
        .onErrorResume(TimeoutException.class, e -> {
            log.warn("Query timed out for market={}, symbol={}", market, symbol);
            return Mono.just(ResponseEntity.status(504).body("Query timed out"));
        });
    }
}
