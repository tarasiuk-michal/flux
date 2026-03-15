# Flux Gateway — Build Tasks

Complete in order. After each task, run the verification gate. Do NOT proceed to the next task until all checks pass. Refer to CLAUDE.md for config, endpoints, and gotchas.

---

## Task 1: Project Skeleton

**Do:**
- Initialize Maven project with pom.xml: spring-boot-starter-webflux, spring-kafka, spring-boot-starter-actuator, spring-boot-starter-logging, junit-jupiter, mockito, testcontainers (kafka)
- Create application.yml per CLAUDE.md configuration section
- Create package structure per CLAUDE.md
- Health endpoint: GET /api/health → `{"status": "UP"}`

**Verify:**
```bash
./mvnw clean compile                          # 0 errors
./mvnw spring-boot:run &
sleep 5
curl -s http://localhost:8081/api/health       # {"status": "UP"}
kill %1
```

---

## Task 2: WebClient & Kafka Producer Config

**Do:**
- WebClientConfig @Configuration: WebClient bean with connection timeout 5s, read timeout 10s, base URL from config (warehouse-url)
- KafkaProducerConfig @Configuration: KafkaTemplate<String, String> bean with JSON serializer, acks=1, linger.ms and batch.size from config
- Verify beans load without errors

**Verify:**
```bash
./mvnw clean compile
./mvnw spring-boot:run &
sleep 5
# App starts without bean creation errors — check logs for no exceptions
curl -s http://localhost:8081/api/health       # still UP
kill %1
```
No startup errors related to bean wiring. Health still responds.

---

## Task 3: Data Ingestion Endpoint + API Key Validation

**Do:**
- DataPayload model with Jakarta Bean Validation: @NotBlank symbol, @NotNull price, @NotNull volume, @NotNull timestamp, @NotBlank market
- DataController: POST /api/data
  - Extract X-API-Key header, validate against config value
  - Missing/wrong key → 401 immediately (fail fast, before Kafka)
  - Invalid body → 400
  - Valid request → 202 Accepted placeholder (Kafka comes next task)

**Verify:**
```bash
./mvnw clean compile
./mvnw spring-boot:run &
sleep 5
# Valid request (no Kafka yet, just 202 placeholder)
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8081/api/data \
  -H "X-API-Key: changeme" \
  -H "Content-Type: application/json" \
  -d '{"symbol":"PKO","price":50.0,"volume":1000,"timestamp":"2025-03-15T10:00:00Z","market":"warsaw"}'
# Expected: 202
# Wrong API key
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8081/api/data \
  -H "X-API-Key: wrong" \
  -H "Content-Type: application/json" \
  -d '{"symbol":"PKO","price":50.0,"volume":1000,"timestamp":"2025-03-15T10:00:00Z","market":"warsaw"}'
# Expected: 401
# Missing API key header
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8081/api/data \
  -H "Content-Type: application/json" \
  -d '{"symbol":"PKO","price":50.0,"volume":1000,"timestamp":"2025-03-15T10:00:00Z","market":"warsaw"}'
# Expected: 401
# Invalid body (missing required fields)
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8081/api/data \
  -H "X-API-Key: changeme" \
  -H "Content-Type: application/json" \
  -d '{"symbol":"PKO"}'
# Expected: 400
kill %1
```
Auth works, validation works, correct status codes returned.

---

## Task 4: Kafka Publishing

**Do:**
- KafkaProducerService: publish(DataPayload) method
  - Use `Mono.fromFuture(kafkaTemplate.send(topic, payload))` for reactive chain
  - On success: log "Published {symbol} from {market}"
  - Add `.timeout(Duration.ofSeconds(5))` — on timeout return 503
- Wire DataController → KafkaProducerService
  - On publish success → 202 Accepted
  - On publish failure → 503 Service Unavailable

**Verify:**
```bash
docker run -d --name flux-kafka -p 9092:9092 apache/kafka:latest
sleep 10
./mvnw clean compile
./mvnw spring-boot:run &
sleep 5
# Send valid payload
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8081/api/data \
  -H "X-API-Key: changeme" \
  -H "Content-Type: application/json" \
  -d '{"symbol":"PKO","price":50.0,"volume":1000,"timestamp":"2025-03-15T10:00:00Z","market":"warsaw"}'
# Expected: 202
# Verify message landed on Kafka
docker exec flux-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic data-stream --from-beginning --max-messages 1 --timeout-ms 5000
# Expected: JSON payload with symbol, price, volume, timestamp, market
# Check app logs for "Published PKO from warsaw"
kill %1
docker rm -f flux-kafka
```
Message confirmed on Kafka topic. 202 returned to client.

---

## Task 5: Query Proxy Endpoint

