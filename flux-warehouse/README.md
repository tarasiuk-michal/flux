# flux-warehouse — Kafka Consumer & SQLite Persistence

Spring Boot WebFlux service that consumes market data from Kafka, validates, and persists to SQLite with JPA. Exposes query API for retrieving historical prices.

## Features

✅ **Kafka Consumer** — Consumes `data-stream` topic with error resilience
✅ **Validated Ingestion** — Required fields, market/symbol lookup, unknown symbol handling
✅ **Normalized Schema** — market → company → prices (4 × 23 seed data)
✅ **Query API** — `GET /api/query?market=warsaw&symbol=PKO&limit=100`
✅ **Metrics** — Consumed, saved, failed counters + latency timer
✅ **SQLite Optimizations** — WAL journal mode, compound indexes, in-memory company cache

## Architecture

```
Kafka topic "data-stream"
     │
     │ {symbol, price, volume, timestamp, market}
     ▼
┌─────────────────────────┐
│ DataConsumer            │ @KafkaListener on data-stream
│ (error resilient)       │ → DataService.processMessage()
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│ DataService             │ Validate + Lookup company (cached)
│ (in-memory cache)       │ → PriceRepository.save()
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│ SQLite (data/flux-data.db)  │
│ market ─ company ─ prices   │ 4 markets × 23 companies
└─────────────────────────┘
           │
           ▼
GET /api/query
```

## Building

### Compile
```bash
mvn clean compile
```

### Run Tests
```bash
mvn test              # DataServiceTest: 7 unit tests
mvn clean verify      # Full build + tests + package
```

### Start Application
```bash
mvn spring-boot:run   # Starts on port 8082, waits for Kafka messages
```

## Configuration

Edit `src/main/resources/application.yml`:

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092      # KAFKA_BOOTSTRAP env var
    consumer:
      group-id: flux-warehouse
      auto-offset-reset: earliest
    topic: data-stream
  datasource:
    url: jdbc:sqlite:data/flux-data.db
    driver-class-name: org.sqlite.JDBC

server:
  port: 8082
```

## Kafka Message Format

```json
{
  "symbol": "PKO",
  "price": 50.0,
  "volume": 1000,
  "timestamp": "2025-03-15T10:00:00Z",
  "market": "warsaw"
}
```

## API Endpoints

### Health Check
```bash
curl http://localhost:8082/api/health
# {"status": "UP"}
```

### Query Prices
```bash
# With symbol
curl "http://localhost:8082/api/query?market=warsaw&symbol=PKO&limit=10"

# All symbols in market
curl "http://localhost:8082/api/query?market=nyse"

