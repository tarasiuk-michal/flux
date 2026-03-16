# Flux Integration Guide

Complete guide for deploying and testing the Flux distributed market data system with Docker Compose.

## Architecture Overview

The Flux system consists of three Spring Boot microservices communicating via Kafka and HTTP:

```
flux-generator (8882)
    │
    ├─→ POST /api/data → flux-gateway (8881)
    │                        │
    │                        ├─→ PUBLISH → Kafka "data-stream"
    │                        │
    │                        ├─→ GET /api/query → flux-warehouse (8880)
    │
    └─→ Query market data via gateway

flux-warehouse (8880)
    │
    ├─→ SUBSCRIBE to Kafka "data-stream"
    │
    └─→ PERSIST to SQLite data/flux-data.db
```

**Services:**
- **flux-warehouse** (port 8880): Kafka consumer, SQLite persistence, query endpoint
- **flux-gateway** (port 8881): API gateway, Kafka producer, warehouse proxy
- **flux-generator** (port 8882): Load testing tool for generating market data
- **Kafka** (port 9092): Message broker (internal to Docker network)
- **Zookeeper** (port 2181): Kafka coordinator (internal to Docker network)

## Prerequisites

- Docker (20.10+)
- Docker Compose (2.0+)
- Java 21+ (for local testing, optional for Docker builds)
- Maven (optional, mvnw provided in each module)
- Curl (for testing endpoints)

## Quick Start

### 1. Deploy with Docker Compose

```bash
cd /home/precluch/JetBrains/IdeaProjects/flux

# Review .env configuration
cat .env

# Deploy services (builds images, starts containers)
./scripts/deploy.sh
```

This script will:
- Validate Docker and Docker Compose installation
- Build Docker images for all three services
- Start Kafka, Zookeeper, and all three services
- Wait for services to become healthy
- Display connection information

### 2. Verify Services Are Running

```bash
# Check Docker Compose status
docker-compose ps

# Health checks
curl http://localhost:8880/api/health  # warehouse
curl http://localhost:8881/api/health  # gateway
curl http://localhost:8882/api/health  # generator
```

### 3. Test Data Flow

```bash
# Generate test data (via generator)
curl -X POST http://localhost:8882/api/load-test/start \
  -H "Content-Type: application/json" \
  -d '{
    "requestsPerSecond": 5,
    "durationSeconds": 10,
    "concurrency": 2
  }'

# Query persisted data (via gateway → warehouse)
curl "http://localhost:8881/api/query?market=warsaw&symbol=PKO&limit=10"

# View Kafka topic (optional)
docker-compose exec kafka kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 \
  --topic data-stream \
  --from-beginning \
  --max-messages 5
```

### 4. View Logs

```bash
# All services
docker-compose logs -f

# Individual service
docker-compose logs -f flux-warehouse
docker-compose logs -f flux-gateway
docker-compose logs -f flux-generator
docker-compose logs -f kafka
```

### 5. Run Tests

```bash
# Unit and integration tests for all modules
./scripts/test.sh

# Individual module tests
cd flux-warehouse && mvn clean verify
cd flux-gateway && mvn clean verify
cd flux-generator && mvn clean verify
```

### 6. Stop Services

```bash
# Stop but keep volumes (state persists)
docker-compose stop

# Stop and remove containers/networks (state preserved)
docker-compose down

# Complete cleanup (removes volumes, data loss!)
./scripts/teardown.sh
```

## Network Modes

### Local Mode (Default)

**Use for:** Local development, testing

**Configuration:**
```bash
# .env
NETWORK_MODE=local
```

**Service URLs:**
- Warehouse: `http://localhost:8880/api`
- Gateway: `http://localhost:8881/api`
- Generator: `http://localhost:8882/api`

**How it works:**
- Services communicate via Docker bridge network using service names (dns)
- Kafka: `kafka:9092` (internal to Docker network)
- Ports exposed to localhost via port mappings

### Tailscale Mode

**Use for:** Cross-machine access, production

**Configuration:**
```bash
# .env
NETWORK_MODE=tailscale
MACHINE_FQDN=thinkpad.antelope-centauri.ts.net  # Your Tailscale FQDN
```

**Service URLs:**
```
Warehouse: http://thinkpad.antelope-centauri.ts.net:8880/api
Gateway:   http://thinkpad.antelope-centauri.ts.net:8881/api
Generator: http://thinkpad.antelope-centauri.ts.net:8882/api
```

**Requirements:**
- Machine must be on Tailscale network
- Tailscale ACL must allow access to ports 8880, 8881, 8882
- All client machines must have Tailscale access