**Do:**
- QueryController: GET /api/query with params market, symbol, limit
- WebClient call to warehouse URL + /api/query, pass through query params
- Timeout 10s on WebClient call
- Warehouse success → 200 with pass-through body
- Warehouse timeout → 504
- Warehouse unreachable → 503

**Verify:**
```bash
./mvnw clean compile
./mvnw spring-boot:run &
sleep 5
# Warehouse not running — should get 503
curl -s -o /dev/null -w "%{http_code}" "http://localhost:8081/api/query?market=warsaw"
# Expected: 503 (warehouse unreachable)
kill %1
# Now test with a mock warehouse (python one-liner)
python3 -c "
from http.server import HTTPServer, BaseHTTPRequestHandler
import json
class H(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header('Content-Type','application/json')
        self.end_headers()
        self.wfile.write(json.dumps([{'symbol':'PKO','price':50.0}]).encode())
    def log_message(self, *a): pass
HTTPServer(('',8082),H).serve_forever()
" &
MOCK_PID=$!
sleep 1
./mvnw spring-boot:run &
sleep 5
curl -s "http://localhost:8081/api/query?market=warsaw&symbol=PKO"
# Expected: [{"symbol":"PKO","price":50.0}]
kill %1
kill $MOCK_PID
```
Proxy passes through responses. Handles unreachable warehouse gracefully.

---

## Task 6: Correlation ID Filter & Error Handling

**Do:**
- CorrelationIdWebFilter: generate UUID per request, add to MDC, add to response header `X-Correlation-Id`
- GlobalExceptionHandler @ControllerAdvice per CLAUDE.md error table
- Response body: `{"timestamp": "...", "status": N, "message": "...", "correlationId": "..."}`

**Verify:**
```bash
./mvnw clean compile
./mvnw spring-boot:run &
sleep 5
# Check correlation ID in response headers
curl -s -I -X POST http://localhost:8081/api/data \
  -H "X-API-Key: wrong" \
  -H "Content-Type: application/json" \
  -d '{}'
# Expected: X-Correlation-Id header present
# Check error body structure
curl -s -X POST http://localhost:8081/api/data \
  -H "Content-Type: application/json" \
  -d '{}' | python3 -m json.tool
# Expected: JSON with timestamp, status (401), message, correlationId fields
kill %1
```
Every response has correlation ID. Error bodies are structured.

---

## Task 7: Metrics

**Do:**
- MetricsService with Micrometer MeterRegistry:
  - Counter: gateway.requests.total, gateway.requests.success, gateway.requests.failed
  - Timer: gateway.kafka.publish.duration
- Wire into DataController: increment on each request, record publish duration
- Expose via actuator: /actuator/metrics, /actuator/health

**Verify:**
```bash
docker run -d --name flux-kafka -p 9092:9092 apache/kafka:latest
sleep 10
./mvnw clean compile
./mvnw spring-boot:run &
sleep 5
# Send a few requests
for i in 1 2 3; do
  curl -s -o /dev/null -X POST http://localhost:8081/api/data \
    -H "X-API-Key: changeme" \
    -H "Content-Type: application/json" \
    -d '{"symbol":"PKO","price":50.0,"volume":1000,"timestamp":"2025-03-15T10:00:00Z","market":"warsaw"}'
done
# One bad request
curl -s -o /dev/null -X POST http://localhost:8081/api/data -H "X-API-Key: wrong" -H "Content-Type: application/json" -d '{}'
# Check metrics
curl -s http://localhost:8081/actuator/metrics/gateway.requests.total | grep -o '"value":[0-9.]*'
# Expected: value >= 4
curl -s http://localhost:8081/actuator/metrics/gateway.requests.success | grep -o '"value":[0-9.]*'
# Expected: value >= 3
curl -s http://localhost:8081/actuator/metrics/gateway.requests.failed | grep -o '"value":[0-9.]*'
# Expected: value >= 1
kill %1
docker rm -f flux-kafka
```
Metrics reflect actual request counts.

---

## Task 8: Tests

**Do:**
- GatewayIntegrationTest: @SpringBootTest + WebTestClient + Testcontainers Kafka
  - POST /api/data with valid key → 202, message on Kafka topic
  - POST /api/data with bad key → 401
  - POST /api/data with invalid JSON → 400
  - POST /api/data with missing key header → 401
- QueryControllerTest: mock warehouse via WireMock or similar
  - Proxy success → 200
  - Warehouse timeout → 504
  - Warehouse down → 503
- Verify correlation ID present in all responses

**Verify:**
```bash
./mvnw clean verify
# ALL tests pass, 0 failures, 0 errors
```
`./mvnw clean verify` exits 0. Commit.
