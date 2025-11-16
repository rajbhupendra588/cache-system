# Functional Requirements (FR) and Non-Functional Requirements (NFR)
## Custom Java + Spring Distributed Cache Management System

**Document Version:** 1.0  
**Date:** November 2025  
**Project:** Distributed Cache Management System  
**Architecture:** Monolith Application (Java + Spring Boot)

---

## Executive Summary

### Problem Statement
A Java + Spring based ticket management system (Jira-like) experiences high latency when loading issues. Each issue load aggregates data from approximately 20 database tables, resulting in 4-5 second response times.

### Solution Goal
Develop a custom, in-process, distributed caching layer that runs as a standalone Spring Boot monolith to reduce issue view latency from ~4-5 seconds to **sub-500ms** for cache-hit scenarios.

### Key Constraints
- **No external dependencies:** No Redis, Docker, or Kubernetes
- **Pure Java + Spring:** Standalone monolith application
- **Distributed:** Multiple JVM instances coordinate cache state
- **In-process:** Cache runs within the application JVM

---

## High-Level Architecture

The system consists of a single Spring Boot monolith containing:

1. **Cache Core** - In-memory cache manager with sharding, eviction, TTL, serialization
2. **Cache Coordinator** - Cluster membership, discovery, and synchronization
3. **Persistence Adapter** - Optional write-through/write-behind connectors to database
4. **Integration Layer** - Spring beans/interceptors for service/DAO integration
5. **Admin UI & REST API** - Configuration, cache inspection, manual invalidation
6. **Metrics & Observability** - Micrometer metrics, JMX, structured logging
7. **Security & ACL** - Secure REST admin endpoints with authentication

**Deployment Model:** Single monolith artifact deployed on multiple application servers (VMs/bare metal). Instances discover each other and exchange cache coordination messages.

---

## Functional Requirements (FR)

### FR-1: Cache API (Programmatic Interface)

**Requirement:** Provide a typed Java service (Spring bean) for cache operations.

**Interface Specification:**
```java
public interface CacheService {
    <T> Optional<T> get(String cacheName, String key, Class<T> type);
    <T> T getOrLoad(String cacheName, String key, Supplier<T> loader, Duration ttl);
    void put(String cacheName, String key, Object value, Duration ttl);
    void putAll(String cacheName, Map<String, Object> entries, Duration ttl);
    void invalidate(String cacheName, String key);
    void invalidateAll(String cacheName);
    void prefetch(String cacheName, Collection<String> keys);
    CacheStats getStats(String cacheName);
}
```

**Sub-Requirements:**
- **FR-1.1:** `getOrLoad` must atomically prevent thundering herd on cache miss (single loader lock per key)
- **FR-1.2:** Support typed caches where `cacheName` defines the entity type (e.g., `issue`, `comments`, `customField`)

**Acceptance Criteria:**
- All methods are thread-safe and support concurrent access
- `getOrLoad` ensures only one loader execution per key during cache miss
- Type safety is maintained for all generic operations

---

### FR-2: Cache Configuration per Cache Name

**Requirement:** Support configurable cache behavior per named cache.

**Configuration Parameters:**
- **TTL:** Default and override per cache
- **Eviction Policy:** LRU, LFU, TTL-only
- **Max Entries:** Maximum number of entries per cache
- **Memory Cap:** Maximum memory usage per cache (bytes)
- **Replication Mode:** NONE, INVALIDATE, REPLICATE
- **Persistence Mode:** NONE, WRITE_THROUGH, WRITE_BACK

**Acceptance Criteria:**
- Configuration can be set via properties file, YAML, or programmatic API
- Configuration changes can be hot-reloaded (Phase 1+)
- Default values provided for all parameters

---

### FR-3: Cluster Membership & Discovery

**Requirement:** Enable automatic or manual peer discovery for distributed cache coordination.

**Discovery Mechanisms:**
- Static list of peer endpoints (IP:port)
- Multicast/UDP discovery (optional)
- TCP-based peer registration

**Features:**
- Heartbeat mechanism with configurable interval
- Failure detection with configurable threshold
- Cluster view accessible via API
- Configurable gossip/heartbeat intervals

**Acceptance Criteria:**
- Nodes can discover peers within 30 seconds of startup
- Failed nodes are detected within configured timeout (default: 2 minutes)
- Cluster status API returns accurate membership information

---

### FR-4: Cache Coherence & Invalidation Modes

