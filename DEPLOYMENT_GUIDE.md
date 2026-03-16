# Flux Deployment Guide

This guide explains the automated deployment system for Flux microservices with comprehensive logging and diagnostics.

## Quick Start

### Prerequisites
- Docker installed and running
- Docker Compose installed
- User permissions: Docker group access (see Docker Setup below)
- Curl installed (for health checks)

### Docker Setup (One-Time)

To run Docker commands without `sudo`, add your user to the docker group:

```bash
# Add user to docker group
sudo usermod -aG docker $USER

# Activate the new group (choose one)
newgrp docker          # Creates new shell with docker group
# OR logout and login again
```

Verify setup:
```bash
docker ps  # Should work without sudo
```

## Deployment Scripts

### 1. Standard Deployment (`scripts/deploy.sh`)

Standard deployment with logging and pre-flight checks:

```bash
./scripts/deploy.sh
```

**What it does:**
1. Checks Docker socket access (Phase 1)
2. Verifies all dependencies (Java, Docker, Docker Compose, Maven)
3. Loads environment configuration from `.env`
4. Builds Docker images with layer caching
5. Starts Docker Compose services
6. Waits for all services to be healthy
7. Displays connection URLs and useful commands

**Output:**
- Main log: `logs/deployment-[timestamp].log`
- Build logs: `logs/*-build.log`
- Docker Compose log: `logs/docker-compose.log`

**Options:**
```bash
# Verbose output with detailed logging
VERBOSE=1 ./scripts/deploy.sh

# With custom retry delay (seconds)
RETRY_DELAY=10 ./scripts/deploy.sh
```

**Log File Location:**
All deployment logs are saved to the `logs/` directory with timestamps. The script outputs the log file path at the end.

### 2. Automated Debug Loop (`scripts/debug-deploy.sh`)

Use this when standard deployment fails or services don't become healthy:

```bash
./scripts/debug-deploy.sh
```

**What it does:**
1. Checks Docker access
2. Runs deploy script and captures output
3. Waits for services to stabilize
4. Performs health checks on all services
5. If any service fails:
   - Extracts error patterns from logs
   - Suggests fixes
   - Attempts auto-fixes (restart, wait for dependencies)
   - Retries up to 3 times
6. Provides detailed diagnostics on final failure

**Configuration:**
```bash
# Max retry attempts (default: 3)
MAX_RETRIES=5 ./scripts/debug-deploy.sh

# Delay between retries in seconds (default: 5)
RETRY_DELAY=10 ./scripts/debug-deploy.sh
```

**Output:**
- Debug log: `logs/debug-deploy-[timestamp].log`
- Individual deployment logs: `logs/deploy-[attempt].log`
- Error logs: `logs/[service]-error.log`

### 3. System Diagnostics (`scripts/lib/diagnostics.sh`)

Source and use diagnostic functions:

```bash
source ./scripts/lib/diagnostics.sh

# Run full system diagnostics
run_full_diagnostics

# Check specific aspects
check_docker_socket
check_docker_group
get_all_services_status
diagnose_service flux-warehouse
```

## Service Status

Check service status at any time:

```bash
# View all containers
docker-compose ps

# View service logs
docker-compose logs -f [service]

# Common services:
# - zookeeper
# - kafka
# - flux-warehouse (port 8880)
# - flux-gateway (port 8881)
# - flux-generator (port 8882)
```

## Health Checks

All services have health endpoints:

```bash
# Check individual services
curl http://localhost:8880/api/health  # Warehouse
curl http://localhost:8881/api/health  # Gateway
curl http://localhost:8882/api/health  # Generator

# With Tailscale mode (see .env)
curl http://$MACHINE_FQDN:8880/api/health
```

## Logging Configuration

### Log File Structure
```
logs/
├── deployment-[timestamp].log      # Main deployment log
├── docker-compose.log               # Docker Compose output
├── flux-warehouse-build.log        # Warehouse build output
├── flux-gateway-build.log          # Gateway build output
├── flux-generator-build.log        # Generator build output
└── debug-deploy-[timestamp].log    # Debug loop logs
```

