# Flux Warehouse — CLAUDE.md

## What This Is
Spring Boot WebFlux Kafka consumer. Reads market data from Kafka topic "data-stream", persists to a normalized SQLite schema (market → company → prices). Exposes query endpoint for flux-gateway to fetch stored data. Error strategy: log and skip.

## Tech Stack
- Java 21, Spring Boot 4.0.x (Spring Framework 7)
- Spring Kafka 4.x (KafkaListener — **not** reactor-kafka, it's been discontinued)
- SQLite + Spring Data JPA (Hibernate, SQLite dialect)
- Spring Boot Actuator + Micrometer 2.x
- Maven

## Architecture
```
Kafka topic "data-stream"
  → KafkaListener receives messages
  → Validate (required fields, known market/symbol)
  → Resolve market → company via normalized schema
  → INSERT into prices table (FK to company)
  → Update metrics
  → On error: log, skip, continue

GET /api/query?market=warsaw&symbol=PKO&limit=100
  → JOIN prices → company → market
  → Return results (called by flux-gateway)
```

## Configuration (application.yml)
```yaml
spring:
  webflux:
    base-path: /api
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9092}
    consumer:
      group-id: flux-warehouse
      auto-offset-reset: earliest
      properties:
        max.poll.records: 500
    topic: data-stream
  datasource:
    url: jdbc:sqlite:data/flux-data.db
    driver-class-name: org.sqlite.JDBC
    hikari:
      maximum-pool-size: 5
  jpa:
    hibernate:
      ddl-auto: validate

server:
  port: 8082

management:
  endpoints:
    web:
      exposure:
        include: health,metrics
```

## Package Structure
```
com.flux.warehouse
├── config/          # KafkaConsumerConfig, DatabaseInitializer
├── consumer/        # DataConsumer (KafkaListener)
├── service/         # DataService, QueryService, MetricsService
├── repository/      # MarketRepository, CompanyRepository, PriceRepository (Spring Data JPA)
├── model/           # Market, Company, Price (JPA entities), DataMessage (Kafka DTO)
├── dto/             # DataDTO (query response)
└── exception/       # GlobalExceptionHandler, ConsumerErrorHandler
```

## Gotchas

**SQLite single-writer** — SQLite handles one writer at a time. Don't use multiple consumer threads. Single KafkaListener thread is fine. Multiple → "database is locked" errors.

**reactor-kafka is discontinued** — Use Spring Kafka's `@KafkaListener` annotation. Wrap blocking JPA calls with `@Async` or accept that the consumer thread blocks on write (acceptable for single-threaded SQLite).

**Schema initialization at startup** — Pre-seed `market` and `company` rows in `@PostConstruct` or via Flyway/Liquibase. The `prices` table and FKs must exist before first Kafka message arrives. Don't create schema dynamically during message processing.

**SQLite PRAGMAs** — Set in DatabaseInitializer at startup:
```sql
PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
```
WAL enables concurrent reads during writes. NORMAL is faster than default FULL.

**Log and skip errors** — Failed writes (bad data, DB errors) are logged but never crash the consumer. Use try-catch in listener, increment failure counter, continue.

**Timestamps** — Stored as ISO-8601 TEXT in SQLite. Don't parse/format in persistence layer.

**HikariCP pool** — Max 5 connections. SQLite doesn't benefit from more. Higher values increase lock contention.

**Query endpoint during writes** — SQLite may lock. Add timeout on queries to avoid hanging. Index `company_id` and `timestamp` on `prices` table.

## Schema
Normalized 3-table design. Created at startup, seeded with known markets and companies.

```sql
CREATE TABLE IF NOT EXISTS market (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,          -- e.g. "Warsaw Stock Exchange"
    code TEXT NOT NULL UNIQUE,   -- e.g. "warsaw", "nyse", "tse", "hkex"
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS company (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    symbol TEXT NOT NULL,        -- e.g. "PKO", "AAPL", "9984"
    name TEXT,                   -- e.g. "PKO Bank Polski", "Apple Inc."
    market_id INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (market_id) REFERENCES market(id),
    UNIQUE(symbol, market_id)
);

CREATE TABLE IF NOT EXISTS prices (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    price REAL NOT NULL,
    volume BIGINT NOT NULL,
    timestamp TEXT NOT NULL,     -- ISO-8601
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES company(id)
);

CREATE INDEX IF NOT EXISTS idx_prices_company_id ON prices(company_id);
CREATE INDEX IF NOT EXISTS idx_prices_timestamp ON prices(timestamp);
```

## Seed Data
Pre-insert at startup (same markets/symbols as sender):
- **warsaw**: PKO, PKOBP, ASSECOPOL, GPWT, KGHM, PKNORLEN, PGEPL, TAURONPE
- **nyse**: AAPL, MSFT, TSLA, GOOGL, AMZN, META, NVDA, JPM
- **tse**: 9984 (SoftBank), 6758 (Sony), 7203 (Toyota), 8031 (Mitsui)
- **hkex**: 700 (Tencent), 3988 (Bank of China), 1000 (HK Property)

## Ingestion Logic
Kafka message `{market: "warsaw", symbol: "PKO", price: 50.0, volume: 1000, timestamp: "..."}`:
1. Lookup `company` by `symbol` + `market.code` (cache in memory — these don't change at runtime)
2. If not found → log warning, skip
3. INSERT into `prices` with resolved `company_id`

## API Endpoints
| Method | Path | Description |
|--------|------|-------------|
| GET | /api/health | Health check |
| GET | /api/query | Query prices (params: `market` required, `symbol` optional, `limit` optional default 100) |

## Query Endpoint Behavior
```sql
-- With symbol
SELECT p.price, p.volume, p.timestamp, c.symbol, m.code AS market
FROM prices p
JOIN company c ON p.company_id = c.id
JOIN market m ON c.market_id = m.id
WHERE m.code = ? AND c.symbol = ?
ORDER BY p.id DESC LIMIT ?

-- Without symbol (all companies in market)
-- Same query without c.symbol filter
```
- Valid query → 200 with results
- No records → 200 with empty list
- Unknown market → 400
- Query timeout (DB locked) → 504
- DB error → 503

## Metrics (Actuator)
- `warehouse.records.consumed` — counter (received from Kafka)
- `warehouse.records.saved` — counter (persisted)
- `warehouse.records.failed` — counter (validation/DB errors)
- `warehouse.save.duration` — timer

## Error Handling
**Consumer errors** (Kafka listener):
- Deserialization failure → log warning, skip
- Validation failure (missing fields) → log warning, skip
- Unknown market/symbol → log warning, skip
- DB error (locked, constraint) → log error, skip
- Never crash the consumer

**HTTP errors** (query endpoint):
| Exception | Status |
|-----------|--------|
| Bad query params | 400 |
| Query timeout | 504 |
| DB access error | 503 |
| Generic | 500 |

## Testing
- DatabaseInitializerTest: verify market/company/prices tables exist, seed data present
- DataServiceTest: valid message → price row saved with correct company_id; missing field → skipped; unknown market/symbol → skipped
- QueryControllerTest: valid query → results with JOINed data; bad market → 400; timeout → 504; empty → 200 []
- KafkaConsumerTest: Testcontainers Kafka, publish message → verify price saved to DB + metrics updated

## Running
```bash
./mvnw spring-boot:run
curl http://localhost:8082/api/health
curl "http://localhost:8082/api/query?market=warsaw&symbol=PKO&limit=100"
curl http://localhost:8082/actuator/metrics/warehouse.records.consumed
```

## Startup Order
1. Kafka broker
2. flux-gateway (8081)
3. flux-warehouse (8082) — waits for Kafka messages
4. flux-generator (8080) — starts sending

## Monitoring
- SQLite file: `data/flux-data.db` (grows with records)
- Watch for: "database is locked" errors, save latency spikes, consumer lag
