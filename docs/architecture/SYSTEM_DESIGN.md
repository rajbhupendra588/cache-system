# High-Level System Design

**Version:** 1.0.0  
**Last Updated:** November 2025

> 📊 **Viewing Diagrams:** The diagrams in this document use Mermaid syntax. To see the actual visual diagrams:
> - **🌐 HTML Viewer (Easiest):** Open [`view-diagrams.html`](view-diagrams.html) in your browser - all diagrams rendered instantly!
> - **GitHub/GitLab:** Push to repository and view on web (diagrams render automatically)
> - **VS Code:** Install "Markdown Preview Enhanced" extension
> - **Online:** Copy diagram code to https://mermaid.live/
> - **See:** [`VIEW_DIAGRAMS.md`](VIEW_DIAGRAMS.md) for detailed viewing instructions

---

## 📐 Table of Contents

1. [System Overview](#system-overview)
2. [High-Level Architecture](#high-level-architecture)
3. [Component Architecture](#component-architecture)
4. [Cache Flow Diagrams](#cache-flow-diagrams)
5. [Cluster Communication](#cluster-communication)
6. [Deployment Architecture](#deployment-architecture)
7. [Data Flow Diagrams](#data-flow-diagrams)
8. [Sequence Diagrams](#sequence-diagrams)

---

## System Overview

The Distributed Cache Management System is a **Spring Boot monolith** that provides high-performance, in-memory caching with distributed coordination across multiple JVM instances. It's designed to reduce latency in ticket management systems by caching frequently accessed data.

### Key Characteristics

- **Architecture:** Monolith (single JAR deployed on multiple nodes)
- **Caching Strategy:** In-memory with TTL, LRU/LFU eviction
- **Distribution:** Peer-to-peer cluster coordination
- **Communication:** TCP sockets for inter-node messaging
- **Serialization:** Kryo for efficient data transfer

---

## High-Level Architecture

### System Architecture Diagram

```mermaid
graph TB
    subgraph "Client Applications"
        APP1[Application 1<br/>JVM Instance]
        APP2[Application 2<br/>JVM Instance]
        APP3[Application N<br/>JVM Instance]
    end
    
    subgraph "Cache System Node 1"
        API1[REST API<br/>Port 8080]
        CORE1[Cache Core<br/>CacheService]
        CLUSTER1[Cluster Coordinator]
        MEM1[In-Memory Cache<br/>ConcurrentHashMap]
    end
    
    subgraph "Cache System Node 2"
        API2[REST API<br/>Port 8081]
        CORE2[Cache Core<br/>CacheService]
        CLUSTER2[Cluster Coordinator]
        MEM2[In-Memory Cache<br/>ConcurrentHashMap]
    end
    
    subgraph "Cache System Node N"
        APIN[REST API<br/>Port 808N]
        COREN[Cache Core<br/>CacheService]
        CLUSTERN[Cluster Coordinator]
        MEMN[In-Memory Cache<br/>ConcurrentHashMap]
    end
    
    subgraph "External Systems"
        DB[(Database)]
        METRICS[Prometheus<br/>Metrics]
    end
    
    APP1 -->|HTTP/REST| API1
    APP2 -->|HTTP/REST| API2
    APP3 -->|HTTP/REST| APIN
    
    API1 --> CORE1
    API2 --> CORE2
    APIN --> COREN
    
    CORE1 --> MEM1
    CORE2 --> MEM2
    COREN --> MEMN
    
    CORE1 -->|Load on Miss| DB
    CORE2 -->|Load on Miss| DB
    COREN -->|Load on Miss| DB
    
    CLUSTER1 <-->|TCP Port 9090| CLUSTER2
    CLUSTER2 <-->|TCP Port 9091| CLUSTERN
    CLUSTER1 <-->|TCP Port 909N| CLUSTERN
    
    CORE1 --> CLUSTER1
    CORE2 --> CLUSTER2
    COREN --> CLUSTERN
    
    CORE1 --> METRICS
    CORE2 --> METRICS
    COREN --> METRICS
```

### Architecture Layers

```mermaid
graph TD
    subgraph "Presentation Layer"
        REST[REST API Controllers<br/>CacheController, ClusterController]
        UI[Web Dashboard<br/>HTML/CSS/JS]
    end
    
    subgraph "Service Layer"
        CS[CacheService<br/>Business Logic]
        CC[ClusterCoordinator<br/>Cluster Management]
    end
    
    subgraph "Core Layer"
        CM[CacheManager<br/>In-Memory Storage]
        MS[MessageSender<br/>Network Communication]
        MR[MessageReceiver<br/>Network Communication]
        CMEM[ClusterMembership<br/>Peer Discovery]
    end
    
    subgraph "Infrastructure Layer"
        METRICS[Metrics<br/>Micrometer]
        HEALTH[Health Checks<br/>Actuator]
        SEC[Security<br/>Spring Security]
        CONFIG[Configuration<br/>YAML Properties]
    end
    
    REST --> CS
    UI --> REST
    CS --> CM
    CS --> CC
    CC --> MS
    CC --> MR
    CC --> CMEM
    CS --> METRICS
    CS --> HEALTH
    REST --> SEC
    CS --> CONFIG
```

---

## Component Architecture

### Detailed Component Diagram

```mermaid
graph LR
    subgraph "API Layer"
        CC[CacheController<br/>REST Endpoints]
        CLUSTERC[ClusterController<br/>Cluster Status]
        WC[WebController<br/>Dashboard]
    end
    
    subgraph "Service Layer"
        CS[CacheServiceImpl<br/>Business Logic]
        ICM[InMemoryCacheManager<br/>Storage Engine]
    end
    
    subgraph "Cluster Layer"
        COORD[ClusterCoordinator<br/>Orchestration]
        MEMBERSHIP[ClusterMembership<br/>Peer Management]
        MSENDER[MessageSender<br/>Outbound Messages]
        MRECEIVER[MessageReceiver<br/>Inbound Messages]
    end
    
    subgraph "Core Data Structures"
        CACHE[ConcurrentHashMap<br/>Cache Storage]
        LOCKS[ReentrantLock<br/>Per-Key Locks]
        FUTURES[CompletableFuture<br/>Async Loading]
    end
    
    subgraph "Supporting Components"
        METRICS[CacheMetrics<br/>Observability]
        SERIAL[SerializationUtil<br/>Kryo Serialization]
        HEALTH[CacheHealthIndicator<br/>Health Checks]
    end
    
    CC --> CS
    CLUSTERC --> COORD
    WC --> CS
    CS --> ICM
    CS --> COORD
    CS --> METRICS
    ICM --> CACHE
    ICM --> LOCKS
    ICM --> FUTURES
    COORD --> MEMBERSHIP
    COORD --> MSENDER
    MRECEIVER --> COORD
    MSENDER --> SERIAL
    MRECEIVER --> SERIAL
    CS --> HEALTH
```

### Component Responsibilities

```mermaid
mindmap
  root((Distributed Cache System))
    API Layer
      REST Controllers
        Cache Operations
        Cluster Status
        Web Dashboard
    Service Layer
      CacheService
        getOrLoad
        put/invalidate
        Thundering Herd Prevention
      CacheManager
        In-Memory Storage
        Eviction Policies
        TTL Management
    Cluster Layer
      ClusterCoordinator
        Message Routing
        Replication Logic
      MessageSender/Receiver
        TCP Communication
        Serialization
      ClusterMembership
        Peer Discovery
        Heartbeat Monitoring
    Infrastructure
      Metrics
        Micrometer Integration
        Prometheus Export
      Security
        Basic Auth
        Input Validation
      Configuration
        YAML Properties
        Environment Variables
```

---

## Cache Flow Diagrams

### Cache Hit Flow

```mermaid
sequenceDiagram
    participant Client
    participant CacheService
    participant CacheManager
    participant CacheStorage
    
    Client->>CacheService: getOrLoad("issue", "issue:123", loader)
    CacheService->>CacheManager: get("issue", "issue:123")
    CacheManager->>CacheStorage: lookup("issue:123")
    CacheStorage-->>CacheManager: CacheEntry (found)
    CacheManager->>CacheManager: Check TTL (valid)
    CacheManager-->>CacheService: Optional<Issue> (present)
    CacheService->>CacheService: Record Hit Metric
    CacheService-->>Client: Issue (from cache, <2ms)
    
    Note over CacheService,CacheStorage: Cache Hit - No Database Query
```

### Cache Miss Flow (with Thundering Herd Prevention)

```mermaid
sequenceDiagram
    participant Client1
    participant Client2
    participant Client3
    participant CacheService
    participant LockManager
    participant CacheManager
    participant Database
    
    par Concurrent Requests
        Client1->>CacheService: getOrLoad("issue", "issue:123", loader)
        Client2->>CacheService: getOrLoad("issue", "issue:123", loader)
        Client3->>CacheService: getOrLoad("issue", "issue:123", loader)
    end
    
    CacheService->>CacheManager: get("issue", "issue:123")
    CacheManager-->>CacheService: Optional.empty() (miss)
    
    CacheService->>LockManager: acquireLock("issue:123")
    LockManager-->>CacheService: Lock acquired (Client1)
    LockManager-->>CacheService: Wait (Client2)
    LockManager-->>CacheService: Wait (Client3)
    
    CacheService->>CacheService: Check if loaded by another thread
    CacheService->>Database: loader.get() - Load from DB
    Database-->>CacheService: Issue data (~300ms)
    
    CacheService->>CacheManager: put("issue", "issue:123", issue)
    CacheService->>LockManager: releaseLock("issue:123")
    
    LockManager-->>CacheService: Lock released
    CacheService->>CacheService: Notify waiting threads
    CacheService-->>Client1: Issue (from DB, ~300ms)
    CacheService-->>Client2: Issue (from cache, <2ms)
    CacheService-->>Client3: Issue (from cache, <2ms)
    
    Note over Client1,Client3: Only ONE database query executed
```

### Cache Invalidation Flow

```mermaid
sequenceDiagram
    participant Client
    participant CacheService
    participant CacheManager
    participant ClusterCoordinator
    participant MessageSender
    participant PeerNode1
    participant PeerNode2
    
    Client->>CacheService: invalidate("issue", "issue:123")
    CacheService->>CacheManager: invalidate("issue", "issue:123")
    CacheManager->>CacheManager: Remove from local cache
    CacheService->>CacheService: Record invalidation metric
    
    CacheService->>ClusterCoordinator: Should replicate?
    ClusterCoordinator-->>CacheService: Yes (INVALIDATE mode)
    
    CacheService->>ClusterCoordinator: Get active peers
    ClusterCoordinator-->>CacheService: [peer1, peer2]
    
    par Async Invalidation
        CacheService->>MessageSender: sendInvalidation(peer1, message)
        MessageSender->>PeerNode1: TCP InvalidationMessage
        PeerNode1-->>MessageSender: ACK
        MessageSender-->>CacheService: Success
    and
        CacheService->>MessageSender: sendInvalidation(peer2, message)
        MessageSender->>PeerNode2: TCP InvalidationMessage
        PeerNode2-->>MessageSender: ACK
        MessageSender-->>CacheService: Success
    end
    
    CacheService-->>Client: Invalidation complete
```

---

## Cluster Communication

### Cluster Topology

```mermaid
graph TB
    subgraph "Cluster Network"
        NODE1[Node 1<br/>localhost:8080<br/>Comm: 9090]
        NODE2[Node 2<br/>localhost:8081<br/>Comm: 9091]
        NODE3[Node 3<br/>localhost:8082<br/>Comm: 9092]
        NODE4[Node 4<br/>localhost:8083<br/>Comm: 9093]
        NODE5[Node 5<br/>localhost:8084<br/>Comm: 9094]
    end
    
    NODE1 <-->|TCP 9090↔9091| NODE2
    NODE2 <-->|TCP 9091↔9092| NODE3
    NODE3 <-->|TCP 9092↔9093| NODE4
    NODE4 <-->|TCP 9093↔9094| NODE5
    NODE1 <-->|TCP 9090↔9092| NODE3
    NODE1 <-->|TCP 9090↔9093| NODE4
    NODE2 <-->|TCP 9091↔9094| NODE5
    
    style NODE1 fill:#e1f5ff
    style NODE2 fill:#e1f5ff
    style NODE3 fill:#e1f5ff
    style NODE4 fill:#e1f5ff
    style NODE5 fill:#e1f5ff
```

### Cluster Discovery and Membership

```mermaid
stateDiagram-v2
    [*] --> Starting: Application Start
    Starting --> Discovering: Load Configuration
    Discovering --> StaticDiscovery: Type: static
    Discovering --> MulticastDiscovery: Type: multicast
    
    StaticDiscovery --> Connecting: Connect to Peers
    MulticastDiscovery --> Connecting: Discover Peers
    
    Connecting --> Connected: All Peers Connected
    Connected --> Heartbeat: Start Heartbeat
    
    Heartbeat --> Heartbeat: Send Heartbeat (5s)
    Heartbeat --> PeerDown: Timeout (15s)
    Heartbeat --> PeerUp: Peer Recovered
    
    PeerDown --> Heartbeat: Retry Connection
    PeerUp --> Heartbeat: Resume Heartbeat
    
    Heartbeat --> [*]: Shutdown
```

### Message Flow in Cluster

```mermaid
graph LR
    subgraph "Node A (Writer)"
        CS1[CacheService]
        CC1[ClusterCoordinator]
        MS1[MessageSender]
    end
    
    subgraph "Network"
        TCP[TCP Socket<br/>Port 9090]
    end
    
    subgraph "Node B (Receiver)"
        MR2[MessageReceiver]
        CC2[ClusterCoordinator]
        CM2[CacheManager]
    end
    
    subgraph "Node C (Receiver)"
        MR3[MessageReceiver]
        CC3[ClusterCoordinator]
        CM3[CacheManager]
    end
    
    CS1 -->|1. invalidate| CC1
    CC1 -->|2. Get Peers| CC1
    CC1 -->|3. Create Message| MS1
    
    MS1 -->|4. Serialize| TCP
    TCP -->|5. Send| MR2
    TCP -->|5. Send| MR3
    
    MR2 -->|6. Deserialize| CC2
    MR3 -->|6. Deserialize| CC3
    
    CC2 -->|7. Process| CM2
    CC3 -->|7. Process| CM3
    
    CM2 -->|8. Invalidate| CM2
    CM3 -->|8. Invalidate| CM3
```

---

## Deployment Architecture

### Single Node Deployment

```mermaid
graph TB
    subgraph "Application Server"
        JVM[JVM Process]
        APP[Spring Boot App<br/>Port 8080]
        CACHE[In-Memory Cache<br/>No Cluster]
    end
    
    subgraph "Client Applications"
        CLIENT1[Service 1]
        CLIENT2[Service 2]
    end
    
    subgraph "External"
        DB[(Database)]
        METRICS[Prometheus]
    end
    
    CLIENT1 -->|HTTP| APP
    CLIENT2 -->|HTTP| APP
    APP --> CACHE
    APP -->|Load on Miss| DB
    APP -->|Metrics| METRICS
    
    style APP fill:#90EE90
    style CACHE fill:#FFE4B5
```

### Multi-Node Cluster Deployment

```mermaid
graph TB
    subgraph "Server 1"
        JVM1[JVM 1]
        APP1[Cache App<br/>:8080]
        CACHE1[Local Cache]
        COMM1[Comm :9090]
    end
    
    subgraph "Server 2"
        JVM2[JVM 2]
        APP2[Cache App<br/>:8081]
        CACHE2[Local Cache]
        COMM2[Comm :9091]
    end
    
    subgraph "Server 3"
        JVM3[JVM 3]
        APP3[Cache App<br/>:8082]
        CACHE3[Local Cache]
        COMM3[Comm :9092]
    end
    
    subgraph "Load Balancer"
        LB[HAProxy/Nginx<br/>:80]
    end
    
    subgraph "Clients"
        CLIENT[Client Apps]
    end
    
    subgraph "External"
        DB[(Database)]
        PROM[Prometheus]
    end
    
    CLIENT --> LB
    LB --> APP1
    LB --> APP2
    LB --> APP3
    
    APP1 --> CACHE1
    APP2 --> CACHE2
    APP3 --> CACHE3
    
    COMM1 <-->|TCP| COMM2
    COMM2 <-->|TCP| COMM3
    COMM1 <-->|TCP| COMM3
    
    APP1 --> DB
    APP2 --> DB
    APP3 --> DB
    
    APP1 --> PROM
    APP2 --> PROM
    APP3 --> PROM
    
    style APP1 fill:#90EE90
    style APP2 fill:#90EE90
    style APP3 fill:#90EE90
    style LB fill:#87CEEB
```

### Network Architecture

```mermaid
graph TB
    subgraph "Client Network"
        CLIENT1[Client 1]
        CLIENT2[Client 2]
    end
    
    subgraph "Application Network"
        subgraph "Node 1"
            HTTP1[HTTP :8080]
            COMM1[Comm :9090]
        end
        subgraph "Node 2"
            HTTP2[HTTP :8081]
            COMM2[Comm :9091]
        end
        subgraph "Node 3"
            HTTP3[HTTP :8082]
            COMM3[Comm :9092]
        end
    end
    
    subgraph "Management Network"
        METRICS[Prometheus :9090]
        GRAFANA[Grafana :3000]
    end
    
    CLIENT1 --> HTTP1
    CLIENT1 --> HTTP2
    CLIENT2 --> HTTP3
    
    COMM1 <--> COMM2
    COMM2 <--> COMM3
    COMM1 <--> COMM3
    
    HTTP1 --> METRICS
    HTTP2 --> METRICS
    HTTP3 --> METRICS
    METRICS --> GRAFANA
    
    style HTTP1 fill:#FFE4B5
    style HTTP2 fill:#FFE4B5
    style HTTP3 fill:#FFE4B5
    style COMM1 fill:#E6E6FA
    style COMM2 fill:#E6E6FA
    style COMM3 fill:#E6E6FA
```

---

## Data Flow Diagrams

### Complete Request Flow

```mermaid
flowchart TD
    START([Client Request]) --> CHECK{Request Type}
    
    CHECK -->|GET| GET_FLOW[GET Flow]
    CHECK -->|PUT| PUT_FLOW[PUT Flow]
    CHECK -->|INVALIDATE| INVALIDATE_FLOW[INVALIDATE Flow]
    
    GET_FLOW --> CACHE_CHECK{In Cache?}
    CACHE_CHECK -->|Yes| TTL_CHECK{TTL Valid?}
    TTL_CHECK -->|Yes| RETURN_HIT[Return Cached Value<br/><2ms]
    TTL_CHECK -->|No| EVICT[Evict Entry]
    CACHE_CHECK -->|No| LOAD[Load from Database]
    
    LOAD --> LOCK{Lock Acquired?}
    LOCK -->|Yes| DB_QUERY[Query Database<br/>~300ms]
    LOCK -->|No| WAIT[Wait for Loader]
    WAIT --> RETURN_MISS[Return Loaded Value]
    
    DB_QUERY --> CACHE_STORE[Store in Cache]
    CACHE_STORE --> RETURN_MISS
    EVICT --> LOAD
    
    PUT_FLOW --> VALIDATE[Validate Input]
    VALIDATE --> STORE[Store in Cache]
    STORE --> REPLICATE{Replication Mode?}
    REPLICATE -->|INVALIDATE| SEND_INVALIDATE[Send Invalidation to Peers]
    REPLICATE -->|REPLICATE| SEND_REPLICATE[Send Replication to Peers]
    REPLICATE -->|NONE| SKIP[Skip Replication]
    SEND_INVALIDATE --> RETURN_SUCCESS[Return Success]
    SEND_REPLICATE --> RETURN_SUCCESS
    SKIP --> RETURN_SUCCESS
    
    INVALIDATE_FLOW --> REMOVE_LOCAL[Remove from Local Cache]
    REMOVE_LOCAL --> SEND_INVALIDATE
    
    RETURN_HIT --> METRICS[Record Metrics]
    RETURN_MISS --> METRICS
    RETURN_SUCCESS --> METRICS
    METRICS --> END([Response])
```

### Cache Storage Structure

```mermaid
classDiagram
    class CacheService {
        +getOrLoad()
        +put()
        +invalidate()
        +getStats()
    }
    
    class InMemoryCacheManager {
        -Map~String, Cache~ caches
        -Map~String, ReentrantLock~ locks
        -Map~String, CompletableFuture~ loaders
        +get()
        +put()
        +invalidate()
        +evict()
    }
    
    class Cache {
        -String name
        -CacheConfiguration config
        -ConcurrentHashMap~String, CacheEntry~ entries
        -long hits
        -long misses
        +get()
        +put()
        +invalidate()
        +evictLRU()
        +evictLFU()
    }
    
    class CacheEntry {
        -Object value
        -Instant createdAt
        -Instant expiresAt
        -long accessCount
        -Instant lastAccessed
        +isExpired()
        +getValue()
    }
    
    class CacheConfiguration {
        +Duration ttl
        +EvictionPolicy policy
        +int maxEntries
        +long memoryCap
        +ReplicationMode replicationMode
    }
    
    CacheService --> InMemoryCacheManager
    InMemoryCacheManager --> Cache
    Cache --> CacheEntry
    Cache --> CacheConfiguration
```

---

## Sequence Diagrams

### Complete Cache Operation Sequence

```mermaid
sequenceDiagram
    participant Client
    participant REST API
    participant CacheService
    participant CacheManager
    participant ClusterCoordinator
    participant MessageSender
    participant Peer Node
    participant Database
    participant Metrics
    
    Client->>REST API: GET /api/cache/issue/keys/issue:123
    REST API->>CacheService: getOrLoad("issue", "issue:123", loader)
    
    CacheService->>CacheManager: get("issue", "issue:123")
    CacheManager-->>CacheService: Optional.empty() (miss)
    
    CacheService->>CacheService: acquireLock("issue:123")
    CacheService->>Database: loader.get() - Load Issue
    Database-->>CacheService: Issue data
    
    CacheService->>CacheManager: put("issue", "issue:123", issue)
    CacheManager->>CacheManager: Store in ConcurrentHashMap
    
    CacheService->>ClusterCoordinator: Should replicate?
    ClusterCoordinator-->>CacheService: Yes (INVALIDATE mode)
    
    CacheService->>MessageSender: sendInvalidation(peer, message)
    MessageSender->>MessageSender: Serialize with Kryo
    MessageSender->>Peer Node: TCP InvalidationMessage
    Peer Node-->>MessageSender: ACK
    
    CacheService->>Metrics: recordHit() / recordMiss()
    CacheService-->>REST API: Issue
    REST API-->>Client: 200 OK + Issue JSON
```

### Cluster Startup Sequence

```mermaid
sequenceDiagram
    participant Node1
    participant Node2
    participant Node3
    participant ClusterMembership
    participant MessageReceiver
    participant MessageSender
    
    Note over Node1,Node3: Application Startup
    
    Node1->>ClusterMembership: Initialize
    Node2->>ClusterMembership: Initialize
    Node3->>ClusterMembership: Initialize
    
    ClusterMembership->>ClusterMembership: Load Static Peers
    ClusterMembership->>MessageSender: Connect to Peer (Node2)
    ClusterMembership->>MessageSender: Connect to Peer (Node3)
    
    MessageSender->>Node2: TCP Connection
    Node2->>MessageReceiver: Accept Connection
    MessageReceiver->>ClusterMembership: Register Peer (Node1)
    
    MessageSender->>Node3: TCP Connection
    Node3->>MessageReceiver: Accept Connection
    MessageReceiver->>ClusterMembership: Register Peer (Node1)
    
    Node2->>MessageSender: Connect to Peer (Node3)
    MessageSender->>Node3: TCP Connection
    Node3->>MessageReceiver: Accept Connection
    MessageReceiver->>ClusterMembership: Register Peer (Node2)
    
    Note over Node1,Node3: All Nodes Connected
    
    Node1->>ClusterMembership: Start Heartbeat
    Node2->>ClusterMembership: Start Heartbeat
    Node3->>ClusterMembership: Start Heartbeat
    
    loop Every 5 seconds
        Node1->>Node2: Heartbeat
        Node1->>Node3: Heartbeat
        Node2->>Node1: Heartbeat
        Node2->>Node3: Heartbeat
        Node3->>Node1: Heartbeat
        Node3->>Node2: Heartbeat
    end
```

### Cache Replication Sequence

```mermaid
sequenceDiagram
    participant Writer
    participant WriterCache
    participant WriterCoordinator
    participant WriterSender
    participant Network
    participant ReaderReceiver
    participant ReaderCoordinator
    participant ReaderCache
    
    Writer->>WriterCache: put("issue", "issue:123", data)
    WriterCache->>WriterCache: Store locally
    WriterCache->>WriterCoordinator: Check replication mode
    
    alt Replication Mode: INVALIDATE
        WriterCoordinator->>WriterSender: Send InvalidationMessage
        WriterSender->>Network: Serialize & Send
        Network->>ReaderReceiver: Receive Message
        ReaderReceiver->>ReaderCoordinator: Process Invalidation
        ReaderCoordinator->>ReaderCache: invalidate("issue", "issue:123")
        ReaderCache->>ReaderCache: Remove from cache
    else Replication Mode: REPLICATE
        WriterCoordinator->>WriterSender: Send ReplicationMessage
        WriterSender->>Network: Serialize & Send
        Network->>ReaderReceiver: Receive Message
        ReaderReceiver->>ReaderCoordinator: Process Replication
        ReaderCoordinator->>ReaderCache: put("issue", "issue:123", data)
        ReaderCache->>ReaderCache: Store in cache
    else Replication Mode: NONE
        WriterCoordinator->>WriterCoordinator: Skip replication
    end
```

---

## Performance Characteristics

### Latency Breakdown

```mermaid
graph LR
    subgraph "Cache Hit"
        CH1[Client Request] --> CH2[CacheService<br/>0.1ms]
        CH2 --> CH3[CacheManager<br/>0.5ms]
        CH3 --> CH4[Return Value<br/>0.4ms]
        CH4 --> CH5[Total: <2ms]
    end
    
    subgraph "Cache Miss"
        CM1[Client Request] --> CM2[CacheService<br/>0.1ms]
        CM2 --> CM3[CacheManager<br/>0.5ms]
        CM3 --> CM4[Database Query<br/>300ms]
        CM4 --> CM5[Store in Cache<br/>1ms]
        CM5 --> CM6[Return Value<br/>0.4ms]
        CM6 --> CM7[Total: ~300ms]
    end
    
    style CH5 fill:#90EE90
    style CM7 fill:#FFE4B5
```

### Throughput Architecture

```mermaid
graph TB
    subgraph "High Concurrency"
        REQ1[Request 1]
        REQ2[Request 2]
        REQ3[Request 3]
        REQN[Request N<br/>5000 req/sec]
    end
    
    subgraph "Cache Service"
        CS[CacheService<br/>Thread-Safe]
        LOCKS[Per-Key Locks<br/>ConcurrentHashMap]
    end
    
    subgraph "Storage"
        CACHE[ConcurrentHashMap<br/>Lock-Free Reads]
    end
    
    REQ1 --> CS
    REQ2 --> CS
    REQ3 --> CS
    REQN --> CS
    
    CS --> LOCKS
    CS --> CACHE
    
    style CS fill:#90EE90
    style CACHE fill:#FFE4B5
```

---

## Security Architecture

### Security Layers

```mermaid
graph TB
    subgraph "External"
        CLIENT[Client Request]
    end
    
    subgraph "Security Layer"
        AUTH[Basic Authentication<br/>Spring Security]
        VALIDATE[Input Validation<br/>@Valid Annotations]
        RATE[Rate Limiting<br/>Future Enhancement]
    end
    
    subgraph "Application Layer"
        CONTROLLER[REST Controllers]
        SERVICE[Cache Service]
    end
    
    CLIENT --> AUTH
    AUTH -->|Authenticated| VALIDATE
    VALIDATE -->|Valid| CONTROLLER
    CONTROLLER --> SERVICE
    
    style AUTH fill:#FF6B6B
    style VALIDATE fill:#FFE66D
    style CONTROLLER fill:#90EE90
```

---

## Monitoring and Observability

### Metrics Flow

```mermaid
graph LR
    subgraph "Application"
        CS[CacheService]
        CM[CacheManager]
        CC[ClusterCoordinator]
    end
    
    subgraph "Metrics Collection"
        METRICS[CacheMetrics<br/>Micrometer]
        COUNTERS[Counters<br/>Hits, Misses]
        TIMERS[Timers<br/>Load Time]
        GAUGES[Gauges<br/>Size, Memory]
    end
    
    subgraph "Export"
        ACTUATOR[Spring Actuator<br/>/actuator/metrics]
        PROMETHEUS[Prometheus<br/>/actuator/prometheus]
    end
    
    subgraph "Visualization"
        GRAFANA[Grafana<br/>Dashboards]
        ALERTS[AlertManager<br/>Alerts]
    end
    
    CS --> METRICS
    CM --> METRICS
    CC --> METRICS
    
    METRICS --> COUNTERS
    METRICS --> TIMERS
    METRICS --> GAUGES
    
    COUNTERS --> ACTUATOR
    TIMERS --> ACTUATOR
    GAUGES --> ACTUATOR
    
    ACTUATOR --> PROMETHEUS
    PROMETHEUS --> GRAFANA
    PROMETHEUS --> ALERTS
```

---

## Summary

This system design document provides comprehensive visual diagrams of the Distributed Cache Management System architecture. All diagrams use **Mermaid syntax** which renders as actual visual diagrams in:

- ✅ GitHub
- ✅ GitLab
- ✅ Most Markdown viewers
- ✅ Documentation sites (MkDocs, Docusaurus, etc.)
- ✅ VS Code with Markdown Preview Enhanced

### Key Architecture Points

1. **Monolith Design:** Single JAR deployed on multiple nodes
2. **Peer-to-Peer:** Direct TCP communication between nodes
3. **In-Memory Storage:** ConcurrentHashMap for thread-safe access
4. **Thundering Herd Prevention:** Per-key locks with CompletableFuture
5. **Cluster Coordination:** Automatic invalidation/replication
6. **Observability:** Full metrics integration with Prometheus

---

**Last Updated:** November 2025  
**Version:** 1.0.0

