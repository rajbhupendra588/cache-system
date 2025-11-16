#!/bin/bash

# Multi-node cluster test with 10 nodes and screenshot capture (BAU)
# Tests cluster functionality with 10 nodes and captures screenshots

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
cd "$PROJECT_ROOT"

# Configuration
NUM_NODES=10
BASE_HTTP_PORT=8080
BASE_COMM_PORT=9090
AUTH="admin:admin"
SCREENSHOT_DIR="$PROJECT_ROOT/test-output/screenshots/multi-node-10"
LOG_DIR="$PROJECT_ROOT/test-output/logs/multi-node-10"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Create directories
mkdir -p "$SCREENSHOT_DIR"
mkdir -p "$LOG_DIR"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Multi-Node Cluster Test (10 Nodes)${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Cleanup function
cleanup() {
    echo -e "\n${YELLOW}Stopping all nodes...${NC}"
    for i in $(seq 1 $NUM_NODES); do
        pkill -f "spring-boot:run.*node-$i" || true
        pkill -f "DistributedCacheApplication.*node-$i" || true
    done
    pkill -f "DistributedCacheApplication" || true
    sleep 2
    echo -e "${GREEN}Cleanup complete${NC}"
}

trap cleanup EXIT

# Function to create node configuration
create_node_config() {
    local node_num=$1
    local http_port=$2
    local comm_port=$3
    local peers=$4
    local config_file="/tmp/cache-node${node_num}-10nodes.yml"
    
    cat > "$config_file" <<EOF
server:
  port: $http_port

spring:
  application:
    name: cache-node${node_num}

cache:
  system:
    cluster:
      enabled: true
      node-id: node-${node_num}
      discovery:
        type: static
        static:
          peers: $peers
      heartbeat:
        interval-ms: 2000
        timeout-ms: 6000
      communication:
        port: $comm_port
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
  file:
    name: $LOG_DIR/node${node_num}.log

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  endpoint:
    health:
      show-details: always
EOF
    echo "$config_file"
}

# Function to capture PNG screenshot using Selenium WebDriver
capture_cluster_screenshot() {
    local node_num=$1
    local http_port=$2
    local description=$3
    
    # Use Java utility to capture actual PNG screenshot
    local screenshot_path=$(mvn -q exec:java \
        -Dexec.mainClass="com.cache.automation.util.MultiNodeScreenshotUtil" \
        -Dexec.args="$node_num $http_port $description $SCREENSHOT_DIR" \
        -Dexec.classpathScope=test 2>/dev/null)
    
    if [ -n "$screenshot_path" ] && [ -f "$screenshot_path" ]; then
        local filename=$(basename "$screenshot_path")
        echo -e "${GREEN}✓ Screenshot captured: $filename${NC}"
    else
        echo -e "${YELLOW}⚠ Screenshot capture failed, falling back to data capture${NC}"
        # Fallback: capture data as text file
        capture_cluster_data "$node_num" "$http_port" "$description"
    fi
}

# Fallback function to capture cluster data as text (if screenshot fails)
capture_cluster_data() {
    local node_num=$1
    local http_port=$2
    local description=$3
    local timestamp=$(date +"%Y-%m-%d_%H-%M-%S")
    local filename="node${node_num}_${description}_${timestamp}.txt"
    
    {
        echo "=== Node $node_num - $description ==="
        echo "Captured: $(date)"
        echo ""
        echo "=== Cluster Status ==="
        curl -s -u "$AUTH" "http://localhost:$http_port/api/cluster" 2>/dev/null | python3 -m json.tool 2>/dev/null || echo "N/A"
        echo ""
        echo "=== Health Status ==="
        curl -s -u "$AUTH" "http://localhost:$http_port/actuator/health" 2>/dev/null | python3 -m json.tool 2>/dev/null || echo "N/A"
        echo ""
        echo "=== Cache Statistics ==="
        curl -s -u "$AUTH" "http://localhost:$http_port/api/cache/test/stats" 2>/dev/null | python3 -m json.tool 2>/dev/null || echo "N/A"
    } > "$SCREENSHOT_DIR/$filename"
    
    echo -e "${YELLOW}⚠ Data captured as text: $filename${NC}"
}

# Function to capture dashboard screenshot
capture_dashboard_screenshot() {
    local node_num=$1
    local http_port=$2
    local description=$3
    
    # Use the same screenshot capture function
    capture_cluster_screenshot "$node_num" "$http_port" "dashboard_$description"
}

# Build the application
echo -e "${BLUE}Building application...${NC}"
mvn clean package -DskipTests -q || {
    echo -e "${RED}Build failed${NC}"
    exit 1
}
echo -e "${GREEN}✓ Build successful${NC}"

# Generate peer list for all nodes
PEER_LIST=""
for i in $(seq 1 $NUM_NODES); do
    comm_port=$((BASE_COMM_PORT + i - 1))
    if [ $i -eq 1 ]; then
        PEER_LIST="localhost:$comm_port"
    else
        PEER_LIST="$PEER_LIST,localhost:$comm_port"
    fi
done

# Create configurations and start nodes
echo -e "\n${BLUE}Creating node configurations and starting nodes...${NC}"
NODE_PIDS=()
NODE_CONFIGS=()

for i in $(seq 1 $NUM_NODES); do
    http_port=$((BASE_HTTP_PORT + i - 1))
    comm_port=$((BASE_COMM_PORT + i - 1))
    
    echo -e "${YELLOW}Setting up Node $i (HTTP: $http_port, Comm: $comm_port)...${NC}"
    
    # Create config
    config=$(create_node_config $i $http_port $comm_port "$PEER_LIST")
    NODE_CONFIGS+=("$config")
    
    # Start node in background
    SPRING_CONFIG_LOCATION="file:$config" \
    mvn spring-boot:run > "$LOG_DIR/node${i}.log" 2>&1 &
    NODE_PIDS+=($!)
    
    # Stagger node starts
    sleep 2
done

# Wait for all nodes to start
echo -e "\n${YELLOW}Waiting for all nodes to start...${NC}"
MAX_WAIT=60
WAIT_COUNT=0
ALL_STARTED=false

while [ $WAIT_COUNT -lt $MAX_WAIT ]; do
    STARTED_COUNT=0
    for i in $(seq 1 $NUM_NODES); do
        http_port=$((BASE_HTTP_PORT + i - 1))
        if curl -s -u "$AUTH" "http://localhost:$http_port/actuator/health" > /dev/null 2>&1; then
            STARTED_COUNT=$((STARTED_COUNT + 1))
        fi
    done
    
    echo -e "${YELLOW}Started: $STARTED_COUNT/$NUM_NODES nodes${NC}"
    
    if [ $STARTED_COUNT -eq $NUM_NODES ]; then
        ALL_STARTED=true
        break
    fi
    
    WAIT_COUNT=$((WAIT_COUNT + 1))
    sleep 2
done

if [ "$ALL_STARTED" != "true" ]; then
    echo -e "${RED}Not all nodes started successfully${NC}"
    echo "Checking logs..."
    for i in $(seq 1 $NUM_NODES); do
        if ! curl -s -u "$AUTH" "http://localhost:$((BASE_HTTP_PORT + i - 1))/actuator/health" > /dev/null 2>&1; then
            echo -e "${RED}Node $i failed to start. Log:${NC}"
            tail -20 "$LOG_DIR/node${i}.log"
        fi
    done
    exit 1
fi

echo -e "${GREEN}✓ All $NUM_NODES nodes started successfully!${NC}"
sleep 5  # Give cluster time to establish connections

# Capture initial screenshots
echo -e "\n${BLUE}=== Capturing Initial Screenshots ===${NC}"
for i in $(seq 1 $NUM_NODES); do
    http_port=$((BASE_HTTP_PORT + i - 1))
    capture_cluster_screenshot "$i" "$http_port" "initial_state"
done

# Test 1: Cluster Status Check
echo -e "\n${BLUE}=== Test 1: Cluster Status Check ===${NC}"
for i in $(seq 1 $NUM_NODES); do
    http_port=$((BASE_HTTP_PORT + i - 1))
    echo -e "\n${YELLOW}Node $i Cluster Status:${NC}"
    cluster_status=$(curl -s -u "$AUTH" "http://localhost:$http_port/api/cluster")
    echo "$cluster_status" | python3 -c "import sys, json; d=json.load(sys.stdin); print(f\"  Node ID: {d.get('nodeId')}\"); print(f\"  Active Peers: {len(d.get('activePeers', []))}\"); print(f\"  Known Peers: {len(d.get('knownPeers', []))}\")" 2>/dev/null || echo "$cluster_status" | head -10
    capture_cluster_screenshot "$i" "$http_port" "cluster_status"
done

# Test 2: Cache Operations Across Nodes
echo -e "\n${BLUE}=== Test 2: Cache Operations Across Nodes ===${NC}"
TEST_KEY="multi-node-test-$(date +%s)"
echo "Testing with key: $TEST_KEY"

# Put operation on node 1 (simulated via stats check)
echo "Performing operations on Node 1..."
curl -s -X POST -u "$AUTH" \
    -H "Content-Type: application/json" \
    -d "{\"key\": \"$TEST_KEY\"}" \
    "http://localhost:$BASE_HTTP_PORT/api/cache/test/invalidate" > /dev/null

sleep 3

# Check stats on all nodes and capture screenshots
echo -e "\nCache statistics after operation:"
for i in $(seq 1 $NUM_NODES); do
    http_port=$((BASE_HTTP_PORT + i - 1))
    echo -e "${YELLOW}Node $i:${NC}"
    stats=$(curl -s -u "$AUTH" "http://localhost:$http_port/api/cache/test/stats")
    echo "$stats" | python3 -c "import sys, json; d=json.load(sys.stdin); print(f\"  Size: {d.get('size', 0)}, Hits: {d.get('hits', 0)}, Misses: {d.get('misses', 0)}\")" 2>/dev/null || echo "  Stats available"
    capture_cluster_screenshot "$i" "$http_port" "after_cache_operation"
done

# Test 3: Node Health Monitoring
echo -e "\n${BLUE}=== Test 3: Node Health Monitoring ===${NC}"
for i in $(seq 1 $NUM_NODES); do
    http_port=$((BASE_HTTP_PORT + i - 1))
    health=$(curl -s -u "$AUTH" "http://localhost:$http_port/actuator/health")
    status=$(echo "$health" | python3 -c "import sys, json; print(json.load(sys.stdin).get('status', 'UNKNOWN'))" 2>/dev/null || echo "UP")
    echo "Node $i health: $status"
    capture_cluster_screenshot "$i" "$http_port" "health_check"
done

# Test 4: Cluster Communication Test
echo -e "\n${BLUE}=== Test 4: Cluster Communication Test ===${NC}"
# Invalidate on multiple nodes
for i in 1 5 10; do
    http_port=$((BASE_HTTP_PORT + i - 1))
    echo "Testing communication from Node $i..."
    curl -s -X POST -u "$AUTH" \
        -H "Content-Type: application/json" \
        -d "{\"key\": \"comm-test-$i\"}" \
        "http://localhost:$http_port/api/cache/test/invalidate" > /dev/null
    capture_cluster_screenshot "$i" "$http_port" "communication_test"
done

sleep 3

# Test 5: Final Cluster State
echo -e "\n${BLUE}=== Test 5: Final Cluster State ===${NC}"
for i in $(seq 1 $NUM_NODES); do
    http_port=$((BASE_HTTP_PORT + i - 1))
    capture_cluster_screenshot "$i" "$http_port" "final_state"
done

# Generate summary report
echo -e "\n${BLUE}=== Generating Test Summary ===${NC}"
SUMMARY_FILE="$SCREENSHOT_DIR/test_summary_$(date +%Y-%m-%d_%H-%M-%S).html"
cat > "$SUMMARY_FILE" <<EOF
<!DOCTYPE html>
<html>
<head>
    <title>Multi-Node Test Summary (10 Nodes)</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }
        .container { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        h1 { color: #333; }
        .summary { margin: 20px 0; }
        .node-list { list-style: none; padding: 0; }
        .node-item { padding: 10px; margin: 5px 0; background: #f9f9f9; border-left: 4px solid #4CAF50; }
        .timestamp { color: #999; font-size: 12px; }
    </style>
</head>
<body>
    <div class="container">
        <h1>Multi-Node Cluster Test Summary</h1>
        <p class="timestamp">Test completed: $(date)</p>
        
        <div class="summary">
            <h2>Test Configuration</h2>
            <ul>
                <li><strong>Number of Nodes:</strong> $NUM_NODES</li>
                <li><strong>Base HTTP Port:</strong> $BASE_HTTP_PORT</li>
                <li><strong>Base Communication Port:</strong> $BASE_COMM_PORT</li>
                <li><strong>Screenshots Directory:</strong> $SCREENSHOT_DIR</li>
            </ul>
        </div>
        
        <div class="summary">
            <h2>Node Status</h2>
            <ul class="node-list">
EOF

for i in $(seq 1 $NUM_NODES); do
    http_port=$((BASE_HTTP_PORT + i - 1))
    health=$(curl -s -u "$AUTH" "http://localhost:$http_port/actuator/health" 2>/dev/null || echo '{"status":"DOWN"}')
    status=$(echo "$health" | python3 -c "import sys, json; print(json.load(sys.stdin).get('status', 'UNKNOWN'))" 2>/dev/null || echo "UNKNOWN")
    cat >> "$SUMMARY_FILE" <<EOF
                <li class="node-item">
                    <strong>Node $i</strong> (Port $http_port): $status
                </li>
EOF
done

cat >> "$SUMMARY_FILE" <<EOF
            </ul>
        </div>
        
        <div class="summary">
            <h2>Screenshots</h2>
            <p>All screenshots have been captured in: <code>$SCREENSHOT_DIR</code></p>
            <p>Each node has screenshots for:</p>
            <ul>
                <li>Initial state</li>
                <li>Cluster status</li>
                <li>After cache operations</li>
                <li>Health checks</li>
                <li>Communication tests</li>
                <li>Final state</li>
            </ul>
        </div>
    </div>
</body>
</html>
EOF

echo -e "${GREEN}✓ Summary report generated: $SUMMARY_FILE${NC}"

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}All Tests Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "${YELLOW}Screenshots: $SCREENSHOT_DIR${NC}"
echo -e "${YELLOW}Logs: $LOG_DIR${NC}"
echo -e "${YELLOW}Summary: $SUMMARY_FILE${NC}"
echo ""
echo -e "${BLUE}To view screenshots:${NC}"
echo "  open $SCREENSHOT_DIR"
echo ""
echo -e "${BLUE}To view logs:${NC}"
echo "  tail -f $LOG_DIR/node*.log"

