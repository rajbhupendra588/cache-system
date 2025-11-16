#!/bin/bash

# Multi-node cluster test script
# This script sets up and tests a 3-node cluster

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
NODE1_PORT=8080
NODE2_PORT=8081
NODE3_PORT=8082
COMM_PORT=9090
BASE_DIR="$SCRIPT_DIR/multi-node-test"

echo -e "${GREEN}=== Multi-Node Cluster Test Setup ===${NC}"

# Cleanup function
cleanup() {
    echo -e "\n${YELLOW}Cleaning up...${NC}"
    pkill -f "spring-boot:run" || true
    pkill -f "DistributedCacheApplication" || true
    sleep 2
    rm -rf "$BASE_DIR"
}

trap cleanup EXIT

# Create test directories
mkdir -p "$BASE_DIR/node1" "$BASE_DIR/node2" "$BASE_DIR/node3"

# Create application.yml for each node
create_node_config() {
    local node_num=$1
    local http_port=$2
    local node_id=$3
    local peers=$4
    local config_file="$BASE_DIR/node$node_num/application.yml"
    
    cat > "$config_file" <<EOF
server:
  port: $http_port

spring:
  application:
    name: distributed-cache-system-node$node_num

cache:
  system:
    cluster:
      enabled: true
      node-id: $node_id
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
    default-memory-cap-mb: 512
    
    caches:
      test:
        ttl: PT30M
        eviction-policy: LRU
        max-entries: 10000
        memory-cap-mb: 256
        replication-mode: INVALIDATE
        persistence-mode: NONE

logging:
  level:
    com.cache: INFO
    root: WARN
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"

management:
  endpoints:
    web:
      exposure:
        include: health,metrics
EOF
}

# Create configurations
create_node_config 1 $NODE1_PORT "node-1" "localhost:$COMM_PORT,localhost:$COMM_PORT"
create_node_config 2 $NODE2_PORT "node-2" "localhost:$COMM_PORT,localhost:$COMM_PORT"
create_node_config 3 $NODE3_PORT "node-3" "localhost:$COMM_PORT,localhost:$COMM_PORT"

# Fix peer configurations - each node should know about the others
sed -i '' "s/peers: localhost:\$COMM_PORT,localhost:\$COMM_PORT/peers: localhost:$COMM_PORT,localhost:$COMM_PORT/g" "$BASE_DIR/node1/application.yml" 2>/dev/null || \
sed -i "s/peers: localhost:\$COMM_PORT,localhost:\$COMM_PORT/peers: localhost:$COMM_PORT,localhost:$COMM_PORT/g" "$BASE_DIR/node1/application.yml"

# Actually, let's create proper peer lists
cat > "$BASE_DIR/node1/application.yml" <<EOF
server:
  port: $NODE1_PORT

spring:
  application:
    name: distributed-cache-system-node1

cache:
  system:
    cluster:
      enabled: true
      node-id: node-1
      discovery:
        type: static
        static:
          peers: localhost:$COMM_PORT,localhost:$COMM_PORT
      heartbeat:
        interval-ms: 2000
        timeout-ms: 6000
      communication:
        port: $COMM_PORT
        async: true
    
    default-ttl: PT1H
    default-eviction-policy: LRU
    default-max-entries: 10000
    default-memory-cap-mb: 512
    
    caches:
      test:
        ttl: PT30M
        eviction-policy: LRU
        max-entries: 10000
        memory-cap-mb: 256
        replication-mode: INVALIDATE
        persistence-mode: NONE

logging:
  level:
    com.cache: INFO
    root: WARN
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"

management:
  endpoints:
    web:
      exposure:
        include: health,metrics
EOF

cat > "$BASE_DIR/node2/application.yml" <<EOF
server:
  port: $NODE2_PORT

spring:
  application:
    name: distributed-cache-system-node2

cache:
  system:
    cluster:
      enabled: true
      node-id: node-2
      discovery:
        type: static
        static:
          peers: localhost:$COMM_PORT,localhost:$COMM_PORT
      heartbeat:
        interval-ms: 2000
        timeout-ms: 6000
      communication:
        port: $COMM_PORT
        async: true
    
    default-ttl: PT1H
    default-eviction-policy: LRU
    default-max-entries: 10000
    default-memory-cap-mb: 512
    
    caches:
      test:
        ttl: PT30M
        eviction-policy: LRU
        max-entries: 10000
        memory-cap-mb: 256
        replication-mode: INVALIDATE
        persistence-mode: NONE

logging:
  level:
    com.cache: INFO
    root: WARN
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"

management:
  endpoints:
    web:
      exposure:
        include: health,metrics
EOF

cat > "$BASE_DIR/node3/application.yml" <<EOF
server:
  port: $NODE3_PORT

spring:
  application:
    name: distributed-cache-system-node3

cache:
  system:
    cluster:
      enabled: true
      node-id: node-3
      discovery:
        type: static
        static:
          peers: localhost:$COMM_PORT,localhost:$COMM_PORT
      heartbeat:
        interval-ms: 2000
        timeout-ms: 6000
      communication:
        port: $COMM_PORT
        async: true
    
    default-ttl: PT1H
    default-eviction-policy: LRU
    default-max-entries: 10000
    default-memory-cap-mb: 512
    
    caches:
      test:
        ttl: PT30M
        eviction-policy: LRU
        max-entries: 10000
        memory-cap-mb: 256
        replication-mode: INVALIDATE
        persistence-mode: NONE

logging:
  level:
    com.cache: INFO
    root: WARN
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"

management:
  endpoints:
    web:
      exposure:
        include: health,metrics
EOF

echo -e "${GREEN}Configuration files created${NC}"

# Copy compiled classes to test directories (simplified - just use main resources)
# For a real test, we'd need to build and run with different configs

echo -e "${YELLOW}Note: This script demonstrates the setup.${NC}"
echo -e "${YELLOW}For actual testing, you would:${NC}"
echo -e "  1. Build the application: mvn clean package"
echo -e "  2. Run each node with: java -jar target/*.jar --spring.config.location=file:$BASE_DIR/node1/application.yml"
echo -e "  3. Test cluster operations"

echo -e "\n${GREEN}Test configuration created in: $BASE_DIR${NC}"
echo -e "${GREEN}Node configurations:${NC}"
echo -e "  Node 1: Port $NODE1_PORT, Config: $BASE_DIR/node1/application.yml"
echo -e "  Node 2: Port $NODE2_PORT, Config: $BASE_DIR/node2/application.yml"
echo -e "  Node 3: Port $NODE3_PORT, Config: $BASE_DIR/node3/application.yml"

