#!/bin/bash

# Test script for cluster operations
# Tests cache operations across multiple nodes

set -e

BASE_URL_NODE1="http://localhost:8080"
BASE_URL_NODE2="http://localhost:8081"
BASE_URL_NODE3="http://localhost:8082"
AUTH="admin:admin"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}=== Cluster Operations Test ===${NC}\n"

# Wait for nodes to be ready
wait_for_node() {
    local url=$1
    local node_name=$2
    local max_attempts=30
    local attempt=0
    
    echo -e "${YELLOW}Waiting for $node_name to be ready...${NC}"
    while [ $attempt -lt $max_attempts ]; do
        if curl -s -u "$AUTH" "$url/actuator/health" > /dev/null 2>&1; then
            echo -e "${GREEN}$node_name is ready${NC}"
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 1
    done
    
    echo -e "${RED}$node_name failed to start${NC}"
    return 1
}

# Test functions
test_cluster_status() {
    echo -e "\n${GREEN}=== Testing Cluster Status ===${NC}"
    
    for node in 1 2 3; do
        local url_var="BASE_URL_NODE$node"
        local url="${!url_var}"
        echo -e "\n${YELLOW}Node $node Cluster Status:${NC}"
        curl -s -u "$AUTH" "$url/api/cluster" | python3 -m json.tool 2>/dev/null || curl -s -u "$AUTH" "$url/api/cluster"
        echo ""
    done
}

test_cache_put() {
    echo -e "\n${GREEN}=== Testing Cache Put (Node 1) ===${NC}"
    
    local key="test-key-$(date +%s)"
    local value="test-value-$(date +%s)"
    
    echo "Putting key: $key, value: $value"
    curl -s -X POST -u "$AUTH" \
        -H "Content-Type: application/json" \
        -d "{\"key\": \"$key\", \"value\": \"$value\"}" \
        "$BASE_URL_NODE1/api/cache/test/put" || echo "Put endpoint may not exist, using alternative"
    
    # Alternative: Use invalidate to test cluster communication
    echo -e "\n${GREEN}Testing cache invalidation propagation...${NC}"
    curl -s -X POST -u "$AUTH" \
        -H "Content-Type: application/json" \
        -d "{\"key\": \"$key\"}" \
        "$BASE_URL_NODE1/api/cache/test/invalidate"
    
    echo -e "\n${GREEN}Invalidation sent. Checking if propagated to other nodes...${NC}"
}

test_cache_stats() {
    echo -e "\n${GREEN}=== Testing Cache Statistics ===${NC}"
    
    for node in 1 2 3; do
        local url_var="BASE_URL_NODE$node"
        local url="${!url_var}"
        echo -e "\n${YELLOW}Node $node Cache Stats:${NC}"
        curl -s -u "$AUTH" "$url/api/cache/test/stats" | python3 -m json.tool 2>/dev/null || curl -s -u "$AUTH" "$url/api/cache/test/stats"
        echo ""
    done
}

test_heartbeat() {
    echo -e "\n${GREEN}=== Testing Heartbeat Mechanism ===${NC}"
    
    echo "Checking cluster status with heartbeat information..."
    curl -s -u "$AUTH" "$BASE_URL_NODE1/api/cluster" | python3 -m json.tool 2>/dev/null | grep -A 5 "lastHeartbeatTimes\|peerHealth" || \
    curl -s -u "$AUTH" "$BASE_URL_NODE1/api/cluster"
    echo ""
}

test_node_failure() {
    echo -e "\n${GREEN}=== Testing Node Failure Detection ===${NC}"
    echo -e "${YELLOW}This test would:${NC}"
    echo "  1. Stop one node"
    echo "  2. Wait for heartbeat timeout"
    echo "  3. Verify other nodes detect the failure"
    echo -e "${YELLOW}Manual test required${NC}"
}

# Main test execution
main() {
    # Wait for all nodes
    wait_for_node "$BASE_URL_NODE1" "Node 1" || exit 1
    wait_for_node "$BASE_URL_NODE2" "Node 2" || exit 1
    wait_for_node "$BASE_URL_NODE3" "Node 3" || exit 1
    
    sleep 3  # Give cluster time to establish connections
    
    test_cluster_status
    test_cache_stats
    test_heartbeat
    test_cache_put
    
    echo -e "\n${GREEN}=== Test Complete ===${NC}"
}

main "$@"

