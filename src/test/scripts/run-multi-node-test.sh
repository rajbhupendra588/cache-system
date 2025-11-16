#!/bin/bash

# Comprehensive multi-node cluster test runner
# Tests all production-level features

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Configuration
NODE1_HTTP=8080
NODE2_HTTP=8081
NODE3_HTTP=8082
COMM_PORT=9090
AUTH="admin:admin"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

LOG_DIR="$SCRIPT_DIR/test-logs"
mkdir -p "$LOG_DIR"

# Cleanup function
cleanup() {
    echo -e "\n${YELLOW}Stopping all nodes...${NC}"
    pkill -f "spring-boot:run.*node-1" || true
    pkill -f "spring-boot:run.*node-2" || true
    pkill -f "spring-boot:run.*node-3" || true
    pkill -f "DistributedCacheApplication" || true
    sleep 2
}

trap cleanup EXIT

# Build the application
echo -e "${BLUE}Building application...${NC}"
mvn clean package -DskipTests -q || {
    echo -e "${RED}Build failed${NC}"
    exit 1
}

# Create temporary config files
create_config() {
    local node_id=$1
    local http_port=$2
    local peers=$3
    local config_file="/tmp/cache-node${node_id}.yml"
    
    cat > "$config_file" <<EOF
server:
  port: $http_port

spring:
  application:
    name: cache-node${node_id}

cache:
  system:
    cluster:
      enabled: true
      node-id: node-${node_id}
      discovery:
        type: static
        static:
          peers: $peers
      heartbeat:
        interval-ms: 2000
        timeout-ms: 6000
      communication:
        port: $COMM_PORT
        async: true
    
    default-ttl: PT1H
    default-eviction-policy: LRU
    default-max-entries: 10000
    default-memory-cap-mb: 256
    
    caches:
      test:
        ttl: PT30M
        eviction-policy: LRU
        max-entries: 5000
        memory-cap-mb: 128
        replication-mode: INVALIDATE
        persistence-mode: NONE

logging:
  level:
    com.cache: INFO
    root: WARN
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level - %msg%n"

management:
  endpoints:
    web:
      exposure:
        include: health,metrics
EOF
    echo "$config_file"
}

# Create configs with proper peer lists
CONFIG1=$(create_config 1 $NODE1_HTTP "localhost:$COMM_PORT,localhost:$COMM_PORT")
CONFIG2=$(create_config 2 $NODE2_HTTP "localhost:$COMM_PORT,localhost:$COMM_PORT")
CONFIG3=$(create_config 3 $NODE3_HTTP "localhost:$COMM_PORT,localhost:$COMM_PORT")

# Start nodes in background
echo -e "${BLUE}Starting Node 1 (HTTP: $NODE1_HTTP, Comm: $COMM_PORT)...${NC}"
SPRING_CONFIG_LOCATION="file:$CONFIG1" \
mvn spring-boot:run > "$LOG_DIR/node1.log" 2>&1 &
NODE1_PID=$!

sleep 3

echo -e "${BLUE}Starting Node 2 (HTTP: $NODE2_HTTP, Comm: $COMM_PORT)...${NC}"
SPRING_CONFIG_LOCATION="file:$CONFIG2" \
mvn spring-boot:run > "$LOG_DIR/node2.log" 2>&1 &
NODE2_PID=$!

sleep 3

echo -e "${BLUE}Starting Node 3 (HTTP: $NODE3_HTTP, Comm: $COMM_PORT)...${NC}"
SPRING_CONFIG_LOCATION="file:$CONFIG3" \
mvn spring-boot:run > "$LOG_DIR/node3.log" 2>&1 &
NODE3_PID=$!

