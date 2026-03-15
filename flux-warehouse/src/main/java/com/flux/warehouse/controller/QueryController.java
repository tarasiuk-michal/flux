package com.flux.warehouse.controller;

import com.flux.warehouse.dto.DataDTO;
import com.flux.warehouse.service.QueryService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/query")
public class QueryController {

    private final QueryService queryService;
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(5);

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<?> query(
            @RequestParam(required = true) String market,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) Integer limit) {

        return Mono.defer(() -> {
            try {
                List<DataDTO> results = queryService.queryPrices(market, symbol, limit);
                return Mono.just((Object) ResponseEntity.ok(results));
            } catch (IllegalArgumentException e) {
                return Mono.just((Object) ResponseEntity.badRequest().build());
            } catch (Exception e) {
                return Mono.just((Object) ResponseEntity.status(503).build());
            }
        }).timeout(QUERY_TIMEOUT)
        .onErrorResume(e -> Mono.just((Object) ResponseEntity.status(504).build()));
    }
}
