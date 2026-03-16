# Flux Deployment - Quick Start

## 🚀 Get Started in 30 Seconds

### 1. First Time Only: Setup Docker Access
```bash
# Add yourself to docker group
sudo usermod -aG docker $USER
newgrp docker

# Verify it works
docker ps
```

### 2. Deploy the Services
```bash
# Standard deployment with logging
./scripts/deploy.sh

# Or if something fails, auto-retry:
./scripts/debug-deploy.sh
```

### 3. Check Services Are Running
```bash
# View status
docker-compose ps

# Health check
curl http://localhost:8880/api/health  # Warehouse
curl http://localhost:8881/api/health  # Gateway
curl http://localhost:8882/api/health  # Generator
```

## 📋 Common Commands

```bash
# View logs
tail -f logs/deployment-*.log
docker-compose logs -f [service]

# Stop services
docker-compose down

# Restart a service
docker-compose restart flux-warehouse

# Full diagnostics
source scripts/lib/diagnostics.sh
run_full_diagnostics

# Rebuild and redeploy
docker-compose build --no-cache
./scripts/deploy.sh
```

## 🆘 Troubleshooting

### "Cannot access Docker daemon"
```bash
# Fix: Add to docker group
sudo usermod -aG docker $USER && newgrp docker
./scripts/deploy.sh
```

### "Services won't start"
```bash
# Let the automated debug script fix it
./scripts/debug-deploy.sh
```

### "Need to see what went wrong?"
```bash
# All logs are in logs/ directory
cat logs/deployment-*.log
docker-compose logs flux-warehouse --tail=100
```

## 📁 Key Files

| File | Purpose |
|------|---------|
| `scripts/deploy.sh` | Deploy with logging |
| `scripts/debug-deploy.sh` | Auto-retry on failure |
| `scripts/lib/diagnostics.sh` | System diagnostics |
| `DEPLOYMENT_GUIDE.md` | Full documentation |
| `.env` | Configuration (network mode, API key, etc.) |
| `logs/` | All deployment logs |

## 🔧 Configuration

Edit `.env` to set:
- `NETWORK_MODE` - `local` or `tailscale`
- `MACHINE_FQDN` - Your machine hostname
- `FLUX_API_KEY` - Your API key

## 📊 Service Ports

| Service | Port | Health URL |
|---------|------|-----------|
| Warehouse | 8880 | http://localhost:8880/api/health |
| Gateway | 8881 | http://localhost:8881/api/health |
| Generator | 8882 | http://localhost:8882/api/health |
| Kafka | 9092 | Internal (no health endpoint) |

## ⏱️ Expected Times

- **First deployment:** 3-5 minutes (building images)
- **Subsequent deployments:** 30-60 seconds (cached layers)
- **Service startup:** 30-40 seconds (health checks pass)

## 🎯 Success Indicators

✅ All checks passed if you see:
```
✓ Docker is installed
✓ Docker Compose is installed
✓ Docker socket access OK
✓ Environment loaded
✓ flux-warehouse is healthy
✓ flux-gateway is healthy
✓ flux-generator is healthy
```

---

**For detailed information, see:** `DEPLOYMENT_GUIDE.md`

**For implementation details, see:** `IMPLEMENTATION_STATUS.md`
