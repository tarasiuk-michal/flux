#!/bin/bash

set -eo pipefail

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo -e "${BLUE}╔════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  Flux Test Suite                           ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════╝${NC}"

# Function to print result
print_result() {
    local module=$1
    local status=$2

    if [ $status -eq 0 ]; then
        echo -e "${GREEN}✓ $module tests passed${NC}"
    else
        echo -e "${RED}✗ $module tests failed${NC}"
        return 1
    fi
}

# Track overall test result
overall_result=0

# Run tests for each module
echo
echo -e "${BLUE}Running tests...${NC}"
echo

# Test flux-warehouse
echo -e "${BLUE}Testing flux-warehouse...${NC}"
cd "$PROJECT_ROOT/flux-warehouse"
if [ -f "pom.xml" ]; then
    if mvn clean verify 2>&1 | tee /tmp/warehouse-test.log; then
        print_result "flux-warehouse" 0
    else
        print_result "flux-warehouse" 1
        overall_result=1
    fi
else
    echo -e "${YELLOW}⚠ flux-warehouse pom.xml not found, skipping${NC}"
fi

echo

# Test flux-gateway
echo -e "${BLUE}Testing flux-gateway...${NC}"
cd "$PROJECT_ROOT/flux-gateway"
if [ -f "pom.xml" ]; then
    if mvn clean verify 2>&1 | tee /tmp/gateway-test.log; then
        print_result "flux-gateway" 0
    else
        print_result "flux-gateway" 1
        overall_result=1
    fi
else
    echo -e "${YELLOW}⚠ flux-gateway pom.xml not found or source missing, skipping${NC}"
fi

echo

# Test flux-generator
echo -e "${BLUE}Testing flux-generator...${NC}"
cd "$PROJECT_ROOT/flux-generator"
if [ -f "pom.xml" ]; then
    if mvn clean verify 2>&1 | tee /tmp/generator-test.log; then
        print_result "flux-generator" 0
    else
        print_result "flux-generator" 1
        overall_result=1
    fi
else
    echo -e "${YELLOW}⚠ flux-generator pom.xml not found or source missing, skipping${NC}"
fi

echo

# Print summary
echo -e "${BLUE}═══════════════════════════════════════════${NC}"
if [ $overall_result -eq 0 ]; then
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}Some tests failed. Check logs above.${NC}"
    echo
    echo -e "${BLUE}Test logs:${NC}"
    echo "  Warehouse: tail -f /tmp/warehouse-test.log"
    echo "  Gateway:   tail -f /tmp/gateway-test.log"
    echo "  Generator: tail -f /tmp/generator-test.log"
    exit 1
fi
