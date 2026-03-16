#!/bin/bash

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo -e "${BLUE}╔════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  Flux Docker Teardown                       ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════╝${NC}"

cd "$PROJECT_ROOT"

# Ask for confirmation
echo
echo -e "${YELLOW}This will:${NC}"
echo "  • Stop all running Docker Compose services"
echo "  • Remove containers and networks"
echo "  • Remove volumes (data loss!)"
echo

read -p "Are you sure you want to continue? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo -e "${BLUE}Teardown cancelled.${NC}"
    exit 0
fi

echo
echo -e "${BLUE}Tearing down Docker Compose services...${NC}"

if docker-compose down -v; then
    echo -e "${GREEN}✓ Services stopped and volumes removed${NC}"
else
    echo -e "${RED}✗ Error during teardown${NC}"
    exit 1
fi

# Clean up build artifacts
echo
echo -e "${BLUE}Cleaning up build artifacts...${NC}"

for module in flux-warehouse flux-gateway flux-generator; do
    if [ -d "$module/target" ]; then
        rm -rf "$module/target"
        echo -e "${GREEN}✓ Cleaned $module/target${NC}"
    fi
done

# Remove database files
if [ -d "flux-warehouse/data" ]; then
    rm -rf "flux-warehouse/data"
    echo -e "${GREEN}✓ Cleaned flux-warehouse/data${NC}"
fi

echo
echo -e "${GREEN}╔════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  Teardown Complete!                        ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════╝${NC}"