**Requirement:** Support multiple cache coherence strategies for distributed scenarios.

**Invalidation Mode:**
- On write, coordinator sends invalidation message to peers
- Peers evict the specified key upon receipt
- Support synchronous (blocking) or asynchronous (best-effort) invalidation

**Replication Mode:**
- On `put`, value is serialized and replicated to N peers (configurable replication factor)
- Replication can be synchronous or asynchronous

**Local-Only Mode:**
- No network traffic
- Purely local cache operations

**Acceptance Criteria:**
- Invalidation messages propagate to all healthy peers within 500ms (async mode)
- Replication factor is respected (N copies maintained across cluster)
- Local-only mode has zero network overhead

---

### FR-5: Read/Write Behavior Options

**Requirement:** Support multiple caching patterns for different use cases.

**Cache Patterns:**
1. **Cache-Aside (Application-Controlled):**
   - Application loads from DB, then calls `put`
   - Application checks cache first, loads on miss

2. **Read-Through:**
   - `getOrLoad` with loader function
   - CacheService handles loader execution and optional DB writes

3. **Write-Through:**
   - `put` synchronously writes to DB via Persistence Adapter
   - Cache updated only after successful DB write

4. **Write-Behind:**
   - `put` buffers entries and flushes to DB asynchronously
   - Configurable flush interval and batch size
   - Provides durability guarantees

**Acceptance Criteria:**
- All patterns are supported via configuration
- Write-behind buffers are flushed on graceful shutdown
- Write-through operations are atomic (cache + DB)

---

### FR-6: Consistency & Versioning

**Requirement:** Provide mechanisms to prevent stale data and support optimistic updates.

**Features:**
- Version field or ETag support per cache entry
- Optimistic update semantics (version-based conflict detection)
- Per-key metadata:
  - `lastUpdatedTs` - Timestamp of last update
  - `originNodeId` - Node that created/updated the entry
  - `version` - Version number for conflict detection

**Acceptance Criteria:**
- Version conflicts are detected and reported
- Metadata is accessible via API
- Version increments on each update

---

### FR-7: Invalidation Triggers

**Requirement:** Support multiple mechanisms to trigger cache invalidation.

**Trigger Mechanisms:**
1. **Application-Level Hooks:**
   - AOP aspects intercepting DAO updates
   - Manual invalidation calls in service layer
   - Event-driven invalidation

2. **Database Triggers (Optional):**
   - Outbox pattern adapter
   - Change Data Capture (CDC) adapter
   - Provided as extension point for custom implementation

**Acceptance Criteria:**
- AOP integration works with Spring-managed beans
- Invalidation events are published reliably
- Extension points are well-documented

---

### FR-8: Bulk/Partial Operations

**Requirement:** Support efficient bulk operations for prefetching and batch updates.

**Operations:**
- Bulk `get` - Retrieve multiple keys in single operation
- Bulk `put` - Store multiple entries atomically
- Bulk `invalidate` - Remove multiple keys
- Range operations for composite keys (e.g., `issue:123:*`)

**Acceptance Criteria:**
- Bulk operations are more efficient than individual calls
- Range operations support wildcard patterns
- Atomicity guarantees for bulk `put` operations

---

### FR-9: Cache Warming & Prefetching

**Requirement:** Enable proactive cache population to improve hit rates.

**Features:**
- Admin API to warm caches (preload last N frequently accessed items)
- Background prefetch scheduler with configurable rules
- Prefetch strategies:
  - Time-based (e.g., every N minutes)
  - Event-based (e.g., on user login)
  - Pattern-based (e.g., prefetch related entities)

**Acceptance Criteria:**
- Warming operations don't block normal cache operations
- Prefetch scheduler respects memory and throughput limits
- Admin API provides feedback on warming progress

---

### FR-10: Serialization & Marshalling

**Requirement:** Support pluggable serialization mechanisms with version compatibility.

**Serialization Options:**
- Default: Java serialization
- Recommended: Kryo (performance) or JSON (portability)
- Support for custom serializers via extension point

**Features:**
- Backward/forward compatibility of schemas
- Type hints for version-safe deserialization
- Efficient serialized form (avoid full entity graphs)

**Acceptance Criteria:**
- Serialization is pluggable via Spring configuration
- Schema evolution is supported (old/new formats)
- Serialization errors are handled gracefully

---

### FR-11: Admin UI and REST Endpoints

**Requirement:** Provide web-based administration interface and REST API for cache management.