# Response
[
  {
    "symbol": "PKO",
    "market": "warsaw",
    "price": 50.0,
    "volume": 1000,
    "timestamp": "2025-03-15T10:00:00Z"
  },
  ...
]
```

### Query Parameters

| Param | Type | Required | Default | Notes |
|-------|------|----------|---------|-------|
| `market` | String | Yes | — | warsaw, nyse, tse, hkex |
| `symbol` | String | No | — | e.g., PKO, AAPL, 9984 |
| `limit` | Integer | No | 100 | Max 1000, results DESC by id |

### Response Codes

| Code | Scenario |
|------|----------|
| `200` | Success (may be empty list) |
| `400` | Missing/invalid params, unknown market |
| `503` | Database error |
| `504` | Query timeout (5 sec) |

## Database Schema

### market
```sql
CREATE TABLE market (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  code TEXT NOT NULL UNIQUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Seed data** (4 rows):
- warsaw → Warsaw Stock Exchange
- nyse → New York Stock Exchange
- tse → Tokyo Stock Exchange
- hkex → Hong Kong Exchanges and Clearing

### company
```sql
CREATE TABLE company (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  symbol TEXT NOT NULL,
  name TEXT,
  market_id INTEGER NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (market_id) REFERENCES market(id),
  UNIQUE(symbol, market_id)
);
```

**Seed data** (23 rows, idempotent on startup):
- warsaw (8): PKO, PKOBP, ASSECOPOL, GPWT, KGHM, PKNORLEN, PGEPL, TAURONPE
- nyse (8): AAPL, MSFT, TSLA, GOOGL, AMZN, META, NVDA, JPM
- tse (4): 9984, 6758, 7203, 8031
- hkex (3): 700, 3988, 1000

### prices
```sql
CREATE TABLE prices (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  company_id INTEGER NOT NULL,
  price REAL NOT NULL,
  volume BIGINT NOT NULL,
  timestamp TEXT NOT NULL (ISO-8601),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (company_id) REFERENCES company(id)
);

CREATE INDEX idx_prices_company_id ON prices(company_id);
CREATE INDEX idx_prices_timestamp ON prices(timestamp);
```

## Metrics

Access via Actuator:

```bash
# Consumed from Kafka
curl http://localhost:8082/actuator/metrics/warehouse.records.consumed

# Successfully persisted
curl http://localhost:8082/actuator/metrics/warehouse.records.saved

# Validation/DB errors
curl http://localhost:8082/actuator/metrics/warehouse.records.failed

# Persistence latency (timer)
curl http://localhost:8082/actuator/metrics/warehouse.save.duration

# List all metrics
curl http://localhost:8082/actuator/metrics
```

## Error Handling

### Kafka Consumer Errors

| Error | Behavior |
|-------|----------|
| Deserialization (malformed JSON) | Log warning, skip message |
| Missing field (symbol, price, etc.) | Log warning, skip |
| Unknown market code | Log warning, skip |
| Unknown company (symbol+market) | Log warning, increment failed counter |
| DB locked / constraint violation | Log error, skip |

**Consumer never crashes.** All errors are logged and counted; message processing continues.

### HTTP API Errors

- **400 Bad Request**: Missing market param, invalid market code
- **503 Service Unavailable**: Database connection error
- **504 Gateway Timeout**: Query locked for >5 seconds

## Testing

### Unit Tests (7 passing)

```bash
mvn test -k DataServiceTest
```

Covers:
- Valid message → persisted with correct company_id
- Missing symbol → skipped
- Missing price → skipped
- Missing volume → skipped
- Missing timestamp → skipped
- Missing market → skipped
- Unknown company → skipped, failed counter incremented

### Full Build

```bash
mvn clean verify
```

Includes compilation, unit tests, and JAR packaging.

## Running with Mock Data

### 1. Start Kafka
```bash
docker run -d --name flux-kafka -p 9092:9092 apache/kafka:latest
```

### 2. Start warehouse
```bash
mvn spring-boot:run
```

### 3. Publish test message
```bash
docker exec flux-kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic data-stream <<< \
  '{"symbol":"PKO","price":50.0,"volume":1000,"timestamp":"2025-03-15T10:00:00Z","market":"warsaw"}'
```

### 4. Query
```bash
curl "http://localhost:8082/api/query?market=warsaw&symbol=PKO"
```

## Performance Notes

- **SQLite WAL mode**: Enables concurrent reads during writes
- **Synchronous NORMAL**: Faster than default FULL
- **HikariCP max-pool-size: 5**: SQLite prefers small pools
- **In-memory company cache**: Avoids repeated DB lookups during validation
- **Index on company_id + timestamp**: Fast joins and filtering

## Troubleshooting

| Issue | Diagnosis | Fix |
|-------|-----------|-----|
| "database is locked" | Multiple writer threads | Single KafkaListener thread (concurrency=1) |
| Query returns empty | Generator not running | Start flux-generator |
| "Unknown market" error | Typo in market param | Use: warsaw, nyse, tse, hkex |
| Health check fails | Kafka not running | `docker run ... apache/kafka` |
| No errors in logs | Consumer might be on different offset | Check Kafka offset: `docker exec flux-kafka ...` |

## Project Structure

```
flux-warehouse/
├── pom.xml                                    # Maven config
├── src/main/
│   ├── java/com/flux/warehouse/
│   │   ├── FluxWarehouseApplication.java
│   │   ├── config/
│   │   │   ├── DatabaseInitializer.java      # Schema + seed data
│   │   │   └── KafkaConsumerConfig.java
│   │   ├── consumer/
│   │   │   └── DataConsumer.java             # @KafkaListener
│   │   ├── controller/
│   │   │   ├── HealthController.java
│   │   │   └── QueryController.java
│   │   ├── dto/
│   │   │   └── DataDTO.java
│   │   ├── exception/
│   │   │   ├── GlobalExceptionHandler.java
│   │   │   └── ErrorResponse.java
│   │   ├── model/
│   │   │   ├── Market.java                   # JPA entity
│   │   │   ├── Company.java
│   │   │   ├── Price.java
│   │   │   └── DataMessage.java              # Kafka DTO
│   │   ├── repository/
│   │   │   ├── MarketRepository.java
│   │   │   ├── CompanyRepository.java
│   │   │   └── PriceRepository.java
│   │   └── service/
│   │       ├── DataService.java              # Validation + persistence
│   │       ├── MetricsService.java
│   │       └── QueryService.java             # Query JOIN logic
│   └── resources/
│       └── application.yml
├── src/test/java/
│   └── com/flux/warehouse/
│       ├── config/DatabaseInitializerTest.java
│       ├── consumer/KafkaConsumerTest.java
│       ├── controller/QueryControllerTest.java
│       └── service/DataServiceTest.java
└── data/flux-data.db                         # SQLite database (created at runtime)
```

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| spring-boot-starter-webflux | 4.0.3 | Reactive HTTP |
| spring-boot-starter-data-jpa | 4.0.3 | ORM |
| spring-kafka | 4.0.3 | Kafka consumer |
| sqlite-jdbc | 3.45.0.0 | SQLite driver |
| spring-boot-starter-actuator | 4.0.3 | Metrics |
| junit-jupiter | — | Testing |
| testcontainers | 1.19.7 | Integration tests |

## Deployment

### Docker

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/flux-warehouse-1.0.0.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
mvn clean package
docker build -t flux-warehouse:latest .
docker run -e KAFKA_BOOTSTRAP=kafka:9092 -p 8082:8082 flux-warehouse:latest
```

## License

MIT
