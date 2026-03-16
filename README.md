# Flux: High-Throughput Market Data Pipeline

Distributed system for market data ingestion, persistence, and querying. Three Spring Boot 4 services: generator creates mock market data, gateway routes requests, warehouse consumes from Kafka and persists to SQLite.

## Architecture

```
flux-generator (8080)          flux-warehouse (8082)
  Market Data Source             Kafka Consumer
       │                              │
       │  Kafka: data-stream          │
       └─────────────────────────────►│
                                      │  Validate & Persist
                                      │  (SQLite: market → company → prices)
                                      │
                        ┌─────────────┘
                        │
                  GET /api/query
                  flux-gateway (8081)
```

## Tech Stack

- Java 21, Spring Boot 4.0.x, Spring Framework 7
- Spring WebFlux (Netty) — Reactive HTTP
- Spring Kafka 4.x — @KafkaListener message consumption
- SQLite + Spring Data JPA — Normalized schema with indexes
- Micrometer 2.x + Actuator — Metrics & monitoring
- Maven multi-module build

## Prerequisites

- Java 21+
- Maven 3.8+
- Kafka broker (localhost:9092)
- Docker (optional, for Kafka: `docker run -d -p 9092:9092 apache/kafka:latest`)

## Quick Start (Docker)

**Fastest way to run the entire system with Kafka, all three services, and proper networking:**

```bash
# Deploy everything (builds images, starts containers)
./scripts/deploy.sh

# Verify services are running
curl http://localhost:8880/api/health     # warehouse
curl http://localhost:8881/api/health     # gateway
curl http://localhost:8882/api/health     # generator
```

See [INTEGRATION_GUIDE.md](./INTEGRATION_GUIDE.md) for full Docker Compose guide, networking modes, configuration, and troubleshooting.

## Quick Start (Local Development)

**Run modules directly without Docker (requires Kafka running):**

### 1. Start Kafka
```bash
docker run -d --name flux-kafka -p 9092:9092 apache/kafka:latest
sleep 10
```

### 2. Build & Run

**flux-warehouse** (8880 — consumes Kafka, serves queries):
```bash
cd flux-warehouse
mvn spring-boot:run
```

**flux-generator** (8882 — publishes mock data):
```bash
cd flux-generator
mvn spring-boot:run
```

**flux-gateway** (8881 — API gateway):
```bash
cd flux-gateway
mvn spring-boot:run
```

### 3. Verify

```bash
# Warehouse health
curl http://localhost:8880/api/health

# Query data (once generator has published)
curl "http://localhost:8880/api/query?market=warsaw&symbol=PKO&limit=10"
```

## Modules

| Module | Port | Status | Role |
|--------|------|--------|------|
| **flux-warehouse** | 8880 | ✅ Complete | Kafka consumer → SQLite persistence → Query API |
| **flux-gateway** | 8881 | ✅ Complete | HTTP gateway, Kafka producer, warehouse proxy |
| **flux-generator** | 8882 | ✅ Complete | Load testing tool for market data generation |

### flux-warehouse (Complete)

- **Input**: Kafka topic `data-stream` — JSON messages with `{symbol, price, volume, timestamp, market}`
- **Processing**: Validate required fields → Lookup company from cache → Persist to `prices` table
- **Schema**: Normalized (4 markets × 23 companies = 92 base records)
- **Endpoints**:
  - `GET /api/health` — `{"status": "UP"}`
  - `GET /api/query?market=warsaw&symbol=PKO&limit=100` — Results ordered by DESC id
- **Metrics**:
  - `warehouse.records.consumed` — Kafka message count
  - `warehouse.records.saved` — Successful inserts
  - `warehouse.records.failed` — Validation/DB errors
  - `warehouse.save.duration` — Persistence latency

See [flux-warehouse/README.md](./flux-warehouse/README.md) for details.

## Database Schema

### Tables
```sql
market (id, name, code, created_at) [UNIQUE(code)]
company (id, symbol, name, market_id, created_at) [UNIQUE(symbol, market_id), FK market]
prices (id, company_id, price, volume, timestamp, created_at) [FK company]
  [INDEX: company_id, timestamp]
```

### Seed Data
- **warsaw** (8): PKO, PKOBP, ASSECOPOL, GPWT, KGHM, PKNORLEN, PGEPL, TAURONPE
- **nyse** (8): AAPL, MSFT, TSLA, GOOGL, AMZN, META, NVDA, JPM
- **tse** (4): 9984, 6758, 7203, 8031
- **hkex** (3): 700, 3988, 1000

**Total**: 4 markets, 23 companies, initialized on startup with WAL journal mode for concurrency.

## Configuration

Each module has `application.yml`:

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9092}
  datasource:
    url: jdbc:sqlite:data/flux-data.db
    driver-class-name: org.sqlite.JDBC

server:
  port: 8880  # warehouse; 8881=gateway, 8882=generator
```

Override with environment variables:
```bash
KAFKA_BOOTSTRAP=kafka.prod.internal:9092 mvn spring-boot:run
```

## Error Handling

**Consumer** (Kafka):
- Deserialization failure → Log warning, skip
- Missing fields → Log warning, skip
- Unknown market/symbol → Log warning, skip
- DB locked/constraint → Log error, skip

**API** (HTTP):
- Missing required params → `400 Bad Request` + structured JSON
- Unknown market → `400 Bad Request`
- DB error → `503 Service Unavailable`
- Query timeout (5 sec) → `504 Gateway Timeout`

## Monitoring

```bash
# Warehouse metrics
curl http://localhost:8880/actuator/metrics/warehouse.records.consumed
curl http://localhost:8880/actuator/metrics/warehouse.records.saved
curl http://localhost:8880/actuator/metrics/warehouse.records.failed

# List all available metrics
curl http://localhost:8880/actuator/metrics
```

## Testing

**Unit tests** (warehouse):
```bash
cd flux-warehouse
mvn test  # DataServiceTest: 7 tests passing
```

**Integration tests** (full build):
```bash
mvn clean verify  # Compile + unit tests + package
```

## Startup Order

1. **Kafka broker** — `docker run ... apache/kafka`
2. **flux-warehouse** — Starts consuming, waits for messages
3. **flux-generator** — Publishes mock data (optional)
4. **flux-gateway** — API proxy (optional)

## Development

### Build
```bash
mvn clean compile          # Compile only
mvn clean install          # Full build (compile + test + package)
mvn clean verify           # Compile + tests + package
```

### Adding Data (Kafka)
```bash
docker exec flux-kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic data-stream <<< \
  '{"symbol":"PKO","price":50.0,"volume":1000,"timestamp":"2025-03-15T10:00:00Z","market":"warsaw"}'
```

### Debugging
```bash
# Watch SQLite in real-time
sqlite3 data/flux-data.db ".tables"
sqlite3 data/flux-data.db "SELECT COUNT(*) FROM prices;"

# Check warehouse logs
tail -f target/spring.log
```

## Future Enhancements

- [ ] Dead-letter topic for poisoned messages
- [ ] OpenTelemetry distributed tracing
- [ ] Multi-instance warehouse with Kafka consumer groups
- [ ] Real-time WebSocket subscriptions
- [ ] Kubernetes deployment
