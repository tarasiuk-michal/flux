package com.flux.generator.service;

import com.flux.generator.model.MarketData;
import com.flux.generator.model.RequestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoadTestServiceTest {

    @Mock
    private DataGenerator dataGenerator;

    @Mock
    private RequestExecutor executor;

    private LoadTestService loadTestService;

    @BeforeEach
    void setUp() {
        loadTestService = new LoadTestService(dataGenerator, executor);
        MarketData mockData = new MarketData("PKO", 50.0, 1000L, Instant.now(), "warsaw");
        when(dataGenerator.generatePayload()).thenReturn(mockData);
        when(executor.sendPayload(any())).thenReturn(
            Mono.just(new RequestResult(202, 10L, Instant.now(), "PKO", "warsaw", true, null))
        );
    }

    @Test
    void getTestStatus_nonExistentId_returnsNull() {
        assertThat(loadTestService.getTestStatus("nonexistent")).isNull();
    }

    @Test
    void getTestResults_nonExistentId_returnsNull() {
        assertThat(loadTestService.getTestResults("nonexistent")).isNull();
    }

    @Test
    void stopTest_nonExistentId_returnsNull() {
        assertThat(loadTestService.stopTest("nonexistent")).isNull();
    }

    @Test
    void stopTest_activeTest_stopsAndMovesToCompleted() {
        String testId = loadTestService.startLoadTest(1, 10, 1);

        LoadTestService.TestState stopped = loadTestService.stopTest(testId);

        assertThat(stopped).isNotNull();
        assertThat(stopped.status).isEqualTo("STOPPED");
        assertThat(stopped.endTime).isNotNull();
        assertThat(loadTestService.getTestStatus(testId)).isNotNull(); // now in completed
        assertThat(loadTestService.getTestResults(testId)).isNotNull();
    }

    @Test
    void stopTest_alreadyDisposed_doesNotThrow() {
        String testId = loadTestService.startLoadTest(1, 1, 1);
        loadTestService.stopTest(testId); // first stop
        // second stop on already-completed test should return null
        assertThat(loadTestService.stopTest(testId)).isNull();
    }

    // TestState branch coverage

    @Test
    void testState_getElapsedSeconds_withEndTime() {
        LoadTestService.TestState state = new LoadTestService.TestState(
            "t1", Instant.now().minusSeconds(30), 100, 60);
        state.endTime = Instant.now();

        assertThat(state.getElapsedSeconds()).isGreaterThanOrEqualTo(29);
    }

    @Test
    void testState_getElapsedSeconds_withoutEndTime_usesNow() {
        LoadTestService.TestState state = new LoadTestService.TestState(
            "t1", Instant.now().minusSeconds(10), 100, 60);

        assertThat(state.getElapsedSeconds()).isGreaterThanOrEqualTo(9);
    }

    @Test
    void testState_getActualRps_withZeroElapsed() {
        LoadTestService.TestState state = new LoadTestService.TestState(
            "t1", Instant.now(), 100, 60);
        state.endTime = Instant.now(); // 0 seconds elapsed

        assertThat(state.getActualRps()).isEqualTo(0.0);
    }

    @Test
    void testState_getActualRps_withNonZeroElapsed() {
        LoadTestService.TestState state = new LoadTestService.TestState(
            "t1", Instant.now().minusSeconds(10), 100, 60);
        state.endTime = Instant.now();
        state.totalRequests.set(100);

        assertThat(state.getActualRps()).isGreaterThan(0.0);
    }

    @Test
    void testState_getSuccessRate_withZeroTotal() {
        LoadTestService.TestState state = new LoadTestService.TestState(
            "t1", Instant.now(), 100, 60);

        assertThat(state.getSuccessRate()).isEqualTo(0.0);
    }

    @Test
    void testState_getSuccessRate_withNonZeroTotal() {
        LoadTestService.TestState state = new LoadTestService.TestState(
            "t1", Instant.now(), 100, 60);
        state.totalRequests.set(100);
        state.successRequests.set(80);

        assertThat(state.getSuccessRate()).isEqualTo(80.0);
    }
}
