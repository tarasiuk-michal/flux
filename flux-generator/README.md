# flux-generator — Market Data Publisher

Spring Boot service that generates mock market data and publishes to Kafka topic `data-stream` for consumption by flux-warehouse.

## Status

🔨 **In Progress** — Core structure scaffolding

## Planned Features

- [ ] Mock OHLCV data generation for 4 markets × 23 securities
- [ ] Configurable publish rate (RPS)
- [ ] Load test mode with latency percentiles
- [ ] Realistic market simulation (bid/ask spreads, volumes)
- [ ] Scheduled data generation
- [ ] Start/stop/status API endpoints

## Configuration

**Port**: 8080
**Kafka Topic**: `data-stream`

### Message Format

```json
{
  "symbol": "PKO",
  "price": 50.0,
  "volume": 1000,
  "timestamp": "2025-03-15T10:00:00Z",
  "market": "warsaw"
}
```

## Building

```bash
mvn clean compile
mvn spring-boot:run
```

## Dependencies

- Spring Boot WebFlux
- Spring Kafka (KafkaTemplate)
- Spring Boot Actuator

## See Also

- [flux-warehouse](../flux-warehouse/README.md) — Consumer service
- [flux-gateway](../flux-gateway/README.md) — API gateway
- [Main README](../README.md) — Architecture overview