**ACL Configuration:**
```yaml
# In Tailscale ACL (tailscale.com admin panel)
{
  "acls": [
    {
      "action": "accept",
      "src": ["tag:precluch"],
      "dst": ["tag:server:8880,8881,8882"]
    }
  ]
}
```

**Switching modes:**
```bash
# Edit .env
NETWORK_MODE=tailscale
MACHINE_FQDN=thinkpad.antelope-centauri.ts.net

# Restart services
docker-compose down
docker-compose up -d

# Verify
curl http://thinkpad.antelope-centauri.ts.net:8880/api/health
```

## API Reference

### flux-warehouse

**Health Check**
```bash
GET /api/health
```

**Query Endpoint** (proxy for historical data)
```bash
GET /api/query?market={market}&symbol={symbol}&limit={limit}

# Examples
curl "http://localhost:8880/api/query?market=warsaw&symbol=PKO&limit=100"
curl "http://localhost:8880/api/query?market=nyse"
curl "http://localhost:8880/api/query?market=tse&symbol=9984"
```

**Response:**
```json
[
  {
    "market": "warsaw",
    "symbol": "PKO",
    "price": 50.25,
    "volume": 1000,
    "timestamp": "2026-03-16T12:34:56Z"
  }
]
```

### flux-gateway

**Health Check**
```bash
GET /api/health
```

**Ingest Data Endpoint** (produces to Kafka)
```bash
POST /api/data
Content-Type: application/json

{
  "market": "warsaw",
  "symbol": "PKO",
  "price": 50.25,
  "volume": 1000,
  "timestamp": "2026-03-16T12:34:56Z"
}
```

**Query Endpoint** (proxies to warehouse)
```bash
GET /api/query?market={market}&symbol={symbol}&limit={limit}
```

### flux-generator

**Health Check**
```bash
GET /api/health
```

**Start Load Test**
```bash
POST /api/load-test/start
Content-Type: application/json

{
  "requestsPerSecond": 5,
  "durationSeconds": 30,
  "concurrency": 2
}
```

**Get Test Results**
```bash
GET /api/load-test/results/{testId}

# Response
{
  "testId": "abc123",
  "totalRequests": 150,
  "successfulRequests": 148,
  "failedRequests": 2,
  "averageLatency": 245,
  "p95Latency": 512,
  "p99Latency": 789,
  "throughput": 5.0,
  "status": "COMPLETED"
}
```

## Configuration Files

### .env

Environment variables for docker-compose:
```bash
NETWORK_MODE=local|tailscale
MACHINE_FQDN=your-tailscale-fqdn.ts.net
KAFKA_BOOTSTRAP=kafka:9092
FLUX_API_KEY=your-api-key
```

### docker-compose.yml

- **Zookeeper**: Kafka coordinator (port 2181, internal only)
- **Kafka**: Message broker (port 9092, internal only)
- **flux-warehouse**: Spring Boot service (port 8880)
- **flux-gateway**: Spring Boot service (port 8881)
- **flux-generator**: Spring Boot service (port 8882)

**Networks:**
- `flux-network`: Docker bridge network (internal service-to-service communication)
- `kafka:9092`: Internal Kafka broker endpoint
- `localhost:8880-8882`: External ports (host machine access)

**Volumes:**
- `kafka-data`: Kafka message persistence
- `warehouse-data`: SQLite database directory

### application.yml

Each module has Spring Boot configuration:

**flux-warehouse** (`src/main/resources/application.yml`):
```yaml
server.port: 8880
spring.kafka.bootstrap-servers: kafka:9092
spring.datasource.url: jdbc:sqlite:data/flux-data.db
app.network-mode: ${NETWORK_MODE:local}
app.api-key: ${FLUX_API_KEY:test-key-change-me}
```

**flux-gateway** (`src/main/resources/application.yml`):
```yaml
server.port: 8881
spring.kafka.bootstrap-servers: kafka:9092
app.warehouse-url: Resolved from NETWORK_MODE
app.api-key: ${FLUX_API_KEY:test-key-change-me}
```

**flux-generator** (`src/main/resources/application.yml`):
```yaml
server.port: 8882
app.gateway-url: Resolved from NETWORK_MODE
app.api-key: ${FLUX_API_KEY:test-key-change-me}
```

## Troubleshooting

### Services won't start

```bash
# Check logs
docker-compose logs

# Check for port conflicts
lsof -i :8880
lsof -i :8881
lsof -i :8882
lsof -i :9092

# Rebuild images
docker-compose down
docker image rm flux-warehouse flux-gateway flux-generator
./scripts/deploy.sh
```