### Log Format
- Timestamps for all events
- Severity levels: INFO, SUCCESS, WARNING, ERROR
- Captured stderr and stdout
- Container error patterns extracted

### Viewing Logs

```bash
# View main deployment log
tail -f logs/deployment-*.log

# View all logs
less logs/*.log

# View only errors
grep ERROR logs/*.log

# View container logs from Docker
docker-compose logs flux-warehouse --tail=100
```

## Common Issues & Solutions

### Issue: "Cannot access Docker daemon without sudo"

**Solution:**
```bash
sudo usermod -aG docker $USER
newgrp docker
./scripts/deploy.sh
```

### Issue: Services not starting

**Solution:**
```bash
# Run automated diagnostics
./scripts/debug-deploy.sh

# Or manually check:
docker-compose ps
docker-compose logs [service]
```

### Issue: Kafka connection errors

**Solution:** Kafka can take 30-60 seconds to fully initialize. The debug script handles this automatically:
```bash
./scripts/debug-deploy.sh  # Will auto-retry and wait
```

### Issue: Port already in use

**Solution:**
```bash
# Find process using port
lsof -i :8880  # Or other port

# Stop all services
docker-compose down

# Or stop specific container
docker-compose stop [service]
```

### Issue: Build failures

**Solution:**
Check the build log for details:
```bash
cat logs/flux-warehouse-build.log
# Then rebuild
docker-compose build --no-cache flux-warehouse
```

## Production Notes

### Logging Configuration
- All services use JSON file logging driver
- Max file size: 10MB
- Max files: 3 (rotation)
- Supports labels for container identification

### Health Checks
- Interval: 10 seconds
- Timeout: 5 seconds
- Retries: 5 attempts
- Start period: 30-40 seconds (allows startup time)

### Restart Policy
- Zookeeper & Kafka: Restart on failure (up to 3 times)
- Applications: Restart on failure (up to 2 times)

## Environment Configuration

Edit `.env` to configure:

```bash
# Network mode (local or tailscale)
NETWORK_MODE=local

# Machine FQDN (for Tailscale)
MACHINE_FQDN=your-machine.ts.net

# Kafka bootstrap (internal use)
KAFKA_BOOTSTRAP=kafka:9092

# API key
FLUX_API_KEY=your-api-key
```

## Useful Commands

```bash
# View all services and their status
docker-compose ps

# Follow logs for all services
docker-compose logs -f

# Follow logs for specific service
docker-compose logs -f flux-warehouse

# Stop all services
docker-compose down

# Stop specific service
docker-compose stop flux-warehouse

# Restart service
docker-compose restart flux-warehouse

# Rebuild service without cache
docker-compose build --no-cache flux-warehouse

# Remove all volumes (data loss!)
docker-compose down -v

# Execute command in container
docker-compose exec flux-warehouse bash

# Get resource usage
docker stats

# Clean up unused resources
docker system prune
```

## Performance Tips

1. **First deployment is slower** - Docker layers are built fresh
2. **Subsequent deployments are faster** - Cached layers are reused
3. **Use `--no-cache` only when needed** - Forces full rebuild
4. **Monitor resource usage** - `docker stats` shows memory/CPU

## Troubleshooting Workflow

1. **Run deploy script:**
   ```bash
   ./scripts/deploy.sh
   ```

2. **If it fails, run debug script:**
   ```bash
   ./scripts/debug-deploy.sh
   ```

3. **Check logs if needed:**
   ```bash
   cat logs/deployment-*.log
   docker-compose logs [service]
   ```

4. **Run diagnostics:**
   ```bash
   source scripts/lib/diagnostics.sh
   run_full_diagnostics
   ```

5. **Manual recovery:**
   ```bash
   docker-compose down
   docker-compose up -d
   ```

## Support

- **Logs location:** `./logs/`
- **Check deployment log:** `logs/deployment-*.log`
- **Check error details:** `docker-compose logs [service]`
- **System diagnostics:** `source scripts/lib/diagnostics.sh && run_full_diagnostics`

---

**Last Updated:** 2026-03-16
**Version:** 2.0 (Automated Deployment with Logging)
