#!/bin/bash

set -e
set -o pipefail

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
LOG_DIR="${PROJECT_ROOT}/logs"
MAX_RETRIES="${MAX_RETRIES:-3}"
RETRY_DELAY="${RETRY_DELAY:-5}"
ATTEMPT=0

# Logging
LOG_FILE="${LOG_DIR}/debug-deploy-$(date +%s).log"
mkdir -p "$LOG_DIR"

# Functions
log() {
    local msg="$1"
    local level="${2:-INFO}"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[${timestamp}] [${level}] ${msg}" >> "$LOG_FILE"
}

info() {
    echo -e "${BLUE}ℹ ${1}${NC}"
    log "$1" "INFO"
}

success() {
    echo -e "${GREEN}✓ ${1}${NC}"
    log "$1" "SUCCESS"
}

warning() {
    echo -e "${YELLOW}⚠ ${1}${NC}"
    log "$1" "WARNING"
}

error() {
    echo -e "${RED}✗ ${1}${NC}"
    log "$1" "ERROR"
}

header() {
    echo
    echo -e "${CYAN}╔════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║ ${1}${NC}"
    echo -e "${CYAN}╚════════════════════════════════════════════╝${NC}"
    echo
}

# Check Docker access
check_docker_access() {
    if ! docker ps > /dev/null 2>&1; then
        error "Cannot access Docker daemon"
        error "Run: sudo usermod -aG docker \$USER && newgrp docker"
        return 1
    fi
    return 0
}

# Get service status
get_service_status() {
    local service=$1
    local status=$(docker-compose ps "$service" 2>/dev/null | tail -1 | awk '{print $NF}')
    echo "$status"
}

# Wait for service health with timeout
wait_for_health() {
    local url=$1
    local service=$2
    local timeout=${3:-30}
    local elapsed=0

    info "Checking health: $service ($url)"
    while [ $elapsed -lt $timeout ]; do
        if curl -sf "$url" > /dev/null 2>&1; then
            success "$service is healthy"
            return 0
        fi
        echo -ne "."
        sleep 1
        ((elapsed++))
    done

    echo
    error "$service failed health check (timeout after ${timeout}s)"
    return 1
}

# Extract error patterns from container logs
extract_error_patterns() {
    local service=$1
    local log_file="${LOG_DIR}/${service}-error.log"

    # Capture last 100 lines of service logs
    docker-compose logs --tail=100 "$service" > "$log_file" 2>&1 || true

    # Search for common error patterns
    local errors=()

    if grep -q "Connection refused" "$log_file"; then
        errors+=("Connection refused - service may not be running")
    fi

    if grep -q "Address already in use" "$log_file"; then
        errors+=("Port already in use - conflict with existing service")
    fi

    if grep -q "Kafka" "$log_file" && grep -q "Connection" "$log_file"; then
        errors+=("Kafka connection issue - broker may not be ready")
    fi

    if grep -q "OutOfMemory" "$log_file"; then
        errors+=("Out of memory error - container needs more resources")
    fi

    if grep -q "ClassNotFoundException\|NoSuchMethodError" "$log_file"; then
        errors+=("Java classpath issue - rebuild required")
    fi

    if [ ${#errors[@]} -gt 0 ]; then
        echo "${errors[@]}"
    else
        echo "Unknown error (check $log_file)"
    fi
}

# Suggest fixes based on error pattern
suggest_fix() {
    local pattern=$1
    case "$pattern" in
        *"Connection refused"*)
            echo "• Ensure all dependent services are running: docker-compose ps"
            echo "• Check service logs: docker-compose logs [service]"
            echo "• Rebuild images: docker-compose build"
            ;;
        *"Port already in use"*)
            echo "• Kill process using the port: lsof -i :[port]"
            echo "• Or stop conflicting containers: docker-compose down"
            ;;
        *"Kafka connection"*)
            echo "• Ensure Kafka is healthy: docker-compose ps kafka"
            echo "• Check Kafka logs: docker-compose logs kafka"
            echo "• Verify bootstrap server is reachable from application"
            ;;
        *"Out of memory"*)
            echo "• Increase Docker memory limits"
            echo "• Reduce resource-intensive operations"
            ;;
        *"Java classpath"*)
            echo "• Rebuild Docker images to update classpath"
            echo "• docker-compose build --no-cache"
            ;;
        *)
            echo "• Check detailed logs: tail -50 $LOG_FILE"
            echo "• Check container logs: docker-compose logs [service]"
            ;;
    esac
}

