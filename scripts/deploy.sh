#!/bin/bash

set -e
set -o pipefail

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Logging configuration
LOG_DIR="${PROJECT_ROOT}/logs"
LOG_FILE="${LOG_DIR}/deployment-$(date +%s).log"
VERBOSE="${VERBOSE:-0}"
DEBUG="${DEBUG:-0}"

# Create logs directory if it doesn't exist
mkdir -p "$LOG_DIR"

# Function to log with timestamp
log_message() {
    local msg="$1"
    local level="${2:-INFO}"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[${timestamp}] [${level}] ${msg}" >> "$LOG_FILE"
    if [ "$VERBOSE" -eq 1 ] || [ "$level" = "ERROR" ]; then
        echo -e "[${timestamp}] [${level}] ${msg}" >&2
    fi
}

# Initialize log file
{
    echo "╔════════════════════════════════════════════╗"
    echo "║  Flux Docker Deployment - $(date '+%Y-%m-%d %H:%M:%S')  ║"
    echo "╚════════════════════════════════════════════╝"
    echo ""
} > "$LOG_FILE"

echo -e "${BLUE}╔════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  Flux Docker Deployment Script             ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════╝${NC}"
echo "Logging to: $LOG_FILE"
echo
{
    echo "Script started at: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "Project root: $PROJECT_ROOT"
    echo ""
} >> "$LOG_FILE"

# Function to print error and exit
error() {
    echo -e "${RED}✗ Error: $1${NC}"
    log_message "ERROR: $1" "ERROR"
    exit 1
}

# Function to print success
success() {
    echo -e "${GREEN}✓ $1${NC}"
    log_message "$1" "SUCCESS"
}

# Function to print info
info() {
    echo -e "${BLUE}ℹ $1${NC}"
    log_message "$1" "INFO"
}

# Function to print warning
warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
    log_message "$1" "WARNING"
}

# Function to check Docker socket access
check_docker_access() {
    info "Checking Docker socket access..."

    if ! docker ps > /dev/null 2>&1; then
        echo -e "${RED}✗ Cannot access Docker daemon without sudo${NC}"
        echo -e "${YELLOW}To fix this, run one of the following:${NC}"
        echo "  Option 1: Add current user to docker group (requires logout/restart)"
        echo "    sudo usermod -aG docker \$USER"
        echo "    newgrp docker"
        echo ""
        echo "  Option 2: Or simply use sudo to run this script"
        echo "    sudo ./scripts/deploy.sh"
        echo ""
        return 1
    fi
    success "Docker socket access OK"
    return 0
}

# Function to wait for service health
wait_for_service() {
    local url=$1
    local service_name=$2
    local max_attempts=30
    local attempt=1

    echo -ne "${BLUE}Waiting for $service_name...${NC}"

    while [ $attempt -le $max_attempts ]; do
        if curl -sf "$url" > /dev/null 2>&1; then
            echo -e "\r${GREEN}✓ $service_name is healthy${NC}"
            log_message "$service_name is healthy" "SUCCESS"
            return 0
        fi
        echo -ne "."
        sleep 1
        ((attempt++))
    done

    echo -e "\r${RED}✗ $service_name failed to start (timeout)${NC}"
    log_message "$service_name failed to start (timeout)" "ERROR"
    return 1
}

# 1. Pre-flight checks
echo
info "Running pre-flight checks..."

# Check Docker
if ! command -v docker &> /dev/null; then
    error "Docker is not installed. Please install Docker Desktop or Docker Engine."
fi
success "Docker is installed"

# Check Docker Socket Access (Phase 1 - Docker Permissions)
if ! check_docker_access; then
    error "Cannot access Docker daemon. Please fix Docker permissions and try again."
fi

# Check Docker Compose
if ! command -v docker-compose &> /dev/null; then
    error "Docker Compose is not installed. Please install Docker Compose."
fi
success "Docker Compose is installed"

# Check Java 21+
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | grep -oP 'version "\K[0-9]+' | head -1)
    if [ "$JAVA_VERSION" -lt 21 ]; then
        error "Java 21 or higher is required (found version $JAVA_VERSION)"
    fi
    success "Java 21+ is installed (version $JAVA_VERSION)"
else
    warning "Java not found in PATH (will use Docker's JDK). Make sure Java 21+ is installed for local builds."
fi

# Check Maven
if ! command -v mvn &> /dev/null; then
    warning "Maven not found in PATH. Will use mvnw scripts in each module."
fi

# Check .env file
if [ ! -f "$PROJECT_ROOT/.env" ]; then
    warning "No .env file found. Creating from .env.example..."
    if [ -f "$PROJECT_ROOT/.env.example" ]; then
        cp "$PROJECT_ROOT/.env.example" "$PROJECT_ROOT/.env"
        success ".env file created from template"
    else
        error ".env.example not found"
    fi
fi

# 2. Load environment
echo
info "Loading environment configuration..."
cd "$PROJECT_ROOT"
set -a
source .env
set +a
info "Network mode: $NETWORK_MODE"
info "Machine FQDN: $MACHINE_FQDN"
info "Kafka bootstrap: $KAFKA_BOOTSTRAP"
success "Environment loaded"

# 3. Build modules (using Docker layer caching for speed)
echo
info "Building Docker images..."
info "Tip: First build is slower, but subsequent builds use cached layers"