**Dashboard Features:**
- Cache hit/miss rates per cache name
- Cache size and memory usage
- Cluster nodes status and health
- Real-time metrics visualization

**Admin Actions:**
- Invalidate specific keys or entire caches
- Clear cache contents
- Change configuration (hot reloadable)
- View cache contents (with pagination)

**REST API Endpoints:**
```
GET  /api/cache                    - List all caches and stats
GET  /api/cache/{name}             - Get cache details
GET  /api/cache/{name}/keys        - List keys (with prefix filter)
POST /api/cache/{name}/invalidate  - Invalidate keys
POST /api/cache/{name}/clear       - Clear entire cache
POST /api/cache/{name}/config      - Update configuration
GET  /api/cluster                  - Cluster nodes and status
GET  /api/cluster/nodes/{nodeId}   - Specific node details
POST /api/cache/{name}/warm        - Warm cache with data
```

**Security:**
- Endpoint authentication required
- Role-based access control (RBAC)
- Admin role required for write operations

**Acceptance Criteria:**
- Admin UI is accessible and responsive
- All REST endpoints are documented (OpenAPI/Swagger)
- Unauthenticated requests return 401
- Configuration changes take effect immediately

---

### FR-12: Metrics & Instrumentation

**Requirement:** Expose comprehensive metrics for monitoring and observability.

**Metrics (Micrometer):**
- `customcache.requests.total{cache}` - Total cache requests
- `customcache.hits{cache}` - Cache hits
- `customcache.misses{cache}` - Cache misses
- `customcache.evictions{cache}` - Eviction count
- `customcache.load.time.ms{cache}` - Load time histogram
- `customcache.size.entries{cache}` - Current entry count
- `customcache.memory.bytes{cache}` - Memory usage
- `customcache.replication.latency.ms` - Replication latency
- `customcache.invalidation.rate` - Invalidation rate

**Additional Instrumentation:**
- JMX beans for operational teams
- Structured logging for audit trails
- Distributed tracing support (optional)

**Acceptance Criteria:**
- All metrics are exposed via Micrometer
- Metrics are tagged with cache name and node ID
- JMX beans are accessible via standard JMX clients

---

### FR-13: Logging & Audit

**Requirement:** Provide comprehensive logging and audit capabilities.

**Logging Features:**
- Configurable log levels per component
- Structured logging (JSON format option)
- Audit logs for:
  - Admin actions (invalidate, clear, config changes)
  - Invalidation events
  - Cluster membership changes
  - Security events (authentication failures)

**Acceptance Criteria:**
- Logs are structured and parseable
- Audit logs are immutable and tamper-evident
- Log levels can be changed at runtime

---

### FR-14: Monitoring & Alerting Hooks

**Requirement:** Integrate with monitoring systems and provide health checks.

**Health Endpoints:**
- `/actuator/health` - Shows cache health and cluster quorum
- `/actuator/metrics` - Exposes all metrics
- `/actuator/prometheus` - Prometheus-compatible metrics endpoint

**Alerting Integration:**
- Prometheus metrics endpoint
- Grafana dashboard templates
- Configurable alert thresholds

**Acceptance Criteria:**
- Health endpoint accurately reflects system state
- Metrics are compatible with Prometheus
- Alert rules can be configured externally

---

### FR-15: Lifecycle & Graceful Shutdown

**Requirement:** Support clean shutdown with state preservation.

**Shutdown Sequence:**
1. Stop accepting new cache operations
2. Flush write-behind buffers to persistence adapter
3. Exchange final state with peers (optional)
4. Notify peers of shutdown
5. Complete in-flight operations
6. Release resources and exit

**Acceptance Criteria:**
- Shutdown completes within 30 seconds
- No data loss for write-behind buffers
- Peers are notified of node departure

---

### FR-16: Backpressure & Protection

**Requirement:** Protect system from resource exhaustion and provide circuit breakers.

**Protection Mechanisms:**
- Memory limits with automatic eviction
- Circuit breaker: stop caching when memory threshold reached
- Alerts when protection mechanisms activate
- Configurable thresholds and policies

**Acceptance Criteria:**
- System never exceeds configured memory limits
- Circuit breaker prevents OOM errors
- Alerts are sent when protection activates

---

### FR-17: Security

**Requirement:** Provide security for inter-node communication and admin endpoints.

