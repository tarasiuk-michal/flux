# Flux Warehouse — Build Tasks

Complete in order. After each task, run the verification gate. Do NOT proceed to the next task until all checks pass. Refer to CLAUDE.md for schema, config, and gotchas.

---

## Task 1: Project Skeleton

**Do:**
- Initialize Maven project with pom.xml: spring-boot-starter-webflux, spring-boot-starter-data-jpa, spring-kafka, sqlite-jdbc, spring-boot-starter-actuator, junit-jupiter, mockito, testcontainers (kafka)
- Create application.yml per CLAUDE.md configuration section
- Create package structure per CLAUDE.md
- Health endpoint: GET /api/health → `{"status": "UP"}`

**Verify:**
```bash
./mvnw clean compile                          # must succeed, 0 errors
./mvnw spring-boot:run &                      # app starts, logs show port 8082
sleep 5
curl -s http://localhost:8082/api/health       # returns {"status": "UP"}
kill %1
```
All 3 checks must pass. If compile fails, fix before proceeding.

---

## Task 2: JPA Entities & Schema Initialization

**Do:**
- JPA entities: Market (id, name, code, createdAt), Company (id, symbol, name, marketId FK, createdAt), Price (id, companyId FK, price, volume, timestamp, createdAt)
- DatabaseInitializer @Component with @PostConstruct:
  - Execute PRAGMAs: `journal_mode=WAL`, `synchronous=NORMAL`
  - Create tables with `CREATE TABLE IF NOT EXISTS` via JdbcTemplate (don't rely on ddl-auto for table creation — use explicit SQL matching CLAUDE.md schema)
  - Seed market rows (warsaw, nyse, tse, hkex) and company rows per CLAUDE.md seed data
  - Use `INSERT OR IGNORE` to make seeding idempotent
- Add indexes on prices(company_id) and prices(timestamp)

**Verify:**
```bash
./mvnw clean compile
./mvnw spring-boot:run &
sleep 5
# Tables exist and seed data is present
sqlite3 data/flux-data.db "SELECT code FROM market ORDER BY code;"
# Expected: hkex, nyse, tse, warsaw
sqlite3 data/flux-data.db "SELECT m.code, c.symbol FROM company c JOIN market m ON c.market_id = m.id ORDER BY m.code, c.symbol;"
# Expected: all market/symbol combinations from CLAUDE.md seed data
sqlite3 data/flux-data.db "PRAGMA journal_mode;"
# Expected: wal
sqlite3 data/flux-data.db ".schema prices"
# Expected: shows company_id FK, indexes
kill %1
# Restart and verify idempotency — no duplicate rows
./mvnw spring-boot:run &
sleep 5
sqlite3 data/flux-data.db "SELECT COUNT(*) FROM market;"
# Expected: 4 (not 8)
kill %1
```
All checks must pass. Pay special attention to idempotency — second startup must not duplicate seed data.

---

## Task 3: Kafka Consumer & Deserialization

**Do:**
- KafkaConsumerConfig: configure deserializer for JSON → DataMessage DTO
- DataMessage DTO: symbol (String), price (Double), volume (Long), timestamp (String), market (String)
- DataConsumer with @KafkaListener on topic "data-stream", group "flux-warehouse"
- On receive: log "Received {symbol} from {market}" at DEBUG level
- On deserialization error: log warning, skip (don't crash)

**Verify:**
```bash
# Start Kafka
docker run -d --name flux-kafka -p 9092:9092 apache/kafka:latest
sleep 10
./mvnw clean compile
./mvnw spring-boot:run &
sleep 5
# Publish a test message to Kafka
docker exec flux-kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic data-stream <<< \
  '{"symbol":"PKO","price":50.0,"volume":1000,"timestamp":"2025-03-15T10:00:00Z","market":"warsaw"}'
sleep 2
# Check app logs for "Received PKO from warsaw"
# Publish malformed message
docker exec flux-kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic data-stream <<< \
  'not json at all'
sleep 2
# App must NOT crash — check logs for deserialization warning, app still running
curl -s http://localhost:8082/api/health       # still UP
kill %1
docker rm -f flux-kafka
```
Consumer must receive valid messages and survive bad ones.

---

## Task 4: DataService — Ingestion & Persistence

**Do:**
- DataService: processMessage(DataMessage) method
  - Validate required fields (symbol, price, volume, timestamp, market all present)
  - Lookup company by symbol + market code (cache this map in memory at startup — it's static)
  - If company not found → log warning, skip, increment failure metric
  - INSERT into prices table with resolved company_id
- Wire DataConsumer → DataService.processMessage()
- MetricsService: counters for consumed, saved, failed via Micrometer MeterRegistry

**Verify:**
```bash
docker run -d --name flux-kafka -p 9092:9092 apache/kafka:latest
sleep 10
rm -f data/flux-data.db   # fresh DB
./mvnw clean compile
./mvnw spring-boot:run &
sleep 5
# Send valid message
docker exec flux-kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic data-stream <<< \
  '{"symbol":"PKO","price":50.0,"volume":1000,"timestamp":"2025-03-15T10:00:00Z","market":"warsaw"}'
sleep 2
sqlite3 data/flux-data.db "SELECT p.price, p.volume, c.symbol FROM prices p JOIN company c ON p.company_id = c.id;"
# Expected: 50.0|1000|PKO
# Send message with unknown symbol
docker exec flux-kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic data-stream <<< \
  '{"symbol":"UNKNOWN","price":10.0,"volume":100,"timestamp":"2025-03-15T10:00:00Z","market":"warsaw"}'
sleep 2
# Check logs for warning about unknown symbol, no crash
# Send message with missing field
docker exec flux-kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic data-stream <<< \
  '{"symbol":"PKO","market":"warsaw"}'
sleep 2
# Check logs for validation warning, no crash
# Verify metrics
curl -s http://localhost:8082/actuator/metrics/warehouse.records.saved | grep -o '"value":[0-9.]*'
# Expected: value >= 1
curl -s http://localhost:8082/actuator/metrics/warehouse.records.failed | grep -o '"value":[0-9.]*'
# Expected: value >= 2
kill %1
docker rm -f flux-kafka
```
Valid messages persisted. Invalid ones logged and skipped. Metrics reflect reality.

---

## Task 5: Query Endpoint

**Do:**
- QueryController: GET /api/query with params: market (required), symbol (optional), limit (optional, default 100)
- QueryService: execute JOIN query per CLAUDE.md query section, return List<DataDTO>
- DataDTO: symbol, market, price, volume, timestamp
- Timeout on query: 5 seconds
- Unknown market → 400, DB error → 503, timeout → 504

**Verify:**
```bash
docker run -d --name flux-kafka -p 9092:9092 apache/kafka:latest
sleep 10
rm -f data/flux-data.db
./mvnw clean compile
./mvnw spring-boot:run &
sleep 5
# Insert test data via Kafka
for i in 1 2 3; do
  docker exec flux-kafka /opt/kafka/bin/kafka-console-producer.sh \
    --bootstrap-server localhost:9092 --topic data-stream <<< \
    "{\"symbol\":\"PKO\",\"price\":$((50+i)).0,\"volume\":${i}000,\"timestamp\":\"2025-03-15T10:0${i}:00Z\",\"market\":\"warsaw\"}"
done
sleep 3
# Query with market + symbol
curl -s "http://localhost:8082/api/query?market=warsaw&symbol=PKO&limit=10" | python3 -m json.tool
# Expected: 3 records, ordered by id DESC (newest first)
# Query with market only (no symbol)
curl -s "http://localhost:8082/api/query?market=warsaw" | python3 -m json.tool
# Expected: same 3 records
# Query with unknown market
curl -s -o /dev/null -w "%{http_code}" "http://localhost:8082/api/query?market=fake"
# Expected: 400
# Query with valid market, no data
curl -s "http://localhost:8082/api/query?market=nyse&symbol=AAPL"
# Expected: 200, empty list []
kill %1
docker rm -f flux-kafka
```
All query variations return expected results and status codes.

---

## Task 6: Error Handling

**Do:**
- GlobalExceptionHandler @ControllerAdvice for HTTP errors per CLAUDE.md error table
- Response body: `{"timestamp": "...", "status": N, "message": "..."}`
- ConsumerErrorHandler: ensure all consumer exceptions are caught, logged, and skipped (review DataConsumer + DataService for uncaught paths)

**Verify:**
```bash
./mvnw clean compile
./mvnw spring-boot:run &
sleep 5
# Bad query params
curl -s "http://localhost:8082/api/query" | python3 -m json.tool
# Expected: 400 with structured error JSON (missing market param)
# Verify error response has timestamp, status, message fields
curl -s "http://localhost:8082/api/query?market=fake" | python3 -m json.tool
# Expected: 400 with message about unknown market
kill %1
```
All error responses are structured JSON with correct status codes.

---

## Task 7: Tests

**Do:**
- DatabaseInitializerTest: verify tables exist (query sqlite_master), seed data present, idempotent on second run
- DataServiceTest: mock/in-memory DB — valid message → saved; missing field → skipped; unknown symbol → skipped
- QueryControllerTest: @SpringBootTest + WebTestClient — valid query → 200 with data; bad market → 400; empty result → 200 []
- KafkaConsumerTest: Testcontainers Kafka — publish message → assert row in prices table + metrics incremented
- All tests must use test-specific DB (not data/flux-data.db) to avoid pollution

**Verify:**
```bash
./mvnw clean verify
# ALL tests pass, 0 failures, 0 errors
# Check coverage makes sense (not a hard gate, but review)
./mvnw clean verify -pl . 2>&1 | tail -20
```
`./mvnw clean verify` exits 0 with all tests green. This is the final gate — if this passes, commit.
