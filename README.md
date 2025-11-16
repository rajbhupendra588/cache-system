# Distributed Cache Management System

A custom Java + Spring Boot based distributed cache management system designed to reduce latency in ticket management systems by caching frequently accessed data across multiple JVM instances.

> 📚 **Documentation Index:** For a complete overview of all documentation, see [docs/README.md](docs/README.md) - Your one-stop guide to all documentation organized by use case and category.

## ✅ Production Readiness Status

**Current Status:** **PRODUCTION READY** (with recommendations)

This system has been enhanced with:
- ✅ Security hardening (environment variable credentials, input validation)
- ✅ Resilience patterns (retry logic, circuit breaker, graceful shutdown)
- ✅ Performance optimizations (Kryo serialization, connection management)
- ✅ Comprehensive testing (unit tests)
- ✅ Observability (structured logging, audit trail, metrics)

**Production Deployment:** See [`docs/production/PRODUCTION_DEPLOYMENT.md`](docs/production/PRODUCTION_DEPLOYMENT.md) for detailed deployment guide.

**Note:** TLS for inter-node communication is recommended for high-security environments but not required for internal deployments.

## Features

- **In-Memory Caching**: Fast LRU/LFU/TTL-based eviction policies
- **Thundering Herd Prevention**: Single loader execution per key during cache misses
- **Cluster Coordination**: Distributed invalidation and replication across nodes
- **REST API**: Full admin API for cache management
- **Metrics & Observability**: Micrometer metrics with Prometheus support
- **Health Checks**: Spring Boot Actuator integration
- **Security**: Basic authentication for admin endpoints

## Architecture

The system consists of:

1. **Cache Core** - In-memory cache manager with eviction and TTL
2. **Cluster Coordinator** - Peer discovery and message coordination
3. **REST API** - Admin endpoints for cache management
4. **Metrics** - Micrometer integration for observability
5. **Health Checks** - Custom health indicators

> 🏗️ **System Design:** For detailed architecture diagrams and system design, see [`docs/architecture/SYSTEM_DESIGN.md`](docs/architecture/SYSTEM_DESIGN.md) - Comprehensive visual documentation with Mermaid diagrams.

## Quick Start

### For Developers Using This Cache

**New to caching?** Start here: [`docs/getting-started/QUICK_START.md`](docs/getting-started/QUICK_START.md) - Get started in 5 minutes!

**Using as a library?** See: [`docs/guides/LIBRARY_USAGE.md`](docs/guides/LIBRARY_USAGE.md) - Complete guide for integrating into your application

**Need detailed guide?** See: [`docs/guides/DEVELOPER_GUIDE.md`](docs/guides/DEVELOPER_GUIDE.md) - Complete developer documentation

### Prerequisites

- Java 17+
- Maven 3.6+
- Spring Boot 3.2+

### Building

```bash
mvn clean package
```

### Running

```bash
java -jar target/distributed-cache-system-1.0.0-SNAPSHOT.jar
```

### Configuration

Edit `src/main/resources/application.yml` to configure:

- Cache settings (TTL, eviction policy, memory limits)
- Cluster membership (static peer list or multicast)
- Communication ports
- Security settings

### Example Configuration

```yaml
cache:
  system:
    cluster:
      node-id: node-1
      discovery:
        type: static
        static:
          peers: localhost:8081,localhost:8082
    caches:
      issue:
        ttl: PT30M
        eviction-policy: LRU
        max-entries: 50000
        replication-mode: INVALIDATE
```

## Usage

### Programmatic API

```java
@Autowired
private CacheService cacheService;

// Get or load with automatic caching
Issue issue = cacheService.getOrLoad("issue", "issue:123", 
    () -> loadIssueFromDatabase(123), Duration.ofMinutes(30));

// Put directly
cacheService.put("issue", "issue:123", issue, Duration.ofMinutes(30));

// Invalidate
cacheService.invalidate("issue", "issue:123");
```

### REST API

#### List all caches
```bash
curl -u admin:admin http://localhost:8080/api/cache
```

#### Get cache statistics
```bash
curl -u admin:admin http://localhost:8080/api/cache/issue/stats
```

#### Invalidate a key
```bash
curl -X POST -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{"key": "issue:123"}' \
  http://localhost:8080/api/cache/issue/invalidate
```

#### Clear entire cache
```bash
curl -X POST -u admin:admin \
  http://localhost:8080/api/cache/issue/clear
```

#### Get cluster status
```bash
curl -u admin:admin http://localhost:8080/api/cluster
```

### Metrics

Metrics are exposed at:
- `/actuator/metrics` - All metrics
- `/actuator/prometheus` - Prometheus format
- `/actuator/health` - Health check

Key metrics:
- `customcache.hits{cache}` - Cache hits
- `customcache.misses{cache}` - Cache misses
- `customcache.load.time.ms{cache}` - Load time histogram
- `customcache.size.entries{cache}` - Current cache size
- `customcache.memory.bytes{cache}` - Memory usage

## Cluster Setup

### Single Node (Local Mode)

Set `replication-mode: NONE` in cache configuration. No cluster coordination needed.

### Multi-Node Cluster

1. **Static Discovery**: Configure peer list in `application.yml`
   ```yaml
   cluster:
     discovery:
       type: static
       static:
         peers: node1:9090,node2:9090,node3:9090
   ```

2. **Start each node** with different ports:
   ```bash
   # Node 1
   java -jar app.jar --server.port=8080 --cache.system.cluster.node-id=node-1
   
   # Node 2
   java -jar app.jar --server.port=8081 --cache.system.cluster.node-id=node-2
   ```

3. **Replication Modes**:
   - `INVALIDATE`: On write, peers invalidate their cache (eventual consistency)
   - `REPLICATE`: On write, value is replicated to peers (faster reads, higher memory)
   - `NONE`: No cluster coordination

## Performance Targets

- **Cache Hit Latency**: <2ms (in-memory)
- **Cache Miss + Load**: <300ms (with optimized DB queries)
- **End-to-End (Cache Hit)**: <500ms
- **Throughput**: 5,000 reads/sec per JVM

## Security

Default credentials:
- Username: `admin`
- Password: `admin`

**⚠️ Change these in production!**

To customize, edit `SecurityConfiguration.java` or use Spring Security with JWT/OAuth.

## Development

### Project Structure

```
src/main/java/com/cache/
├── core/              # Core cache interfaces and implementations
├── cluster/           # Cluster coordination components
├── api/               # REST API controllers
├── config/            # Configuration classes
├── metrics/           # Metrics integration
└── health/            # Health indicators
```

### Testing

```bash
mvn test
```

### Running Tests

```bash
mvn test
```

## Troubleshooting

### Cache Hit Ratio Low

1. Check TTL settings - may be too short
2. Check eviction policy - entries may be evicted too aggressively
3. Review cache size limits
4. Check metrics: `customcache.evictions{cache}`

### Invalidations Not Propagating

1. Check cluster status: `GET /api/cluster`
2. Verify network connectivity between nodes
3. Check firewall rules for communication port (default: 9090)
4. Review logs for connection errors

### High Memory Usage

1. Reduce `max-entries` or `memory-cap-mb` per cache
2. Enable more aggressive eviction
3. Reduce TTL to expire entries faster
4. Monitor `customcache.memory.bytes{cache}`



## License

This is a custom implementation for internal use.

## Support

For issues and questions, refer to the [docs/reference/FR_NFR_Document.md](docs/reference/FR_NFR_Document.md) for detailed specifications.