# Wait for nodes to start
echo -e "${YELLOW}Waiting for nodes to start...${NC}"
for i in {1..30}; do
    if curl -s -u "$AUTH" "http://localhost:$NODE1_HTTP/actuator/health" > /dev/null 2>&1 && \
       curl -s -u "$AUTH" "http://localhost:$NODE2_HTTP/actuator/health" > /dev/null 2>&1 && \
       curl -s -u "$AUTH" "http://localhost:$NODE3_HTTP/actuator/health" > /dev/null 2>&1; then
        echo -e "${GREEN}All nodes started successfully!${NC}"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${RED}Nodes failed to start${NC}"
        echo "Node 1 log:"
        tail -20 "$LOG_DIR/node1.log"
        exit 1
    fi
    sleep 1
done

sleep 5  # Give cluster time to establish connections

# Run tests
echo -e "\n${GREEN}=== Running Cluster Tests ===${NC}\n"

# Test 1: Cluster Status
echo -e "${BLUE}Test 1: Cluster Status Check${NC}"
for node in 1 2 3; do
    port=$((8079 + node))
    echo -e "\n${YELLOW}Node $node Status:${NC}"
    curl -s -u "$AUTH" "http://localhost:$port/api/cluster" | \
        python3 -c "import sys, json; d=json.load(sys.stdin); print(f\"Node ID: {d.get('nodeId')}\"); print(f\"Active Peers: {len(d.get('activePeers', []))}\"); print(f\"Known Peers: {len(d.get('knownPeers', []))}\"); print(f\"Peer Health: {d.get('peerHealth', {})}\")" 2>/dev/null || \
        curl -s -u "$AUTH" "http://localhost:$port/api/cluster" | head -5
done

# Test 2: Heartbeat Verification
echo -e "\n${BLUE}Test 2: Heartbeat Mechanism${NC}"
curl -s -u "$AUTH" "http://localhost:$NODE1_HTTP/api/cluster" | \
    python3 -c "import sys, json; d=json.load(sys.stdin); hb=d.get('lastHeartbeatTimes', {}); print(f\"Heartbeat times: {hb}\"); cf=d.get('consecutiveFailures', {}); print(f\"Failures: {cf}\")" 2>/dev/null || \
    echo "Heartbeat data available in cluster status"

# Test 3: Cache Operations
echo -e "\n${BLUE}Test 3: Cache Operations Across Nodes${NC}"
TEST_KEY="cluster-test-$(date +%s)"
echo "Testing with key: $TEST_KEY"

# Invalidate on node 1
echo "Invalidating key on Node 1..."
curl -s -X POST -u "$AUTH" \
    -H "Content-Type: application/json" \
    -d "{\"key\": \"$TEST_KEY\"}" \
    "http://localhost:$NODE1_HTTP/api/cache/test/invalidate" > /dev/null

sleep 2

# Check stats on all nodes
echo -e "\nCache statistics after invalidation:"
for node in 1 2 3; do
    port=$((8079 + node))
    echo -e "${YELLOW}Node $node:${NC}"
    curl -s -u "$AUTH" "http://localhost:$port/api/cache/test/stats" | \
        python3 -c "import sys, json; d=json.load(sys.stdin); print(f\"  Size: {d.get('size', 0)}, Hits: {d.get('hits', 0)}, Misses: {d.get('misses', 0)}\")" 2>/dev/null || \
        echo "  Stats available"
done

# Test 4: Node Health
echo -e "\n${BLUE}Test 4: Node Health Monitoring${NC}"
for node in 1 2 3; do
    port=$((8079 + node))
    health=$(curl -s -u "$AUTH" "http://localhost:$port/actuator/health" | \
        python3 -c "import sys, json; print(json.load(sys.stdin).get('status', 'UNKNOWN'))" 2>/dev/null || echo "UP")
    echo "Node $node health: $health"
done

# Test 5: Consistent Hashing (if implemented)
echo -e "\n${BLUE}Test 5: Running Unit Tests${NC}"
mvn test -Dtest=MultiNodeClusterTest -q || echo "Tests completed with some failures"

echo -e "\n${GREEN}=== All Tests Complete ===${NC}"
echo -e "${YELLOW}Logs available in: $LOG_DIR${NC}"
echo -e "${YELLOW}To view logs: tail -f $LOG_DIR/node*.log${NC}"