**Security Features:**
- TLS for inter-node communication (optional but recommended)
- Admin REST endpoints require authentication (JWT or Basic Auth)
- Role-based access control (RBAC)
- Sensitive payload redaction from logs (PII protection)

**Acceptance Criteria:**
- Unauthenticated admin requests are rejected (401)
- TLS can be enabled via configuration
- RBAC roles are enforced on all admin endpoints

---

## Non-Functional Requirements (NFR)

### NFR-1: Latency & Performance Targets

**Primary Goal:** Reduce average issue view latency from ~4-5 seconds to **<500ms** (end-to-end) for cache-hit scenarios.

**Performance Targets:**
- **Cache Get (in-memory local):** <2ms median, <5ms 99th percentile
- **Cache Miss + Loader (single-node):** <300ms if DB query optimized
- **Cache Miss + Loader (with DB):** Document baseline; target <500ms total
- **Invalidation Propagation:** <500ms for async mode, <100ms for sync mode
- **Replication Latency:** <200ms for async replication

**Measurement:**
- Metrics collected via Micrometer histograms
- SLO: 95% of issue view requests with cache-hit complete <200ms

---

### NFR-2: Throughput

**Target Throughput:**
- **Read Operations:** 5,000 concurrent operations/sec per JVM (tuneable)
- **Write Operations:** 500 operations/sec for updates (invalidation/puts)
- **Bulk Operations:** Support batches of 1,000+ keys efficiently

**Note:** These are baseline targets; actual numbers should be validated through load testing and tuned based on hardware and workload.

---

### NFR-3: Availability & Fault Tolerance

**Availability Requirements:**
- Each node must operate independently if peers are down (eventual consistency)
- System must tolerate N-1 node failures without losing ability to serve reads for keys that exist locally
- For replication mode with factor R, maintain durability when <= R-1 nodes fail
- Avoid single point of failure - cluster uses peer-to-peer model, no central coordinator

**Fault Tolerance:**
- Network partitions: local reads still possible
- Node failures: automatic detection and removal from cluster view
- Split-brain detection: prefer eventual consistency over blocking operations

**Target Availability:** 99.9% uptime (8.76 hours downtime/year)

---

### NFR-4: Consistency Model

**Default Model:** Eventual consistency
- Invalidations propagated asynchronously
- Acceptable for most use cases (issue views, comments)

**Optional Model:** Near-strong consistency
- Synchronous invalidation + confirmation
- Configurable per cache or per operation
- Higher latency but stronger guarantees

**Consistency Guarantees:**
- Read-your-writes: Guaranteed within same node
- Monotonic reads: Guaranteed within same node
- Causal consistency: Best-effort across cluster

---

### NFR-5: Memory & GC

**Memory Requirements:**
- JVM heap budget per node: Configurable (recommended: 8-32GB)
- Cache must expose memory usage and enforce memory caps
- Use object pooling and efficient serialization to minimize GC pressure

**GC Targets:**
- GC pauses: <100ms 99th percentile
- GC overhead: <5% of total CPU time
- Memory efficiency: Minimize object allocations in hot paths

**Memory Management:**
- Automatic eviction when memory cap reached
- Memory usage metrics exposed via JMX and Micrometer
- Support for off-heap storage (future enhancement)

---

### NFR-6: Scalability

**Horizontal Scaling:**
- Add more JVM instances by starting on new host (manual or automated)
- Cache partitions/shards balanced across nodes using consistent hashing
- Rebalancing should be online and incremental (avoid long pauses)

**Vertical Scaling:**
- Support increasing heap size per node
- Efficient utilization of available memory

**Scaling Limits:**
- Tested cluster size: Up to 50 nodes (initial target)
- Document maximum recommended cluster size based on testing

---

### NFR-7: Security

**Security Requirements:**
- Inter-node communication optionally encrypted (TLS)
- Admin endpoints authenticated and require ADMIN role
- Sensitive payloads (PII) can be redacted from logs
- Support for security audit logging

**Compliance:**
- Follow OWASP security best practices
- Support for security scanning and penetration testing
- Regular security updates and patches

---

### NFR-8: Observability & SLOs

**Observability Requirements:**
- Export metrics via Micrometer to Prometheus
- Structured logging with correlation IDs
- Distributed tracing support (optional, Phase 3)

**Key Metrics to Monitor:**
- Cache hit ratio (target: >70%, configurable threshold)
- Memory usage (alert if >85% of cap)
- Cluster node unreachable (alert if >2 minutes)
- Request latency percentiles (P50, P95, P99)

