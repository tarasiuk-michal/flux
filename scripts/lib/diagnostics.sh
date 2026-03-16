#!/bin/bash

# Flux Diagnostics Utility Module
# Provides reusable functions for system and service diagnostics

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Check if Docker socket is accessible
check_docker_socket() {
    if [ ! -e /var/run/docker.sock ]; then
        echo -e "${RED}✗ Docker socket not found${NC}"
        return 1
    fi

    if ! docker ps > /dev/null 2>&1; then
        echo -e "${RED}✗ Docker daemon is not accessible${NC}"
        return 1
    fi

    echo -e "${GREEN}✓ Docker socket accessible${NC}"
    return 0
}

# Check Docker group membership
check_docker_group() {
    local user="${1:-$USER}"

    if getent group docker | grep -q "\b$user\b"; then
        echo -e "${GREEN}✓ User '$user' is in docker group${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠ User '$user' is NOT in docker group${NC}"
        echo "  To fix: sudo usermod -aG docker $user"
        echo "  Then: newgrp docker (or restart shell)"
        return 1
    fi
}

# Get detailed service status
get_service_status() {
    local service=$1
    local status=$(docker-compose ps "$service" 2>/dev/null | tail -1)

    if [ -z "$status" ]; then
        echo "NOT_FOUND"
        return 1
    fi

    # Parse status from docker-compose ps output
    local state=$(echo "$status" | awk '{print $NF}')
    echo "$state"
    return 0
}

# Extract container errors from logs
extract_container_error() {
    local service=$1
    local lines="${2:-50}"

    local logs=$(docker-compose logs --tail="$lines" "$service" 2>/dev/null)

    # Check for known error patterns
    if echo "$logs" | grep -q "error\|Error\|ERROR"; then
        echo "$logs" | grep -i "error" | tail -5
        return 0
    fi

    if echo "$logs" | grep -q "exception\|Exception\|EXCEPTION"; then
        echo "$logs" | grep -i "exception" | tail -5
        return 0
    fi

    if echo "$logs" | grep -q "failed\|Failed\|FAILED"; then
        echo "$logs" | grep -i "failed" | tail -5
        return 0
    fi

    return 1
}

# Get all service statuses in a table format
get_all_services_status() {
    echo -e "${BLUE}Service Status:${NC}"
    echo "────────────────────────────────────────"

    local services=("zookeeper" "kafka" "flux-warehouse" "flux-gateway" "flux-generator")
    for service in "${services[@]}"; do
        local status=$(get_service_status "$service")
        local icon="${GREEN}✓${NC}"

        case "$status" in
            Up*)
                icon="${GREEN}✓${NC}"
                ;;
            *)
                icon="${RED}✗${NC}"
                ;;
        esac

        printf "%s %-20s %s\n" "$icon" "$service" "$status"
    done

    echo "────────────────────────────────────────"
}

# Check port availability
check_port_available() {
    local port=$1
    local service_name="${2:-service}"

    if netstat -tuln 2>/dev/null | grep -q ":$port "; then
        echo -e "${RED}✗ Port $port is already in use${NC}"
        return 1
    else
        echo -e "${GREEN}✓ Port $port is available${NC}"
        return 0
    fi
}

# Get service health check status
check_service_health() {
    local url=$1
    local service=$2
    local timeout=${3:-5}

    if curl -sf --connect-timeout "$timeout" "$url" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ $service is healthy${NC}"
        return 0
    else
        echo -e "${RED}✗ $service health check failed${NC}"
        return 1
    fi
}

# Display detailed diagnostics for a service
diagnose_service() {
    local service=$1

    echo
    echo -e "${BLUE}═══ Diagnostics for $service ═══${NC}"
    echo

    # Status
    local status=$(get_service_status "$service")
    if [ $? -eq 0 ]; then
        echo -e "${BLUE}Status:${NC} $status"
    else
        echo -e "${RED}Service not found${NC}"
        return 1
    fi

    # Container ID
    local container_id=$(docker-compose ps -q "$service" 2>/dev/null)
    if [ -n "$container_id" ]; then
        echo -e "${BLUE}Container ID:${NC} $container_id"

        # Memory usage
        local mem=$(docker stats --no-stream "$container_id" 2>/dev/null | tail -1 | awk '{print $4}')
        echo -e "${BLUE}Memory Usage:${NC} $mem"

        # CPU usage
        local cpu=$(docker stats --no-stream "$container_id" 2>/dev/null | tail -1 | awk '{print $3}')
        echo -e "${BLUE}CPU Usage:${NC} $cpu"
    fi

    # Recent errors
    echo
    echo -e "${BLUE}Recent Errors (if any):${NC}"
    if errors=$(extract_container_error "$service" 20); then
        echo "$errors"
    else
        echo -e "${GREEN}No errors detected in recent logs${NC}"
    fi

    echo
}

