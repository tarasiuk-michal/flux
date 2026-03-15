# Flux Generator — Build Tasks

Complete in order. After each task, run the verification gate. Do NOT proceed to the next task until all checks pass. Refer to CLAUDE.md for config, endpoints, and gotchas.

---

## Task 1: Project Skeleton

**Do:**
- Initialize Maven project with pom.xml: spring-boot-starter-webflux, spring-boot-starter-logging, micrometer-core, junit-jupiter, mockito, wiremock
- Create application.yml per CLAUDE.md configuration section
- Create package structure per CLAUDE.md
- Health endpoint: GET /api/health → `{"status": "UP"}`

**Verify:**
```bash
./mvnw clean compile                          # 0 errors
./mvnw spring-boot:run &
sleep 5
curl -s http://localhost:8080/api/health       # {"status": "UP"}
kill %1
```

---

## Task 2: WebClient Configuration

**Do:**
- WebClientConfig @Configuration:
  - Connection timeout: 5s, read timeout: 10s, write timeout: 10s
  - Default header: X-API-Key from config
  - Connection pool: maxConnections=500, pendingAcquireMaxCount=1000
  - Base URL from config (app.gateway-url)
  - Return WebClient bean

**Verify:**
```bash
./mvnw clean compile
./mvnw spring-boot:run &
sleep 5
# App starts without bean errors
curl -s http://localhost:8080/api/health       # still UP
kill %1
```
No startup errors. Bean wires cleanly.

---

## Task 3: Data Generator

**Do:**
- MarketData record: symbol, price, volume, timestamp, market
- DataGenerator service:
  - Maintain symbol lists per market (per CLAUDE.md)
  - generatePayload() → random market, random symbol from that market, realistic price (vary by market), volume 100k–10M, timestamp = Instant.now()
  - Optional: slight price variance (±0.5% from a base) for realism
  - Keep it fast — this must be negligible overhead at 1000 RPS

**Verify:**
```bash
./mvnw clean compile
# Write a quick integration test or main method that generates 10000 payloads and prints timing
./mvnw test -Dtest=DataGeneratorTest
# Test checks:
#   - All fields non-null
#   - Symbol belongs to its market
#   - Price > 0, volume in range
#   - 10000 generations < 100ms (fast enough for 1000 RPS)
```
Generator produces valid, correctly-mapped payloads quickly.

---

## Task 4: Request Executor

**Do:**
- RequestExecutor service: sendPayload(MarketData) → Mono<RequestResult>
- RequestResult: httpStatus, latencyMs, timestamp, symbol, market, success (true if 2xx)
- Uses WebClient POST to gateway /api/data
- `.elapsed()` operator for latency measurement
- `.timeout(Duration.ofSeconds(15))`
- On success: log at DEBUG "Sent {symbol} from {market} — {latencyMs}ms"
- On error: categorize (connection timeout, read timeout, 4xx, 5xx, network error), return failed RequestResult

**Verify:**
```bash
./mvnw clean compile
./mvnw test -Dtest=RequestExecutorTest
# WireMock tests:
#   - Mock 202 response → RequestResult.success=true, latencyMs > 0
#   - Mock timeout → RequestResult.success=false, error type = TIMEOUT
#   - Mock 401 → RequestResult.success=false, httpStatus=401
#   - Mock 500 → RequestResult.success=false, httpStatus=500
#   - Mock connection refused → RequestResult.success=false, error type = CONNECTION
```
All WireMock scenarios pass. Error categorization correct.

---

## Task 5: Load Test Service — Core Engine

**Do:**
- LoadTestService: startLoadTest(requestsPerSecond, durationSeconds, concurrency) → test runs async, returns testId
- Internal state per test: testId (UUID), status (RUNNING/COMPLETED/STOPPED), AtomicLong counters (total, success, failure), startTime
- Flux pipeline:
  - `Flux.interval(Duration.ofNanos(1_000_000_000L / requestsPerSecond))` to control rate
  - `.take(requestsPerSecond * durationSeconds)` to limit total
  - `.flatMap(i -> executor.sendPayload(generator.generatePayload()), concurrency)`
  - `.onBackpressureBuffer(10000)` with overflow log warning
  - `.doOnNext()` to increment counters
- Store active/completed tests in ConcurrentHashMap by testId

**Verify:**
```bash
# Start a WireMock stub that returns 202 for any POST
./mvnw clean compile
./mvnw test -Dtest=LoadTestServiceTest
# Test with low rate (10 RPS for 3 seconds) against WireMock:
#   - Returns testId (non-null UUID)
#   - After completion: totalRequests ≈ 30 (±5 tolerance)
#   - successCount ≈ totalRequests (WireMock always returns 202)
#   - failureCount = 0
#   - status = COMPLETED
#   - Duration ≈ 3 seconds (±1s tolerance)
```
Pipeline emits at controlled rate, counts are accurate, completes cleanly.

---