**SLOs:**
- **Latency SLO:** 95% of issue view requests with cache-hit complete <200ms
- **Availability SLO:** 99.9% uptime
- **Correctness SLO:** Zero data corruption incidents

**Alerting:**
- Configurable alert thresholds
- Integration with PagerDuty, Slack, email
- Runbook for common alert scenarios

---

### NFR-9: Operability & Manageability

**Operational Requirements:**
- Admin UI for runtime config changes (hot reload)
- CLI for common operations:
  - Clear cache
  - Show cluster nodes
  - Export stats
  - Invalidate keys
- Runbook for typical incidents:
  - Cache blowup (memory exhaustion)
  - Node failover
  - Network partition recovery
  - Stale data issues

**Documentation:**
- Architecture diagrams
- API documentation (OpenAPI/Swagger)
- Deployment guides
- Troubleshooting guides
- Performance tuning guides

---

### NFR-10: Testability

**Testing Requirements:**
- **Unit Tests:** Core cache logic, concurrency, eviction policies
- **Integration Tests:** Cluster invalidation, replication, persistence
- **Performance Tests:** Load testing with realistic data shapes
- **Multi-JVM Tests:** Integration test harness for cluster scenarios

**Test Coverage:**
- Code coverage: >80% for core components
- Critical path coverage: 100%
- Concurrency test scenarios: Race conditions, deadlocks, thundering herd

**Performance Benchmarks:**
- Realistic data shape (issue + 20 tables)
- Baseline measurements before/after caching
- Regression testing for performance regressions

---

### NFR-11: Maintainability & Extensibility

**Code Quality:**
- Well-documented public Java API
- Extension points clearly defined:
  - `Serializer` interface
  - `PersistenceAdapter` interface
  - `DiscoveryAdapter` interface
  - `ReplicationStrategy` interface
- Minimal external dependencies
- Adhere to Spring Boot idioms and best practices

**Documentation:**
- JavaDoc for all public APIs
- Architecture decision records (ADRs)
- Extension point documentation with examples

---

### NFR-12: Resource Constraints

**Constraints:**
- **No external systems:** No Redis, no Docker, no Kubernetes
- **Platform:** Runs on Linux/Windows JVMs (Java 11+)
- **Dependencies:** Minimal - Spring Boot, Micrometer, serialization library
- **Deployment:** Single JAR file, standalone execution

**Resource Requirements:**
- Minimum: 4GB heap, 2 CPU cores
- Recommended: 8-32GB heap, 4+ CPU cores
- Disk: Minimal (logs, optional persistence)

---

## Acceptance Criteria / Test Cases

### TC-1: Basic Cache Put/Get
**Test:** Put an Issue object into `issue` cache, then get returns same object.
**Verification:** 
- Object equality matches
- Hit/miss metrics updated correctly
- Type safety maintained

### TC-2: Thundering Herd Prevention
**Test:** Concurrent 200 requests for same missing key trigger single loader call.
**Verification:**
- Only one DB query executed
- All 200 requests receive same result
- Loader lock prevents duplicate executions

### TC-3: Invalidation Propagation
**Test:** Node A updates issue → Node B evicts entry within configured propagation interval (<500ms).
**Verification:**
- Invalidation message received by Node B
- Entry removed from Node B cache
- Metrics show invalidation event

### TC-4: Replication
**Test:** Put with replication factor 2 distributes value to another node; on node failure, replicated data still available.
**Verification:**
- Value exists on 2 nodes
- After node failure, value still accessible from remaining node
- Replication metrics show successful replication

### TC-5: Eviction
**Test:** When cache memory cap exceeded, eviction policy LRU removes oldest used entries.
**Verification:**
- Memory cap enforced
- Oldest entries evicted first
- Metrics show eviction count
- New entries can be added after eviction

### TC-6: Write-Behind Durability
**Test:** Write-behind buffers flush to persistence adapter within configured interval; on restart, buffered entries are retried.
**Verification:**
- Buffers flush within interval
- On restart, unflushed entries are retried
- No data loss for buffered writes

### TC-7: Graceful Shutdown
**Test:** On SIGTERM, node flushes write-behind, sends shutdown notification, and exits cleanly.
**Verification:**
- Write-behind buffers flushed
- Peers notified of shutdown
- No errors in shutdown sequence
- Shutdown completes within 30 seconds

