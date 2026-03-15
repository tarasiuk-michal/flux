package com.flux.generator.controller;

import com.flux.generator.model.LoadTestRequest;
import com.flux.generator.model.LoadTestResult;
import com.flux.generator.service.LoadTestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/load-test")
public class LoadTestController {

    private final LoadTestService loadTestService;

    public LoadTestController(LoadTestService loadTestService) {
        this.loadTestService = loadTestService;
    }

    @PostMapping("/start")
    public Mono<ResponseEntity<Map<String, String>>> startTest(@RequestBody LoadTestRequest request) {
        // Validation
        if (request.requestsPerSecond() <= 0 || request.durationSeconds() <= 0 || request.concurrency() <= 0) {
            return Mono.just(ResponseEntity.badRequest()
                .body(Map.of("error", "requestsPerSecond, durationSeconds, and concurrency must be > 0")));
        }

        String testId = loadTestService.startLoadTest(
            request.requestsPerSecond(),
            request.durationSeconds(),
            request.concurrency()
        );

        return Mono.just(ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(Map.of(
                "testId", testId,
                "status", "RUNNING"
            )));
    }

    @GetMapping("/status/{testId}")
    public Mono<ResponseEntity<Map<String, Object>>> getStatus(@PathVariable String testId) {
        LoadTestService.TestState state = loadTestService.getTestStatus(testId);

        if (state == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        return Mono.just(ResponseEntity.ok(Map.of(
            "testId", state.testId,
            "status", state.status,
            "totalRequests", state.totalRequests.get(),
            "successCount", state.successRequests.get(),
            "failureCount", state.failureRequests.get(),
            "elapsedSeconds", state.getElapsedSeconds(),
            "actualRps", String.format("%.2f", state.getActualRps()),
            "successRate", String.format("%.2f", state.getSuccessRate())
        )));
    }

    @PostMapping("/stop/{testId}")
    public Mono<ResponseEntity<Map<String, Object>>> stopTest(@PathVariable String testId) {
        LoadTestService.TestState state = loadTestService.stopTest(testId);

        if (state == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        return Mono.just(ResponseEntity.ok(Map.of(
            "testId", state.testId,
            "status", state.status,
            "totalRequests", state.totalRequests.get(),
            "successCount", state.successRequests.get(),
            "failureCount", state.failureRequests.get(),
            "durationSeconds", state.getElapsedSeconds()
        )));
    }

    @GetMapping("/results/{testId}")
    public Mono<ResponseEntity<LoadTestResult>> getResults(@PathVariable String testId) {
        LoadTestService.TestState state = loadTestService.getTestResults(testId);

        if (state == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        LoadTestResult result = new LoadTestResult(testId);
        result.totalRequests = state.totalRequests.get();
        result.successCount = state.successRequests.get();
        result.failureCount = state.failureRequests.get();
        result.durationSeconds = state.getElapsedSeconds();
        result.actualRps = state.getActualRps();
        result.successRate = state.getSuccessRate();

        return Mono.just(ResponseEntity.ok(result));
    }
}