## Task 6: Metrics Aggregator

**Do:**
- MetricsAggregator service:
  - Accept stream of RequestResult objects
  - Calculate in real-time (every 10s): actual RPS, success rate %, p50/p95/p99 latency
  - Log summary line: "RPS: {actual}/{target}, Success: {%}, P99: {ms}ms"
- LoadTestResult model: totalRequests, successCount, failureCount, durationSeconds, actualRps, min/max/avg/p50/p95/p99 latency, error breakdown by type
- Wire into LoadTestService: aggregate on completion → produce LoadTestResult

**Verify:**
```bash
./mvnw clean compile
./mvnw test -Dtest=MetricsAggregatorTest
# Feed 100 RequestResults with known latencies
#   - p50 is correct (median of input)
#   - p99 is correct
#   - success rate matches input ratio
#   - error breakdown counts match
```
Percentile math is correct. Aggregation produces accurate summaries.

---

## Task 7: Controller & API Endpoints

**Do:**
- LoadTestController:
  - POST /api/load-test/start → body `{requestsPerSecond, durationSeconds, concurrency}` → 202 with `{testId, status: "RUNNING"}`
  - GET /api/load-test/status/{testId} → current progress
  - POST /api/load-test/stop/{testId} → graceful stop via Disposable.dispose(), return final results
  - GET /api/load-test/results/{testId} → full LoadTestResult (404 if test not found or still running)
- Input validation: RPS > 0, duration > 0, concurrency > 0 → 400 on invalid

**Verify:**
```bash
# Need a target for the generator to hit — use a simple mock
python3 -c "
from http.server import HTTPServer, BaseHTTPRequestHandler
class H(BaseHTTPRequestHandler):
    def do_POST(self):
        self.send_response(202)
        self.end_headers()
    def log_message(self, *a): pass
HTTPServer(('',8081),H).serve_forever()
" &
MOCK_PID=$!
sleep 1
./mvnw clean compile
./mvnw spring-boot:run &
sleep 5
# Start a short test
TEST_ID=$(curl -s -X POST http://localhost:8080/api/load-test/start \
  -H "Content-Type: application/json" \
  -d '{"requestsPerSecond":10,"durationSeconds":5,"concurrency":5}' | python3 -c "import sys,json; print(json.load(sys.stdin)['testId'])")
echo "Test ID: $TEST_ID"
# Check status while running
sleep 2
curl -s "http://localhost:8080/api/load-test/status/$TEST_ID" | python3 -m json.tool
# Expected: status=RUNNING, totalRequests > 0
# Wait for completion
sleep 10
curl -s "http://localhost:8080/api/load-test/results/$TEST_ID" | python3 -m json.tool
# Expected: status=COMPLETED, totalRequests ≈ 50, successCount ≈ 50, latency percentiles present
# Invalid config
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/load-test/start \
  -H "Content-Type: application/json" \
  -d '{"requestsPerSecond":0,"durationSeconds":5,"concurrency":5}'
# Expected: 400
kill %1
kill $MOCK_PID
```
Full lifecycle works: start → status → results. Invalid input rejected.

---

## Task 8: Error Handling & Resilience

**Do:**
- GlobalExceptionHandler: bad config → 400, timeout → 503, generic → 500
- LoadTestService: if target RPS unachievable, log warning, don't crash, report actual vs target
- RequestExecutor: categorize all error types (connection, timeout, 4xx, 5xx) into error breakdown

**Verify:**
```bash
# Test against nothing running on 8081 (all requests fail)
./mvnw clean compile
./mvnw spring-boot:run &
sleep 5
TEST_ID=$(curl -s -X POST http://localhost:8080/api/load-test/start \
  -H "Content-Type: application/json" \
  -d '{"requestsPerSecond":10,"durationSeconds":3,"concurrency":5}' | python3 -c "import sys,json; print(json.load(sys.stdin)['testId'])")
sleep 10
RESULT=$(curl -s "http://localhost:8080/api/load-test/results/$TEST_ID")
echo "$RESULT" | python3 -m json.tool
# Expected: failureCount ≈ 30, successCount = 0, error breakdown shows CONNECTION errors
# App did NOT crash
curl -s http://localhost:8080/api/health
# Expected: {"status": "UP"}
kill %1
```
Generator survives total target failure. Reports errors accurately.

---

## Task 9: Tests

**Do:**
- DataGeneratorTest: valid fields, correct market→symbol mapping, price/volume ranges, generation speed
- RequestExecutorTest: WireMock — 202 success, timeout, 401, 500, connection refused
- LoadTestServiceTest: low-rate test against WireMock, verify counts and completion
- LoadTestControllerTest: mock service, test all endpoints and validation
- MetricsAggregatorTest: known inputs → verify percentiles and breakdown

**Verify:**
```bash
./mvnw clean verify
# ALL tests pass, 0 failures, 0 errors
```
`./mvnw clean verify` exits 0. Commit.