### TC-8: Admin Operations
**Test:** Admin UI can clear cache, change TTL, and actions reflect in metrics immediately.
**Verification:**
- Cache cleared successfully
- TTL change takes effect immediately
- Metrics updated in real-time
- Configuration persisted

### TC-9: Security
**Test:** Admin endpoints protected - unauthenticated requests return 401.
**Verification:**
- Unauthenticated requests rejected
- Authenticated requests succeed
- RBAC roles enforced correctly

---

## Key Implementation Details & Patterns

### 1. Key Design
- **Deterministic Keys:** `issue:{issueId}`, `issue:123:comments`, `user:{id}`
- **Composite Keys:** Enable fine-grained invalidation (e.g., `issue:123:*` invalidates all related data)
- **Key Naming Convention:** Documented and consistent across application

### 2. Consistent Hashing & Partitioning
- Map keys to virtual nodes using consistent hashing
- Each physical node hosts a set of virtual shards
- Rebalancing when nodes join/leave cluster

### 3. Invalidation Flow (Recommended)
```
On DB Write (in app transaction)
  ↓
Publish invalidation event to local CacheService (AOP or manual)
  ↓
CacheService:
  1. Invalidate local entry immediately
  2. Send invalidation message to peers asynchronously
  3. Peers receive invalidation and evict
```

### 4. Loader Lock / Singleflight
- Per-key request coalescing
- First request creates async loader future
- Other requests wait for completion
- Prevents duplicate DB queries

### 5. Serialization
- Default: Kryo or Jackson with type hints
- Version-safe serialization for schema evolution
- Keep serialized form small (avoid full entity graphs)

### 6. Replication vs Invalidation Tradeoffs

**Invalidation:**
- Low network bandwidth
- Simpler implementation
- Eventual consistency
- Good for high update frequency

**Replication:**
- Faster reads (local hits)
- Higher network and memory cost
- Better for mostly-read datasets

**Recommendation:** For issue pages (lots of reads, infrequent writes), consider:
- Replication for core read datasets (issue header, last comments)
- Invalidation for heavy write sub-objects

### 7. Hot-Warm Strategy
- **Hot Data:** Replicated across nodes (most frequently accessed issues)
- **Warm Data:** Local-only or longer TTL
- **Cold Data:** Not cached or very long TTL

---

## Admin UI / REST API Specification

### REST Endpoints

#### Cache Management
```
GET    /api/cache                           - List all caches and stats
GET    /api/cache/{name}                    - Get cache details and configuration
GET    /api/cache/{name}/keys               - List keys (query params: prefix, limit, offset)
POST   /api/cache/{name}/invalidate        - Invalidate keys (body: { "keys": ["key1", "key2"] })
POST   /api/cache/{name}/clear             - Clear entire cache
POST   /api/cache/{name}/config            - Update configuration (body: config object)
POST   /api/cache/{name}/warm              - Warm cache (body: { "keys": [...], "loader": "..." })
GET    /api/cache/{name}/stats             - Get cache statistics
```

#### Cluster Management
```
GET    /api/cluster                         - List all cluster nodes and status
GET    /api/cluster/nodes                   - List nodes with details
GET    /api/cluster/nodes/{nodeId}          - Get specific node details
POST   /api/cluster/nodes/{nodeId}/remove   - Remove node from cluster (admin only)
```

#### Metrics & Health
```
GET    /actuator/health                     - Health check (cache health, cluster quorum)
GET    /actuator/metrics                    - All metrics
GET    /actuator/prometheus                 - Prometheus-compatible metrics
GET    /actuator/info                       - Application info
```

### Request/Response Examples

**Invalidate Keys:**
```json
POST /api/cache/issue/invalidate
{
  "keys": ["issue:123", "issue:456"]
}
```

**Update Configuration:**
```json
POST /api/cache/issue/config
{
  "ttl": "PT1H",
  "evictionPolicy": "LRU",
  "maxEntries": 10000,
  "replicationMode": "INVALIDATE"
}
```

**Warm Cache:**
```json
POST /api/cache/issue/warm
{
  "keys": ["issue:123", "issue:456"],
  "loader": "issueLoader"
}
```

---

## Metrics Specification (Micrometer)

