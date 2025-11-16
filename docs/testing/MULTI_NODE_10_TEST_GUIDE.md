# Multi-Node 10 Test Guide

## Overview

This script tests the distributed cache system with **10 nodes** and automatically captures screenshots (BAU - Business As Usual) for each test phase.

## Features

- ✅ Starts 10 nodes automatically
- ✅ Tests cluster functionality across all nodes
- ✅ Captures screenshots at each test phase
- ✅ Generates comprehensive test summary
- ✅ Logs all node activities

## Prerequisites

- Java 17+
- Maven 3.6+
- Application built and ready

## Running the Test

### Quick Start

```bash
cd /Users/bhupendra/Documents/Projects/cache-system
./src/test/scripts/run-multi-node-10-test.sh
```

### What It Does

1. **Builds the application**
2. **Starts 10 nodes** on ports 8080-8089
3. **Waits for all nodes to start** (up to 60 seconds)
4. **Runs comprehensive tests**:
   - Cluster status check
   - Cache operations across nodes
   - Node health monitoring
   - Cluster communication
   - Final state verification
5. **Captures screenshots** at each phase
6. **Generates test summary**

## Screenshot Locations

Screenshots are saved as HTML files in:
```
test-output/screenshots/multi-node-10/
```

Each screenshot includes:
- Cluster status (JSON)
- Health status
- Cache statistics
- Timestamp

### Screenshot Types

- `node{N}_initial_state_*.html` - Initial state after startup
- `node{N}_cluster_status_*.html` - Cluster status check
- `node{N}_after_cache_operation_*.html` - After cache operations
- `node{N}_health_check_*.html` - Health monitoring
- `node{N}_communication_test_*.html` - Communication tests
- `node{N}_final_state_*.html` - Final state

## Test Summary

A comprehensive HTML summary is generated:
```
test-output/screenshots/multi-node-10/test_summary_*.html
```

## Logs

Node logs are saved in:
```
test-output/logs/multi-node-10/node{N}.log
```

## Viewing Results

```bash
# View screenshots
open test-output/screenshots/multi-node-10/

# View summary
open test-output/screenshots/multi-node-10/test_summary_*.html

# View logs
tail -f test-output/logs/multi-node-10/node*.log
```

## Running Java Tests

After starting the nodes with the script, you can also run the Java test class:

```bash
mvn test -Dtest=MultiNode10Test -Dtest.base.url=http://localhost:8080
```

## Troubleshooting

### Nodes Not Starting

- Check if ports 8080-8089 are available
- Check logs in `test-output/logs/multi-node-10/`
- Ensure sufficient system resources

### Timeout Issues

- Increase wait time in script (MAX_WAIT variable)
- Check network connectivity
- Verify firewall settings

### Resource Issues

Running 10 nodes requires:
- Sufficient memory (recommended: 4GB+)
- Available ports (8080-8089, 9090-9099)
- CPU resources

## Configuration

Edit the script to customize:
- Number of nodes (NUM_NODES)
- Base ports (BASE_HTTP_PORT, BASE_COMM_PORT)
- Timeout values
- Test scenarios

