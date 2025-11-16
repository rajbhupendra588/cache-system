# Production Deployment Guide

## Pre-Deployment Checklist

### ✅ Security
- [x] Environment variables for credentials configured
- [x] Input validation on all REST endpoints
- [ ] TLS certificates configured (if using TLS)
- [ ] Firewall rules configured
- [ ] Admin access restricted to internal network

### ✅ Reliability
- [x] Graceful shutdown implemented
- [x] Retry logic with exponential backoff
- [x] Circuit breaker for cluster communication
- [x] Socket timeouts configured
- [x] Error handling improved

### ✅ Performance
- [x] Kryo serialization implemented
- [x] Connection management optimized
- [ ] Load testing completed
- [ ] GC tuning configured
- [ ] Memory limits validated

### ✅ Observability
- [x] Structured logging with correlation IDs
- [x] Audit trail for admin operations
- [x] Micrometer metrics integrated
- [ ] Prometheus alerting rules configured
- [ ] Grafana dashboards created

### ✅ Testing
- [x] Unit tests created
- [ ] Integration tests completed
- [ ] Load tests performed
- [ ] Failure scenario tests completed

## Deployment Steps

### 1. Environment Setup

```bash
# Set environment variables
export CACHE_ADMIN_USERNAME=your-secure-username
export CACHE_ADMIN_PASSWORD=your-secure-password
export SPRING_PROFILES_ACTIVE=production

# Set JVM options for production
export JAVA_OPTS="-Xms4g -Xmx8g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### 2. Configuration

Create `application-production.yml`:

```yaml
server:
  port: 8080

cache:
  system:
    cluster:
      node-id: ${HOSTNAME}
      discovery:
        type: static
        static:
          peers: node1:9090,node2:9090,node3:9090
      communication:
        port: 9090
        async: true

logging:
  level:
    com.cache: INFO
    AUDIT: INFO
  file:
    name: /var/log/cache-system/cache-system.log
```

### 3. Build and Deploy

```bash
# Build
mvn clean package -DskipTests=false

# Run tests
mvn test

# Deploy
java $JAVA_OPTS -jar target/distributed-cache-system-1.0.0-SNAPSHOT.jar \
  --spring.config.additional-location=file:/etc/cache-system/application-production.yml
```

### 4. Health Check

```bash
# Check health
curl http://localhost:8080/actuator/health

# Check metrics
curl http://localhost:8080/actuator/metrics

# Check cache status
curl -u $CACHE_ADMIN_USERNAME:$CACHE_ADMIN_PASSWORD \
  http://localhost:8080/api/cache
```

### 5. Monitoring Setup

1. **Prometheus Configuration** (`prometheus.yml`):
```yaml
scrape_configs:
  - job_name: 'cache-system'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
```

2. **Alert Rules** (`alerts.yml`):
```yaml
groups:
  - name: cache_alerts
    rules:
      - alert: CacheHitRatioLow
        expr: rate(customcache_hits_total[5m]) / rate(customcache_requests_total[5m]) < 0.7
        for: 5m
        annotations:
          summary: "Cache hit ratio below 70%"
      
      - alert: CacheMemoryHigh
        expr: customcache_memory_bytes / 1024 / 1024 / 1024 > 0.85
        for: 5m
        annotations:
          summary: "Cache memory usage above 85%"
```

## Production Configuration Recommendations

### JVM Settings

```bash
JAVA_OPTS="-Xms8g -Xmx16g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:G1HeapRegionSize=16m \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/cache-system/heap-dump.hprof \
  -Djava.security.egd=file:/dev/./urandom"
```

### Cache Configuration

For high-traffic production:

```yaml
cache:
  system:
    caches:
      issue:
        ttl: PT15M  # Shorter TTL for frequently updated data
        max-entries: 100000
        memory-cap-mb: 4096
        replication-mode: INVALIDATE
```

### Cluster Configuration

For multi-node deployment:

```yaml
cache:
  system:
    cluster:
      heartbeat:
        interval-ms: 3000  # More frequent heartbeats
        timeout-ms: 10000  # Shorter timeout
      communication:
        async: true  # Always use async for production
```

## Monitoring & Alerting

### Key Metrics to Monitor

1. **Cache Performance**
   - `customcache_hits_total` / `customcache_requests_total` → Hit ratio
   - `customcache_load_time_ms` → Load latency (P95, P99)
   - `customcache_size_entries` → Cache size

2. **System Health**
   - `jvm_memory_used_bytes` → Memory usage
   - `jvm_gc_pause_seconds` → GC pauses
   - `process_cpu_usage` → CPU usage

3. **Cluster Health**
   - `customcache_cluster_nodes` → Active nodes
   - `customcache_replication_latency_ms` → Replication latency

### Alert Thresholds

- **Cache Hit Ratio < 70%** → Investigate cache effectiveness
- **Memory Usage > 85%** → Risk of OOM, increase heap or reduce cache size
- **GC Pause > 200ms** → Tune GC settings
- **Node Unreachable > 2 minutes** → Network or node issue
- **Load Time P99 > 500ms** → Performance degradation

## Troubleshooting

### High Memory Usage

1. Check cache sizes: `GET /api/cache/{name}/stats`
2. Reduce `max-entries` or `memory-cap-mb`
3. Reduce TTL to expire entries faster
4. Increase JVM heap if needed

### Low Hit Ratio

1. Check TTL settings (may be too short)
2. Review eviction policy
3. Check for excessive invalidations
4. Consider increasing cache size

### Cluster Communication Issues

1. Check cluster status: `GET /api/cluster`
2. Verify network connectivity between nodes
3. Check firewall rules for port 9090
4. Review circuit breaker status in logs

### Performance Issues

1. Check load time metrics
2. Review GC logs
3. Profile with JProfiler or similar
4. Check for lock contention in logs

## Rollback Procedure

1. Stop new traffic (if using load balancer)
2. Stop application: `kill -15 <pid>` (graceful shutdown)
3. Restore previous version
4. Start application
5. Verify health checks
6. Resume traffic

## Backup & Recovery

### Configuration Backup

```bash
# Backup configuration
cp application-production.yml application-production.yml.backup.$(date +%Y%m%d)
```

### Cache Data

Note: Cache is in-memory only. For persistence, implement write-behind adapter (Phase 2 feature).

## Security Hardening

1. **Change Default Credentials**: Always set `CACHE_ADMIN_USERNAME` and `CACHE_ADMIN_PASSWORD`
2. **Network Security**: Restrict admin endpoints to internal network
3. **TLS**: Implement TLS for inter-node communication (future enhancement)
4. **Audit Logs**: Monitor audit logs for unauthorized access attempts
5. **Firewall**: Only allow necessary ports (8080 for HTTP, 9090 for cluster)

## Performance Tuning

### GC Tuning

For G1GC (recommended):
```bash
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:G1HeapRegionSize=16m
-XX:InitiatingHeapOccupancyPercent=45
```

### Cache Tuning

- Start with conservative TTLs and increase based on hit ratio
- Monitor eviction rates - high evictions indicate cache too small
- Balance memory usage vs hit ratio

## Support & Maintenance

### Log Locations

- Application logs: `/var/log/cache-system/cache-system.log`
- Audit logs: Same file, filtered by logger name "AUDIT"
- GC logs: Configure via JVM options

### Health Check Endpoints

- `/actuator/health` - Overall health
- `/actuator/metrics` - All metrics
- `/actuator/prometheus` - Prometheus format
- `/api/cache` - Cache status
- `/api/cluster` - Cluster status

---

**Last Updated:** November 2025
**Version:** 1.0.0