### Metric Names
- `customcache.requests.total{cache,operation}` - Total requests
- `customcache.hits{cache}` - Cache hits
- `customcache.misses{cache}` - Cache misses
- `customcache.evictions{cache,reason}` - Eviction count by reason
- `customcache.load.time.ms{cache}` - Load time histogram
- `customcache.size.entries{cache}` - Current entry count
- `customcache.memory.bytes{cache}` - Memory usage in bytes
- `customcache.replication.latency.ms{cache}` - Replication latency
- `customcache.invalidation.rate{cache}` - Invalidation rate
- `customcache.cluster.nodes` - Number of cluster nodes
- `customcache.cluster.heartbeat.latency.ms` - Heartbeat latency

### Tags
- `cache` - Cache name
- `operation` - Operation type (get, put, invalidate)
- `reason` - Eviction reason (size, ttl, memory)
- `node` - Node ID

---

## Roadmap / Phased Implementation

### Phase 0: MVP (4 weeks)
**Goal:** Basic local cache with integration to ticket app

**Deliverables:**
- Local in-process cache with LRU + TTL
- CacheService API implementation
- Per-key loader with singleflight (thundering herd prevention)
- Integration with ticket app for issue cache only
- Basic metrics: hits/misses
- Basic admin REST to invalidate and clear

**Success Criteria:**
- Issue view latency reduced to <1s for cache-hit scenarios
- Zero thundering herd issues
- Metrics visible via actuator

---

### Phase 1: Cluster & Invalidation (4-8 weeks)
**Goal:** Distributed cache with invalidation

**Deliverables:**
- Cluster membership and discovery (static list or multicast)
- Simple invalidation propagation (UDP/TCP)
- Heartbeat mechanism and failure detection
- Bulk prefetch API + loader optimization
- Admin UI basic dashboard
- Enhanced metrics and monitoring

**Success Criteria:**
- Invalidations propagate within 500ms
- Cluster membership accurate
- Admin UI functional

---

### Phase 2: Replication & Persistence (8-12 weeks)
**Goal:** Advanced features for production readiness

**Deliverables:**
- Replication mode with configurable factor
- Consistent hashing and rebalancing
- Write-behind adapter to DB + persistence hooks
- Security: TLS for node comms, RBAC for admin UI
- Enhanced admin UI with advanced features
- Comprehensive documentation

**Success Criteria:**
- Replication maintains N copies across cluster
- Write-behind buffers flush reliably
- Security features tested and documented

---

### Phase 3: Optimization & Production Hardening (12-20 weeks)
**Goal:** Performance optimization and production readiness

**Deliverables:**
- Advanced eviction policies (LFU)
- Serialization tuning (Kryo optimization)
- GC tuning and performance optimization
- Integration test harness (multi-JVM)
- Load testing and performance benchmarks
- Production runbook and operational guides
- Advanced monitoring and alerting

**Success Criteria:**
- Latency targets met (<500ms for issue view)
- Throughput targets met (5K reads/sec)
- Production-ready with full documentation

---

## Deployment & Rollout Strategy

### Stage 1: Single Node (Week 1-2)
- Deploy cache in local-only mode on single node
- Validate latency improvements for issue view
- Monitor metrics and memory usage
- Baseline performance measurements

### Stage 2: Two-Node Cluster (Week 3-4)
- Enable cluster invalidation with 2 nodes in QA environment
- Validate invalidation propagation
- Test failure scenarios (node shutdown)
- Measure network overhead

### Stage 3: Production Rollout (Week 5-8)
- Gradual rollout to production nodes
- Start with read-heavy workloads
- Monitor hit ratios and latency
- Validate no stale reads for critical updates

### Stage 4: Scale & Optimize (Week 9+)
- Gradually increase node count
- Enable replication for hot caches
- Tune configuration based on metrics
- Optimize based on production patterns

---

## Risks & Mitigations

### Risk 1: Stale Data / Inconsistency
**Impact:** High - Users see outdated information  
**Mitigation:**
- Use short TTLs for critical data
- Synchronous invalidation for critical operations
- Versioning support for conflict detection
- Monitoring and alerting for stale data patterns

### Risk 2: Memory Pressure / OOM
**Impact:** High - Application crashes  
**Mitigation:**
- Enforce strict memory caps and eviction
- Monitor GC and memory usage
- Circuit breaker stops caching when threshold reached
- Use off-heap storage if needed (future)
- Regular heap dump analysis

### Risk 3: Network Partition
**Impact:** Medium - Inconsistency between partitions  
**Mitigation:**
- Ensure local reads still possible
- Detect split-brain scenarios
- Prefer eventual consistency over blocking
- Document partition behavior clearly

