#!/bin/bash

# Automation Test Runner Script
# This script helps run automation tests and manage screenshots

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
BASE_URL="${TEST_BASE_URL:-http://localhost:8080}"
ADMIN_USER="${TEST_ADMIN_USER:-admin}"
ADMIN_PASS="${TEST_ADMIN_PASS:-admin}"
BROWSER="${TEST_BROWSER:-chrome}"
HEADLESS="${TEST_HEADLESS:-true}"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Automation Test Runner${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Function to check if application is running
check_application() {
    echo -e "${YELLOW}Checking if application is running...${NC}"
    if curl -s -f -u "${ADMIN_USER}:${ADMIN_PASS}" "${BASE_URL}/api/cache" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Application is running${NC}"
        return 0
    else
        echo -e "${RED}✗ Application is not running at ${BASE_URL}${NC}"
        echo -e "${YELLOW}Please start the application first:${NC}"
        echo "  mvn spring-boot:run"
        return 1
    fi
}

# Function to run tests
run_tests() {
    local test_type=$1
    local test_class=$2
    
    echo -e "${YELLOW}Running ${test_type} tests...${NC}"
    
    if [ -n "$test_class" ]; then
        mvn test -Dtest="${test_class}" \
            -Dtest.base.url="${BASE_URL}" \
            -Dtest.admin.username="${ADMIN_USER}" \
            -Dtest.admin.password="${ADMIN_PASS}" \
            -Dtest.browser="${BROWSER}" \
            -Dtest.headless="${HEADLESS}"
    else
        mvn test \
            -Dtest.base.url="${BASE_URL}" \
            -Dtest.admin.username="${ADMIN_USER}" \
            -Dtest.admin.password="${ADMIN_PASS}" \
            -Dtest.browser="${BROWSER}" \
            -Dtest.headless="${HEADLESS}"
    fi
}

# Function to show screenshots
show_screenshots() {
    local screenshot_dir="test-output/screenshots"
    
    if [ ! -d "$screenshot_dir" ]; then
        echo -e "${RED}✗ Screenshot directory not found: ${screenshot_dir}${NC}"
        echo "Run tests first to generate screenshots"
        return 1
    fi
    
    local count=$(find "$screenshot_dir" -name "*.png" 2>/dev/null | wc -l | tr -d ' ')
    
    if [ "$count" -eq 0 ]; then
        echo -e "${YELLOW}No screenshots found${NC}"
        return 1
    fi
    
    echo -e "${GREEN}Found ${count} screenshot(s)${NC}"
    echo ""
    echo "Screenshots location: $(pwd)/${screenshot_dir}"
    echo ""
    
    # List screenshots
    echo "Screenshots:"
    find "$screenshot_dir" -name "*.png" -type f | sort | while read -r file; do
        echo "  - $(basename "$file")"
    done
    
    # Open directory (platform-specific)
    if [[ "$OSTYPE" == "darwin"* ]]; then
        open "$screenshot_dir"
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        xdg-open "$screenshot_dir" 2>/dev/null || echo "Open manually: $screenshot_dir"
    else
        echo "Open manually: $screenshot_dir"
    fi
}

# Function to clean test output
clean_output() {
    echo -e "${YELLOW}Cleaning test output...${NC}"
    rm -rf test-output/screenshots
    rm -rf target/surefire-reports
    echo -e "${GREEN}✓ Test output cleaned${NC}"
}

# Main menu
case "${1:-all}" in
    "api")
        if check_application; then
            run_tests "API" "com.cache.automation.api.*"
        fi
        ;;
    "ui")
        if check_application; then
            run_tests "UI" "com.cache.automation.ui.*"
        fi
        ;;
    "integration")
        if check_application; then
            run_tests "Integration" "com.cache.automation.integration.*"
        fi
        ;;
    "cache-api")
        if check_application; then
            run_tests "Cache API" "CacheControllerAPITest"
        fi
        ;;
    "cluster-api")
        if check_application; then
            run_tests "Cluster API" "ClusterControllerAPITest"
        fi
        ;;
    "dashboard-ui")
        if check_application; then
            run_tests "Dashboard UI" "DashboardUITest"
        fi
        ;;
    "screenshots")
        show_screenshots
        ;;
    "clean")
        clean_output
        ;;
    "all")
        if check_application; then
            echo -e "${YELLOW}Running all automation tests...${NC}"
            run_tests "All" ""
            echo ""
            echo -e "${GREEN}Test execution completed!${NC}"
            echo ""
            show_screenshots
        fi
        ;;
    "help"|"-h"|"--help")
        echo "Usage: $0 [command]"
        echo ""
        echo "Commands:"
        echo "  all           Run all tests (default)"
        echo "  api           Run all API tests"
        echo "  ui            Run all UI tests"
        echo "  integration   Run all integration tests"
        echo "  cache-api     Run CacheController API tests"
        echo "  cluster-api   Run ClusterController API tests"
        echo "  dashboard-ui  Run Dashboard UI tests"
        echo "  screenshots   Show and open screenshot directory"
        echo "  clean         Clean test output directories"
        echo "  help          Show this help message"
        echo ""
        echo "Environment Variables:"
        echo "  TEST_BASE_URL      Application base URL (default: http://localhost:8080)"
        echo "  TEST_ADMIN_USER     Admin username (default: admin)"
        echo "  TEST_ADMIN_PASS     Admin password (default: admin)"
        echo "  TEST_BROWSER       Browser for UI tests (default: chrome)"
        echo "  TEST_HEADLESS      Run browser in headless mode (default: true)"
        echo ""
        echo "Examples:"
        echo "  $0 all                    # Run all tests"
        echo "  $0 ui                    # Run UI tests only"
        echo "  $0 screenshots           # View screenshots"
        echo "  TEST_HEADLESS=false $0 ui  # Run UI tests with visible browser"
        ;;
    *)
        echo -e "${RED}Unknown command: $1${NC}"
        echo "Run '$0 help' for usage information"
        exit 1
        ;;
esac