# Auto-fix attempt
attempt_auto_fix() {
    local service=$1
    local pattern=$2

    warning "Attempting auto-fix for: $service"

    case "$pattern" in
        *"Kafka connection"*)
            info "Waiting for Kafka to stabilize..."
            sleep 10
            docker-compose restart "$service" > /dev/null 2>&1 || true
            return 0
            ;;
        *"Connection refused"*)
            info "Restarting service: $service"
            docker-compose restart "$service" > /dev/null 2>&1 || true
            sleep 3
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

# Health check all services
health_check_all() {
    local urls=(
        "http://localhost:8880/api/health:flux-warehouse"
        "http://localhost:8881/api/health:flux-gateway"
        "http://localhost:8882/api/health:flux-generator"
    )

    local failed=()
    for entry in "${urls[@]}"; do
        local url="${entry%:*}"
        local service="${entry#*:}"
        if ! wait_for_health "$url" "$service" 10; then
            failed+=("$service")
        fi
    done

    if [ ${#failed[@]} -eq 0 ]; then
        return 0
    else
        echo "${failed[@]}"
        return 1
    fi
}

# Main execution
main() {
    header "Flux Debug Deploy Loop"
    info "Max retries: $MAX_RETRIES"
    info "Log file: $LOG_FILE"
    echo

    # Check prerequisites
    if ! check_docker_access; then
        error "Cannot proceed without Docker access"
        exit 1
    fi
    success "Docker access OK"
    echo

    # Run deploy script with retries
    while [ $ATTEMPT -lt $MAX_RETRIES ]; do
        ((ATTEMPT++))
        info "Deployment attempt ${ATTEMPT}/${MAX_RETRIES}"

        # Run deploy script
        if VERBOSE=1 "$SCRIPT_DIR/deploy.sh" > "${LOG_DIR}/deploy-${ATTEMPT}.log" 2>&1; then
            success "Deployment script succeeded"

            # Wait a bit for services to stabilize
            info "Waiting for services to stabilize (${RETRY_DELAY}s)..."
            sleep "$RETRY_DELAY"

            # Health check all services
            info "Performing health checks..."
            if failed=$(health_check_all); then
                success "All services are healthy!"
                log "Deployment successful on attempt ${ATTEMPT}" "SUCCESS"
                return 0
            else
                warning "Health check failed for: $failed"
                log "Health check failed for: $failed" "WARNING"
            fi
        else
            error "Deployment script failed on attempt ${ATTEMPT}"
            log "Deployment script failed on attempt ${ATTEMPT}" "ERROR"
        fi

        # Extract errors and suggest fixes
        if [ $ATTEMPT -lt $MAX_RETRIES ]; then
            echo
            info "Analyzing failure..."

            # Check which service failed
            local services=("kafka" "flux-warehouse" "flux-gateway" "flux-generator")
            for service in "${services[@]}"; do
                local status=$(get_service_status "$service")
                if [[ "$status" != "Up"* ]] && [[ "$status" != "(healthy)" ]]; then
                    warning "Service $service is not healthy: $status"

                    # Extract error patterns
                    local errors=$(extract_error_patterns "$service")
                    error "Detected issue: $errors"

                    # Suggest and attempt fixes
                    echo -e "${BLUE}Suggested fixes:${NC}"
                    suggest_fix "$errors"
                    echo

                    # Attempt auto-fix
                    if attempt_auto_fix "$service" "$errors"; then
                        info "Auto-fix applied, retrying..."
                    fi
                    break
                fi
            done

            if [ $ATTEMPT -lt $MAX_RETRIES ]; then
                info "Waiting ${RETRY_DELAY}s before retry..."
                sleep "$RETRY_DELAY"
            fi
        fi

        echo
    done

    # Final status
    echo
    header "Deployment Summary"
    error "Deployment failed after ${ATTEMPT} attempts"
    error "Please review logs:"
    echo "  Main logs: $LOG_DIR/"
    echo "  Full output: $LOG_FILE"
    echo
    error "Manual diagnostics:"
    echo "  Status: docker-compose ps"
    echo "  Logs: docker-compose logs -f [service]"
    echo "  Rebuild: docker-compose build --no-cache"
    exit 1
}

# Run main
main "$@"
