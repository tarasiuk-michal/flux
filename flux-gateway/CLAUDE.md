# Flux Gateway — CLAUDE.md

## What This Is
Spring Boot WebFlux HTTP gateway for the Flux system. Receives market data, validates API key, publishes to Kafka, returns 202 immediately. Proxies query requests to flux-warehouse.

## Tech Stack
- Java 21, Spring Boot 4.0.x (Spring Framework 7)
- Spring WebFlux (Netty)
- Spring Kafka 4.x (KafkaTemplate — **not** reactor-kafka, it's been discontinued)
- Spring Boot Actuator + Micrometer 2.x
- Maven

## Architecture
```
POST /api/data
  → Validate X-API-Key header
  → Publish to Kafka topic "data-stream" (KafkaTemplate, async)
  → Return 202 Accepted immediately

GET /api/query?market=warsaw&symbol=PKO&limit=100
  → WebClient proxy to flux-warehouse:8082/api/query
  → Return 200 with results
```

## Configuration (application.yml)
```yaml
spring:
  webflux:
    base-path: /api
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9092}
    producer:
      acks: 1
      properties:
        linger.ms: 10
        batch.size: 16384
    topic: data-stream

app:
  api-key: ${FLUX_API_KEY:changeme}
  warehouse-url: http://flux-warehouse:8082

server:
  port: 8081
```

## Package Structure
```
com.flux.gateway
├── config/          # WebClientConfig, KafkaProducerConfig
├── controller/      # DataController, QueryController
├── service/         # KafkaProducerService
├── exception/       # GlobalExceptionHandler
├── filter/          # CorrelationIdWebFilter
└── model/           # DataPayload
```

## Gotchas

**No blocking in WebFlux handlers** — No JDBC, no Thread.sleep(), no RestTemplate. Use WebClient for all outbound HTTP.

**Kafka producer backpressure** — KafkaTemplate.send() returns CompletableFuture. If Kafka is slow, convert to Mono via `Mono.fromFuture()` and add `.timeout(5s)`. Don't let requests pile up unbounded.

**reactor-kafka is discontinued** — Spring dropped it. Use Spring Kafka's standard KafkaTemplate with `Mono.fromFuture(kafkaTemplate.send(...))` wrapper for reactive chains.

**API key: fail fast** — Validate before touching Kafka. Wrong key → 401, skip all downstream work.

**Query proxy timeout** — WebClient call to warehouse needs `.timeout(10s)`. Slow warehouse will hold gateway connections.

**Error handling in reactive chains** — No try-catch. Use `.onErrorReturn()`, `.onErrorMap()`, `.doOnError()`. GlobalExceptionHandler still works for HTTP layer.

## API Endpoints
| Method | Path | Description |
|--------|------|-------------|
| GET | /api/health | Health check |
| POST | /api/data | Ingest payload (requires `X-API-Key` header) → 202 |
| GET | /api/query | Proxy to warehouse (params: `market`, `symbol`, `limit`) |

## Data Payload Schema
```java
record DataPayload(
    @NotBlank String symbol,
    @NotNull Double price,
    @NotNull Long volume,
    @NotNull Instant timestamp,
    @NotBlank String market
) {}
```

## Error Handling
CorrelationId WebFilter generates UUID per request, adds to MDC + response headers.

| Exception | Status | When |
|-----------|--------|------|
| Validation error | 400 | Missing/invalid fields |
| Bad/missing API key | 401 | Auth failure |
| Kafka publish timeout | 503 | Kafka unresponsive |
| Warehouse unreachable | 503 | WebClient connection failure |
| Warehouse slow | 504 | Query proxy timeout |

Response body: `{"timestamp": "...", "status": N, "message": "...", "correlationId": "..."}`

## Metrics (Actuator)
- `gateway.requests.total` / `.success` / `.failed` — counters
- `gateway.kafka.publish.duration` — timer
- Endpoints: `/actuator/metrics`, `/actuator/health`

## Testing
- Integration: @SpringBootTest + WebTestClient + Testcontainers Kafka
- POST /api/data with valid key → 202, message on Kafka topic
- POST /api/data with bad key → 401
- POST /api/data with invalid JSON → 400
- QueryController: mock warehouse responses, timeout handling

## Running
```bash
# Kafka first
docker run --rm -p 9092:9092 apache/kafka:latest

./mvnw spring-boot:run
curl http://localhost:8081/api/health
curl -X POST http://localhost:8081/api/data \
  -H "X-API-Key: changeme" \
  -H "Content-Type: application/json" \
  -d '{"symbol":"PKO","price":50.0,"volume":1000,"timestamp":"2025-03-15T10:00:00Z","market":"warsaw"}'
```

## Dependencies
Requires Kafka broker. Warehouse must be running for query endpoint to work.
