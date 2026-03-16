package com.flux.generator.controller;

import com.flux.generator.model.LoadTestRequest;
import com.flux.generator.model.LoadTestResult;
import com.flux.generator.service.LoadTestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoadTestControllerTest {

    @Mock
    private LoadTestService loadTestService;

    @InjectMocks
    private LoadTestController controller;

    @Test
    void startTest_invalidZeroRps_returns400() {
        LoadTestRequest req = new LoadTestRequest(0, 60, 10);

        ResponseEntity<Map<String, String>> response = controller.startTest(req).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("error");

        verify(loadTestService, never()).startLoadTest(anyInt(), anyInt(), anyInt());
    }

    @Test
    void startTest_invalidZeroDuration_returns400() {
        LoadTestRequest req = new LoadTestRequest(100, 0, 10);

        ResponseEntity<Map<String, String>> response = controller.startTest(req).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void startTest_invalidZeroConcurrency_returns400() {
        LoadTestRequest req = new LoadTestRequest(100, 60, 0);

        ResponseEntity<Map<String, String>> response = controller.startTest(req).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void startTest_validRequest_returns202WithTestId() {
        LoadTestRequest req = new LoadTestRequest(100, 60, 10);
        when(loadTestService.startLoadTest(100, 60, 10)).thenReturn("test-id-123");

        ResponseEntity<Map<String, String>> response = controller.startTest(req).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsEntry("testId", "test-id-123");
        assertThat(response.getBody()).containsEntry("status", "RUNNING");
    }

    @Test
    void getStatus_notFound_returns404() {
        when(loadTestService.getTestStatus("unknown")).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = controller.getStatus("unknown").block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getStatus_found_returns200WithDetails() {
        LoadTestService.TestState state = new LoadTestService.TestState("t1", Instant.now(), 100, 60);
        state.totalRequests.set(50);
        state.successRequests.set(48);
        state.failureRequests.set(2);
        when(loadTestService.getTestStatus("t1")).thenReturn(state);

        ResponseEntity<Map<String, Object>> response = controller.getStatus("t1").block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("testId", "t1");
        assertThat(response.getBody()).containsEntry("status", "RUNNING");
        assertThat(response.getBody()).containsKey("totalRequests");
    }

    @Test
    void stopTest_notFound_returns404() {
        when(loadTestService.stopTest("unknown")).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = controller.stopTest("unknown").block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void stopTest_found_returns200WithFinalStats() {
        LoadTestService.TestState state = new LoadTestService.TestState("t1", Instant.now(), 100, 60);
        state.status = "STOPPED";
        state.totalRequests.set(100);
        when(loadTestService.stopTest("t1")).thenReturn(state);

        ResponseEntity<Map<String, Object>> response = controller.stopTest("t1").block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "STOPPED");
    }

    @Test
    void getResults_notFound_returns404() {
        when(loadTestService.getTestResults("unknown")).thenReturn(null);

        ResponseEntity<LoadTestResult> response = controller.getResults("unknown").block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getResults_found_returns200WithLoadTestResult() {
        LoadTestService.TestState state = new LoadTestService.TestState("t1", Instant.now(), 100, 60);
        state.status = "COMPLETED";
        state.totalRequests.set(200);
        state.successRequests.set(195);
        state.failureRequests.set(5);
        state.endTime = Instant.now().plusSeconds(60);
        when(loadTestService.getTestResults("t1")).thenReturn(state);

        ResponseEntity<LoadTestResult> response = controller.getResults("t1").block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoadTestResult result = response.getBody();
        assertThat(result).isNotNull();
        assertThat(result.totalRequests).isEqualTo(200);
        assertThat(result.successCount).isEqualTo(195);
        assertThat(result.failureCount).isEqualTo(5);
    }
}