### "Connection refused" errors

```bash
# In local mode, use localhost:
curl http://localhost:8880/api/health

# In Tailscale mode, use your FQDN:
curl http://thinkpad.antelope-centauri.ts.net:8880/api/health

# Check if service is running
docker-compose ps
```

### Kafka topic not found

```bash
# Create topic manually
docker-compose exec kafka kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create \
  --topic data-stream \
  --partitions 3 \
  --replication-factor 1
```

### Database locked errors

- SQLite allows only one writer at a time
- Ensure only one instance of flux-warehouse is running
- Check logs: `docker-compose logs flux-warehouse | grep "database is locked"`

### High latency or timeouts

```bash
# Check Kafka lag
docker-compose exec kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group flux-warehouse \
  --describe

# Check service health
for svc in warehouse gateway generator; do
  curl -w "\n$svc: %{http_code}\n" http://localhost:888${i}/api/health
done
```

## Development Workflow

### Modify Source Code

```bash
# Edit source in any module
vim flux-warehouse/src/main/java/.../SomeClass.java

# Rebuild and restart
docker-compose up -d --build flux-warehouse

# Or full rebuild
./scripts/deploy.sh
```

### Run Tests

```bash
# All tests
./scripts/test.sh

# Specific module
cd flux-warehouse
mvn clean verify

# Specific test class
mvn test -Dtest=DataConsumerTest
```

### Debug with Logs

```bash
# Real-time logs from one service
docker-compose logs -f flux-gateway

# Last 100 lines with timestamp
docker-compose logs --tail=100 --timestamps flux-warehouse

# All services with timestamps
docker-compose logs -f --timestamps
```

### Database Inspection

```bash
# Direct SQLite access
docker-compose exec flux-warehouse sqlite3 data/flux-data.db

# In sqlite3 CLI
sqlite> SELECT COUNT(*) FROM prices;
sqlite> SELECT * FROM market;
sqlite> .schema prices
```

## Performance Tuning

### Kafka Consumer

Edit `flux-warehouse/src/main/resources/application.yml`:
```yaml
spring.kafka.consumer.properties.max.poll.records: 500  # Increase for higher throughput
```

### Database Connection Pool

Edit application.yml:
```yaml
spring.datasource.hikari.maximum-pool-size: 5  # Increase if connection timeouts
```

### API Timeouts

Edit application.yml:
```yaml
spring.webflux.base-path: /api
server.shutdown: graceful  # Graceful shutdown on restart
server.shutdown-wait-time: 20s
```

## Security Notes

### API Key

All endpoints should enforce `X-API-Key` header:
```bash
curl -H "X-API-Key: test-key-change-me" http://localhost:8880/api/health
```

Change the default in `.env`:
```bash
FLUX_API_KEY=$(openssl rand -hex 32)
```

### Tailscale ACL

Only allow tagged machines (precluch nodes):
```yaml
{
  "acls": [
    {
      "action": "accept",
      "src": ["tag:precluch"],
      "dst": ["tag:server:8880,8881,8882"]
    }
  ]
}
```

### Production Considerations

1. **Secrets Management**: Use Kubernetes Secrets or AWS Secrets Manager
2. **TLS/HTTPS**: Add reverse proxy (nginx, traefik)
3. **Monitoring**: Enable Prometheus metrics on actuator endpoints
4. **Logging**: Aggregate logs with ELK stack or cloud logging
5. **Scaling**: Move to Kubernetes for auto-scaling and orchestration

## Useful Docker Commands

```bash
# View container logs with timestamps
docker-compose logs -f --timestamps

# Enter container shell
docker-compose exec flux-warehouse /bin/bash

# Inspect running container
docker inspect flux-warehouse

# View container resource usage
docker stats flux-warehouse

# Remove all containers and volumes
docker-compose down -v

# Remove dangling images
docker image prune -a
```

## Next Steps

1. **Verify all services are healthy** using the health endpoints
2. **Test data ingestion** using the generator load test endpoint
3. **Query data** to verify end-to-end flow
4. **Run full test suite** with `./scripts/test.sh`
5. **Review logs** for any warnings or errors
6. **Switch to Tailscale mode** for cross-machine access (optional)
7. **Deploy to production** with TLS, proper secrets, and monitoring

## Support

For detailed module information, see:
- `flux-warehouse/CLAUDE.md` - Architecture and schema details
- `flux-gateway/CLAUDE.md` - API gateway configuration
- `flux-generator/CLAUDE.md` - Load testing tool documentation
- Individual module README files
