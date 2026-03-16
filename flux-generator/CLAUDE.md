# Flux Generator — CLAUDE.md

## What This Is
Spring Boot WebFlux load generator. Produces realistic market data payloads and fires them at flux-gateway at configurable rate (target: ~1000 RPS). Non-blocking WebClient + Reactor Flux pipeline.

## Tech Stack
- Java 21, Spring Boot 4.0.x (Spring Framework 7)
- Spring WebFlux (Netty), WebClient
- Project Reactor (Flux/Mono)
- Micrometer 2.x (latency tracking)
- Maven

## Architecture
```
POST /api/load-test/start
  → DataGenerator creates payloads
  → Flux.interval emits at target rate
  → WebClient POSTs to flux-gateway (http://flux-gateway:8881/api/data)
  → Log response + latency
  → Aggregate metrics
```

## Configuration (application.yml)
```yaml
spring:
  webflux:
    base-path: /api

app:
  gateway-url: http://flux-gateway:8881
  api-key: ${FLUX_API_KEY:changeme}
  load-test:
    requests-per-second: 1000
    duration-seconds: 60
    concurrency: 100
    output-file: /tmp/load-test-results.log

server:
  port: 8882
```

## Package Structure
```
com.flux.generator
├── config/          # WebClientConfig
├── controller/      # LoadTestController
├── service/         # LoadTestService, RequestExecutor, DataGenerator, MetricsAggregator
└── model/           # MarketData, LoadTestResult, RequestResult
```

## Gotchas

**Thread exhaustion** — Use `Flux.flatMap(payload -> send(), concurrency)` with concurrency=100. Don't spawn unbounded threads.

**Connection pool** — Default WebClient pool is too small for 1000 RPS. Configure max connections=500, per-route=200 in WebClientConfig.

**Backpressure** — If gateway is slow, requests buffer. Use `.onBackpressureBuffer(10000)` or `.onBackpressureDrop()`. No strategy = OOM.

**Logging at 1000 RPS** — Per-request logging kills throughput. Log every 100th request or aggregate every 10s. Use logback AsyncAppender.

**API key header** — Every request needs `X-API-Key`. Set in WebClient default headers.

**Latency measurement** — Use `.elapsed()` operator or `Instant.now()` before/after. Measures wall time including network.

## Data Model
```java
record MarketData(String symbol, double price, long volume, Instant timestamp, String market) {}
```

Markets and symbols:
- **Warsaw**: PKO, PKOBP, ASSECOPOL, GPWT, KGHM, PKNORLEN, PGEPL, TAURONPE
- **NYSE**: AAPL, MSFT, TSLA, GOOGL, AMZN, META, NVDA, JPM
- **TSE**: 9984 (SoftBank), 6758 (Sony), 7203 (Toyota), 8031 (Mitsui)
- **HKEX**: 700 (Tencent), 3988 (Bank of China), 1000 (HK Property)

Price ranges vary by market (e.g. TSE stocks are thousands of yen). Volume: 100k–10M.

## API Endpoints
| Method | Path | Description |
|--------|------|-------------|
| GET | /api/health | Health check |
| POST | /api/load-test/start | Start test (body: `{requestsPerSecond, durationSeconds, concurrency}`) → 202 |
| GET | /api/load-test/status/{testId} | Progress (total, success, failure, elapsed) |
| POST | /api/load-test/stop/{testId} | Graceful stop → final results |
| GET | /api/load-test/results/{testId} | Full metrics (latency percentiles, error breakdown) |

## Metrics Aggregation
Every 10s log: `RPS: {actual}/{target}, Success: {%}, P99: {ms}ms`

Final report: total, success/fail counts, actual RPS, min/max/avg/p50/p95/p99 latency, error breakdown (timeout, 4xx, 5xx, connection errors).

## Error Handling
- Connection timeout / read timeout / 4xx / 5xx — categorize, count, don't crash
- If target RPS unachievable, log warning, continue, report actual vs target
- GlobalExceptionHandler: bad config → 400, timeout → 503, generic → 500

## Testing
- DataGeneratorTest: valid fields, correct market→symbol mapping, reasonable ranges
- LoadTestControllerTest: mock service, 202 on valid, 400 on invalid (0 RPS)
- RequestExecutorTest: WireMock gateway — 202 success, timeout failure, 401 bad key

## Running
```bash
./mvnw spring-boot:run
curl http://localhost:8882/api/health
curl -X POST http://localhost:8882/api/load-test/start \
  -H "Content-Type: application/json" \
  -d '{"requestsPerSecond":1000,"durationSeconds":60,"concurrency":100}'
```

## JVM Tuning
```bash
java -Xmx2G -Xms2G -XX:+UseZGC -jar generator-app.jar
```

## Dependencies
Requires **flux-gateway** to be running on port 8881 first.