### Risk 4: DB as Bottleneck
**Impact:** Medium - Cache misses still hit slow DB  
**Mitigation:**
- Profile and optimize DB queries
- Consider query-level caching for heavy joins
- Prefetch strategies to reduce misses
- Monitor cache hit ratios and optimize

### Risk 5: Complexity & Maintenance
**Impact:** Medium - Increased maintenance burden  
**Mitigation:**
- Strong test coverage (>80%)
- Comprehensive documentation
- Small incremental feature sets
- Clear extension points for customization
- Regular code reviews

### Risk 6: Performance Regression
**Impact:** Medium - System slower than baseline  
**Mitigation:**
- Performance benchmarks in CI/CD
- Load testing before releases
- Performance regression detection
- Regular performance reviews

---

## Operational Runbook

### Incident: Hit Ratio Drops Suddenly
**Symptoms:** Cache hit ratio < 70%, increased latency  
**Investigation:**
1. Check `cache.hits / cache.requests` metrics
2. Check eviction spikes (`cache.evictions`)
3. Check memory levels (`cache.memory.bytes`)
4. Review recent configuration changes
5. Check cluster status (node failures?)

**Resolution:**
- If evictions high: Increase memory cap or reduce cache size
- If config changed: Revert or adjust
- If node failed: Restart failed node
- If memory leak: Analyze heap dump

---

### Incident: Invalidations Not Propagating
**Symptoms:** Stale data across nodes, invalidation metrics show failures  
**Investigation:**
1. Check cluster status (`/api/cluster`)
2. Check heartbeats (`cluster.heartbeat.latency.ms`)
3. Check network ACLs and firewall rules
4. Review invalidation logs
5. Test network connectivity between nodes

**Resolution:**
- If node unreachable: Restart node or fix network
- If network issue: Fix firewall/ACL rules
- If message queue full: Increase buffer size
- If bug: Check invalidation code and fix

---

### Incident: Out of Memory (OOM)
**Symptoms:** JVM OOM errors, application crashes  
**Investigation:**
1. Analyze heap dump
2. Check memory usage metrics
3. Review cache size and eviction settings
4. Check for memory leaks

**Resolution:**
- Immediate: Restart node with increased heap
- Short-term: Reduce cache size, increase eviction aggressiveness
- Long-term: Optimize serialization, use off-heap, add more nodes

---

### Incident: High Latency Despite Cache Hits
**Symptoms:** Cache hits but still slow response times  
**Investigation:**
1. Check `cache.load.time.ms` - is loader slow?
2. Check GC pauses
3. Check thread pool saturation
4. Profile application code

**Resolution:**
- If loader slow: Optimize DB queries
- If GC issue: Tune GC settings, reduce allocations
- If thread pool: Increase pool size or optimize locking
- If code issue: Profile and optimize hot paths

---

## Example: Issue Read Flow

### Sequence Diagram
```
Web UI Request
    ↓
Service Layer: CacheService.getOrLoad("issue", "issue:123", loader)
    ↓
[Cache Hit?]
    ├─ YES → Return cached DTO (<2ms)
    └─ NO  → Execute loader (singleflight lock)
            ↓
        Loader: Aggregate data from ~20 DB tables
            ↓
        Create DTO
            ↓
        CacheService.put("issue", "issue:123", dto, ttl)
            ↓
        [Replication Mode?]
            ├─ INVALIDATE → Send invalidation to peers
            └─ REPLICATE  → Send value to N peers
            ↓
        Return DTO to Service Layer
            ↓
        Return Response to UI
```

### Performance Targets
- **Cache Hit:** <2ms (local memory access)
- **Cache Miss + DB Load:** <300ms (optimized query)
- **Total End-to-End (Cache Hit):** <500ms
- **Total End-to-End (Cache Miss):** <800ms

---

## Conclusion

This document provides a comprehensive specification for a custom Java + Spring distributed cache management system. The system is designed to reduce issue view latency from 4-5 seconds to sub-500ms while maintaining simplicity (no external dependencies) and operational flexibility.

The phased implementation approach allows for incremental delivery and validation, reducing risk and enabling early value delivery. The system is designed with extensibility in mind, allowing for future enhancements while maintaining a clean, maintainable codebase.

**Next Steps:**
1. Review and approve this specification
2. Create detailed technical design documents
3. Set up project structure and initial codebase
4. Begin Phase 0 (MVP) implementation
5. Establish CI/CD pipeline and testing framework

---

**Document End**