# Full system diagnostics
run_full_diagnostics() {
    echo -e "${BLUE}╔════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║  Flux System Diagnostics                   ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════╝${NC}"
    echo

    # Docker checks
    echo -e "${BLUE}━━ Docker Configuration ━━${NC}"
    check_docker_socket
    check_docker_group
    echo

    # Service status overview
    echo -e "${BLUE}━━ Service Overview ━━${NC}"
    get_all_services_status
    echo

    # Port checks
    echo -e "${BLUE}━━ Port Availability ━━${NC}"
    check_port_available 8880 "flux-warehouse"
    check_port_available 8881 "flux-gateway"
    check_port_available 8882 "flux-generator"
    check_port_available 9092 "kafka"
    check_port_available 2181 "zookeeper"
    echo

    # Health checks
    echo -e "${BLUE}━━ Service Health Checks ━━${NC}"
    check_service_health "http://localhost:8880/api/health" "flux-warehouse"
    check_service_health "http://localhost:8881/api/health" "flux-gateway"
    check_service_health "http://localhost:8882/api/health" "flux-generator"
    echo

    # Individual service diagnostics
    echo -e "${BLUE}━━ Detailed Service Diagnostics ━━${NC}"
    diagnose_service "kafka"
    diagnose_service "flux-warehouse"
    diagnose_service "flux-gateway"
    diagnose_service "flux-generator"
}

# Suggest fix based on error
suggest_fix() {
    local error_pattern=$1

    case "$error_pattern" in
        *"connection"*|*"Connection"*|*"CONNECTION"*)
            echo "Connection issue detected. Possible solutions:"
            echo "1. Ensure all services are running: docker-compose ps"
            echo "2. Check service dependencies are healthy"
            echo "3. Verify network connectivity between containers"
            ;;
        *"port"*|*"Port"*|*"PORT"*)
            echo "Port-related issue detected. Possible solutions:"
            echo "1. Check if port is in use: netstat -tuln | grep :[port]"
            echo "2. Stop conflicting service: docker-compose down"
            echo "3. Change port in docker-compose.yml"
            ;;
        *"memory"*|*"Memory"*|*"MEMORY"*)
            echo "Memory issue detected. Possible solutions:"
            echo "1. Increase Docker memory limit"
            echo "2. Check container memory usage: docker stats"
            echo "3. Reduce number of running containers"
            ;;
        *"kafka"*|*"Kafka"*|*"KAFKA"*)
            echo "Kafka-related issue detected. Possible solutions:"
            echo "1. Verify Kafka is running: docker-compose ps kafka"
            echo "2. Check Kafka logs: docker-compose logs kafka"
            echo "3. Ensure bootstrap server is correct"
            echo "4. Wait for Kafka to fully initialize (30-60s)"
            ;;
        *)
            echo "Unknown error. General troubleshooting steps:"
            echo "1. Check service logs: docker-compose logs [service]"
            echo "2. Verify all services are running: docker-compose ps"
            echo "3. Rebuild images: docker-compose build --no-cache"
            echo "4. Start fresh: docker-compose down && docker-compose up -d"
            ;;
    esac
}

# Auto-recover service
apply_auto_fix() {
    local service=$1
    local error_pattern=$2

    case "$service:$error_pattern" in
        *:*"Kafka"*)
            echo "Applying auto-fix: Waiting for Kafka and restarting service..."
            sleep 10
            docker-compose restart "$service" > /dev/null 2>&1
            return $?
            ;;
        *:*"connection"*)
            echo "Applying auto-fix: Restarting service..."
            docker-compose restart "$service" > /dev/null 2>&1
            return $?
            ;;
        *)
            return 1
            ;;
    esac
}

# Export all functions
export -f check_docker_socket
export -f check_docker_group
export -f get_service_status
export -f extract_container_error
export -f get_all_services_status
export -f check_port_available
export -f check_service_health
export -f diagnose_service
export -f run_full_diagnostics
export -f suggest_fix
export -f apply_auto_fix