# Helper function to build image with logging
build_image() {
    local module=$1
    local tag=$2
    local build_log="${LOG_DIR}/${tag}-build.log"

    echo -ne "${BLUE}Building ${tag}...${NC}"
    cd "$PROJECT_ROOT/$module"

    if docker build -t "${tag}:latest" . > "$build_log" 2>&1; then
        echo -e "\r${GREEN}✓ ${tag} built${NC}"
        log_message "${tag} built successfully" "SUCCESS"
        return 0
    else
        echo -e "\r${RED}✗ ${tag} build failed${NC}"
        log_message "${tag} build failed. See $build_log for details" "ERROR"
        return 1
    fi
}

if ! build_image "flux-warehouse" "flux-warehouse"; then
    error "Failed to build flux-warehouse (see logs/flux-warehouse-build.log)"
fi

if ! build_image "flux-gateway" "flux-gateway"; then
    warning "Gateway source code may be missing. Continuing..."
fi

if ! build_image "flux-generator" "flux-generator"; then
    warning "Generator source code may be missing. Continuing..."
fi

# 4. Start Docker Compose
echo
info "Starting Docker Compose services..."
cd "$PROJECT_ROOT"

COMPOSE_LOG="${LOG_DIR}/docker-compose.log"

if docker-compose down > /dev/null 2>&1; then
    info "Cleaned up previous containers"
fi

if docker-compose up -d > "$COMPOSE_LOG" 2>&1; then
    success "Docker Compose services started"
    log_message "Docker Compose services started successfully" "SUCCESS"
else
    error "Failed to start Docker Compose services (see logs/docker-compose.log)"
fi

# 5. Wait for services to be healthy (in parallel)
echo
info "Waiting for services to be ready..."

# Determine service URLs based on network mode
if [ "$NETWORK_MODE" = "tailscale" ]; then
    WAREHOUSE_URL="http://$MACHINE_FQDN:8880/api/health"
    GATEWAY_URL="http://$MACHINE_FQDN:8881/api/health"
    GENERATOR_URL="http://$MACHINE_FQDN:8882/api/health"
    echo -e "${YELLOW}Note: Using Tailscale mode - services may not be reachable from this machine${NC}"
    echo -e "${YELLOW}Access from Tailscale-connected machines using FQDN: $MACHINE_FQDN${NC}"
else
    WAREHOUSE_URL="http://localhost:8880/api/health"
    GATEWAY_URL="http://localhost:8881/api/health"
    GENERATOR_URL="http://localhost:8882/api/health"
fi

# Check services in parallel for faster startup
(
    if wait_for_service "$WAREHOUSE_URL" "flux-warehouse"; then
        success "flux-warehouse is ready"
    else
        warning "flux-warehouse health check failed (but may still be running)"
    fi
) &
WAREHOUSE_PID=$!

(
    if wait_for_service "$GATEWAY_URL" "flux-gateway"; then
        success "flux-gateway is ready"
    else
        warning "flux-gateway health check failed (source code may be missing)"
    fi
) &
GATEWAY_PID=$!

(
    if wait_for_service "$GENERATOR_URL" "flux-generator"; then
        success "flux-generator is ready"
    else
        warning "flux-generator health check failed (source code may be missing)"
    fi
) &
GENERATOR_PID=$!

# Wait for all background checks
wait $WAREHOUSE_PID $GATEWAY_PID $GENERATOR_PID

# 6. Display connection info
echo
echo -e "${GREEN}╔════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  Deployment Complete!                      ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════╝${NC}"

echo
echo -e "${BLUE}Service URLs (Network Mode: $NETWORK_MODE):${NC}"
if [ "$NETWORK_MODE" = "tailscale" ]; then
    echo "  Warehouse: http://$MACHINE_FQDN:8880/api"
    echo "  Gateway:   http://$MACHINE_FQDN:8881/api"
    echo "  Generator: http://$MACHINE_FQDN:8882/api"
else
    echo "  Warehouse: http://localhost:8880/api"
    echo "  Gateway:   http://localhost:8881/api"
    echo "  Generator: http://localhost:8882/api"
fi

echo
echo -e "${BLUE}Kafka Broker:${NC}"
echo "  Bootstrap: kafka:9092 (internal)"
echo "  Topic:     data-stream"

echo
echo -e "${BLUE}Logging:${NC}"
echo "  Main deployment log: $LOG_FILE"
echo "  Docker compose log:  ${LOG_DIR}/docker-compose.log"
echo "  Build logs:          ${LOG_DIR}/*-build.log"
echo "  Run with --verbose for detailed output"

echo
echo -e "${BLUE}Common Commands:${NC}"
echo "  View logs:     docker-compose logs -f"
echo "  Stop services: docker-compose down"
echo "  Restart:       docker-compose restart"
echo "  Check status:  docker-compose ps"

echo
echo -e "${BLUE}Useful curl commands:${NC}"
if [ "$NETWORK_MODE" = "tailscale" ]; then
    echo "  curl http://$MACHINE_FQDN:8880/api/health"
    echo "  curl 'http://$MACHINE_FQDN:8881/api/query?market=warsaw&symbol=PKO'"
else
    echo "  curl http://localhost:8880/api/health"
    echo "  curl 'http://localhost:8881/api/query?market=warsaw&symbol=PKO'"
fi

echo
echo -e "${BLUE}To switch network modes:${NC}"
echo "  1. Edit .env file (change NETWORK_MODE=local or NETWORK_MODE=tailscale)"
echo "  2. Run: docker-compose down && docker-compose up -d"

echo
echo -e "${BLUE}Troubleshooting:${NC}"
echo "  If services fail, run: ./scripts/debug-deploy.sh"
echo "  For detailed diagnostics, run with: VERBOSE=1 ./scripts/deploy.sh"

echo
success "All set! Services are starting up..."
log_message "Deployment script completed successfully" "SUCCESS"
echo "Full log available at: $LOG_FILE"
