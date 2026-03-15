# flux-gateway — API Gateway & Router

Spring Boot WebFlux API gateway for the Flux system. Routes requests to downstream services (warehouse for queries), handles request validation and authentication.

## Status

🔨 **In Progress** — Core structure scaffolding

## Planned Features

- [ ] HTTP routing to flux-warehouse query endpoint
- [ ] Request validation middleware
- [ ] API key authentication
- [ ] Rate limiting
- [ ] Request/response logging
- [ ] Circuit breaker pattern
- [ ] Metrics aggregation from downstream services

## Configuration

**Port**: 8081

## Building

```bash
mvn clean compile
mvn spring-boot:run
```

## See Also

- [flux-warehouse](../flux-warehouse/README.md) — Core service
- [flux-generator](../flux-generator/README.md) — Data source
- [Main README](../README.md) — Architecture overview
