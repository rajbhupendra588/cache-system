# Using Cache System as a Library

This guide explains how to use the Distributed Cache Management System as a library in your existing Java + Spring Boot application.

## Table of Contents

1. [Adding as Dependency](#adding-as-dependency)
2. [Configuration](#configuration)
3. [Using CacheService](#using-cacheservice)
4. [Complete Example](#complete-example)
5. [Integration with Ticket System](#integration-with-ticket-system)
6. [Troubleshooting](#troubleshooting)

---

## Adding as Dependency

### Option 1: Install to Local Maven Repository

If you've built the cache system locally:

```bash
cd /path/to/cache-system
mvn clean install
```

This installs the artifact to your local Maven repository (`~/.m2/repository`).

### Option 2: Add to Your Project's pom.xml

Add the dependency to your application's `pom.xml`:

```xml
<dependencies>
    <!-- Your existing dependencies -->
    
    <!-- Distributed Cache System Library -->
    <dependency>
        <groupId>com.cache</groupId>
        <artifactId>distributed-cache-system</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    
    <!-- Spring Boot Starter (if not already present) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    
    <!-- Spring Boot Web (for REST endpoints) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

### Option 3: Deploy to Private Maven Repository

If deploying to a private repository (Nexus, Artifactory, etc.):

```bash
mvn clean deploy
```

Then add the repository to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>your-private-repo</id>
        <url>https://your-repo-url/repository/maven-releases/</url>
    </repository>
</repositories>
```

---

## Configuration

### Step 1: Add Configuration Properties

Add cache configuration to your `application.yml` or `application.properties`:

**application.yml:**
```yaml
cache:
  system:
    # Default cache settings
    default-ttl: PT30M
    default-eviction-policy: LRU
    default-max-entries: 50000
    default-memory-cap-mb: 2048
    
    # Cluster configuration (optional - for multi-node deployments)
    cluster:
      enabled: false  # Set to true for cluster mode
      node-id: ${HOSTNAME:app-node-1}
      discovery:
        type: static
        static:
          peers: node1:8080,node2:8080
      heartbeat:
        interval-ms: 5000
        timeout-ms: 15000
      communication:
        port: 9090
        async: true
    
    # Per-cache configurations
    caches:
      issue:
        ttl: PT30M
        eviction-policy: LRU
        max-entries: 100000
        memory-cap-mb: 4096
        replication-mode: NONE  # NONE, INVALIDATE, or REPLICATE
        persistence-mode: NONE
        
      comments:
        ttl: PT5M
        eviction-policy: LRU
        max-entries: 500000
        memory-cap-mb: 2048
        replication-mode: NONE
        
      user:
        ttl: PT2H
        eviction-policy: LRU
        max-entries: 20000
        memory-cap-mb: 512
        replication-mode: NONE
```

**application.properties:**
```properties
# Default cache settings
cache.system.default-ttl=PT30M
cache.system.default-eviction-policy=LRU
cache.system.default-max-entries=50000
cache.system.default-memory-cap-mb=2048

# Cluster configuration (optional)
cache.system.cluster.enabled=false
cache.system.cluster.node-id=${HOSTNAME:app-node-1}

# Per-cache configurations
cache.system.caches.issue.ttl=PT30M
cache.system.caches.issue.eviction-policy=LRU
cache.system.caches.issue.max-entries=100000
cache.system.caches.issue.memory-cap-mb=4096
```

### Step 2: Enable Auto-Configuration

The cache system uses Spring Boot auto-configuration. Ensure your main application class scans the cache package:

```java
package com.yourcompany.yourapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
// The cache system will be auto-configured automatically
// No additional annotations needed!
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

**Note:** The cache system auto-configures itself. If it doesn't, ensure:
1. The cache system JAR is in your classpath
2. Spring Boot can find the `com.cache` package
3. You have `spring-boot-starter` dependency

---

## Using CacheService

### Step 1: Inject CacheService

Simply inject `CacheService` into your service classes:

```java
package com.yourcompany.yourapp.service;

import com.cache.core.CacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class YourService {
    
    @Autowired
    private CacheService cacheService;
    
    // Your methods here
}
```

### Step 2: Use Cache Operations

#### Basic Usage: Get or Load

```java
@Service
public class IssueService {
    
    @Autowired
    private CacheService cacheService;
    
    @Autowired
    private IssueRepository issueRepository;
    
    public Issue getIssue(Long issueId) {
        String cacheKey = "issue:" + issueId;
        
        return cacheService.getOrLoad(
            "issue",                    // Cache name (must match config)
            cacheKey,                   // Cache key
            () -> issueRepository.findById(issueId).orElseThrow(),  // Loader function
            Duration.ofMinutes(30)      // TTL (optional - uses config default if null)
        );
    }
}
```

#### Manual Get/Put

```java
public Issue getIssueManual(Long issueId) {
    String cacheKey = "issue:" + issueId;
    
    // Try cache first
    Optional<Issue> cached = cacheService.get("issue", cacheKey, Issue.class);
    if (cached.isPresent()) {
        return cached.get();
    }
    
    // Cache miss - load from database
    Issue issue = issueRepository.findById(issueId).orElseThrow();
    
    // Cache it
    cacheService.put("issue", cacheKey, issue, Duration.ofMinutes(30));
    
    return issue;
}
```

#### Invalidation

```java
@Transactional
public Issue updateIssue(Long issueId, IssueUpdate update) {
    Issue issue = issueRepository.findById(issueId).orElseThrow();
    issue.update(update);
    Issue saved = issueRepository.save(issue);
    
    // CRITICAL: Invalidate cache after update
    cacheService.invalidate("issue", "issue:" + issueId);
    
    return saved;
}
```

---

## Complete Example

Here's a complete example of integrating the cache system into your ticket management application:

### 1. Project Structure

```
your-ticket-app/
├── pom.xml
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── yourcompany/
│       │           └── ticketapp/
│       │               ├── TicketApplication.java
│       │               ├── service/
│       │               │   └── IssueService.java
│       │               └── controller/
│       │                   └── IssueController.java
│       └── resources/
│           └── application.yml
```

### 2. pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <groupId>com.yourcompany</groupId>
    <artifactId>ticket-app</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>
    
    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        
        <!-- Spring Data JPA (your existing dependency) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        
        <!-- Distributed Cache System Library -->
        <dependency>
            <groupId>com.cache</groupId>
            <artifactId>distributed-cache-system</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>
        
        <!-- Your database driver -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 3. application.yml

```yaml
spring:
  application:
    name: ticket-management-app
  datasource:
    url: jdbc:mysql://localhost:3306/ticketdb
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

# Cache System Configuration
cache:
  system:
    default-ttl: PT30M
    default-eviction-policy: LRU
    default-max-entries: 50000
    default-memory-cap-mb: 2048
    
    # Cluster mode disabled for single-node deployment
    cluster:
      enabled: false
    
    # Configure caches for your use case
    caches:
      issue:
        ttl: PT30M
        eviction-policy: LRU
        max-entries: 100000
        memory-cap-mb: 4096
        replication-mode: NONE
        
      comments:
        ttl: PT5M
        eviction-policy: LRU
        max-entries: 500000
        memory-cap-mb: 2048
        replication-mode: NONE
```

### 4. Main Application Class

```java
package com.yourcompany.ticketapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TicketApplication {
    public static void main(String[] args) {
        SpringApplication.run(TicketApplication.class, args);
    }
}
```

### 5. Service with Caching

```java
package com.yourcompany.ticketapp.service;

import com.cache.core.CacheService;
import com.cache.core.CacheStats;
import com.yourcompany.ticketapp.model.Issue;
import com.yourcompany.ticketapp.repository.IssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class IssueService {
    
    private static final Logger log = LoggerFactory.getLogger(IssueService.class);
    
    @Autowired
    private CacheService cacheService;
    
    @Autowired
    private IssueRepository issueRepository;
    
    /**
     * Get issue with caching.
     * First call: loads from database (~5 seconds)
     * Subsequent calls: returns from cache (<2ms)
     */
    public Issue getIssue(Long issueId) {
        String cacheKey = "issue:" + issueId;
        
        return cacheService.getOrLoad(
            "issue",
            cacheKey,
            () -> {
                log.info("Cache miss - loading issue {} from database", issueId);
                return loadIssueFromDatabase(issueId);
            },
            Duration.ofMinutes(30)
        );
    }
    
    /**
     * Load issue from database (your existing slow method).
     * This aggregates data from ~20 tables.
     */
    private Issue loadIssueFromDatabase(Long issueId) {
        // Your existing implementation that queries 20 tables
        // This is only called on cache miss!
        return issueRepository.findByIdWithAllRelations(issueId)
            .orElseThrow(() -> new IssueNotFoundException(issueId));
    }
    
    /**
     * Update issue - invalidate cache.
     */
    @Transactional
    public Issue updateIssue(Long issueId, IssueUpdate update) {
        Issue issue = getIssue(issueId);
        issue.update(update);
        Issue saved = issueRepository.save(issue);
        
        // CRITICAL: Invalidate cache
        cacheService.invalidate("issue", "issue:" + issueId);
        
        log.info("Updated issue {} and invalidated cache", issueId);
        return saved;
    }
    
    /**
     * Get cache statistics for monitoring.
     */
    public CacheStats getCacheStats() {
        return cacheService.getStats("issue");
    }
}
```

### 6. Controller

```java
package com.yourcompany.ticketapp.controller;

import com.yourcompany.ticketapp.model.Issue;
import com.yourcompany.ticketapp.service.IssueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/issues")
public class IssueController {
    
    @Autowired
    private IssueService issueService;
    
    @GetMapping("/{issueId}")
    public ResponseEntity<Issue> getIssue(@PathVariable Long issueId) {
        Issue issue = issueService.getIssue(issueId);
        return ResponseEntity.ok(issue);
    }
    
    @PutMapping("/{issueId}")
    public ResponseEntity<Issue> updateIssue(
            @PathVariable Long issueId,
            @RequestBody IssueUpdate update) {
        Issue updated = issueService.updateIssue(issueId, update);
        return ResponseEntity.ok(updated);
    }
}
```

---

## Integration with Ticket System

For your specific use case (Jira-like system with 20 tables), see the detailed integration guide:

### Quick Integration Steps

1. **Add Dependency** (see above)

2. **Configure Cache** in `application.yml`:
```yaml
cache:
  system:
    caches:
      issue:
        ttl: PT30M
        max-entries: 100000
        memory-cap-mb: 4096
```

3. **Create IssueViewDTO** (aggregates all 20 tables):
```java
@Data
@Builder
public class IssueViewDTO {
    private Long issueId;
    private String key;
    private String title;
    // ... fields from all 20 tables
    private List<CommentDTO> comments;
    private List<AttachmentDTO> attachments;
    // ... etc
}
```

4. **Update Your Service**:
```java
@Service
public class IssueViewService {
    @Autowired
    private CacheService cacheService;
    
    public IssueViewDTO getIssueView(Long issueId) {
        return cacheService.getOrLoad(
            "issue",
            "issue:view:" + issueId,
            () -> loadFrom20Tables(issueId),  // Your existing slow method
            Duration.ofMinutes(30)
        );
    }
    
    private IssueViewDTO loadFrom20Tables(Long issueId) {
        // Your existing implementation that queries 20 tables
        // This only executes on cache miss!
    }
}
```

5. **Add Invalidation** after every update:
```java
@Transactional
public void updateIssue(Long issueId, IssueUpdate update) {
    // Update in database
    issueRepository.save(update);
    
    // Invalidate cache
    cacheService.invalidate("issue", "issue:view:" + issueId);
}
```

**See `DEVELOPER_GUIDE.md` section "Ticket Management System Integration" for complete examples.**

---

## Advanced Configuration

### Using Environment Variables

Override configuration with environment variables:

```bash
export CACHE_SYSTEM_DEFAULT_TTL=PT1H
export CACHE_SYSTEM_CACHES_ISSUE_MAX_ENTRIES=200000
export CACHE_SYSTEM_CLUSTER_ENABLED=true
```

### Programmatic Configuration

You can also configure caches programmatically:

```java
@Configuration
public class CacheConfiguration {
    
    @Autowired
    private InMemoryCacheManager cacheManager;
    
    @PostConstruct
    public void configureCaches() {
        CacheConfiguration config = new CacheConfiguration(
            Duration.ofMinutes(30),
            CacheConfiguration.EvictionPolicy.LRU,
            100000,
            4096 * 1024 * 1024,
            CacheConfiguration.ReplicationMode.NONE,
            CacheConfiguration.PersistenceMode.NONE
        );
        
        cacheManager.configureCache("customCache", config);
    }
}
```

---

## Troubleshooting

### Problem: CacheService Not Found

**Error:** `No qualifying bean of type 'com.cache.core.CacheService'`

**Solution:**
1. Ensure the cache system JAR is in your classpath
2. Check that Spring Boot can scan `com.cache` package
3. Verify dependency is correctly added to `pom.xml`

```xml
<!-- Verify this dependency exists -->
<dependency>
    <groupId>com.cache</groupId>
    <artifactId>distributed-cache-system</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Problem: Cache Not Working

**Symptoms:** Still seeing slow performance, cache hit ratio is 0%

**Solution:**
1. Check cache configuration in `application.yml`
2. Verify cache name matches configuration:
```java
// Cache name must match config
cacheService.getOrLoad("issue", ...)  // "issue" must be in application.yml
```

3. Check cache statistics:
```java
CacheStats stats = cacheService.getStats("issue");
System.out.println("Hit ratio: " + stats.getHitRatio());
```

### Problem: ClassNotFoundException

**Error:** `java.lang.ClassNotFoundException: com.cache.core.CacheService`

**Solution:**
1. Rebuild and install cache system:
```bash
cd cache-system
mvn clean install
```

2. Rebuild your application:
```bash
cd your-app
mvn clean install
```

3. Verify JAR is in classpath:
```bash
mvn dependency:tree | grep cache-system
```

### Problem: Configuration Not Loading

**Symptoms:** Default values used instead of your configuration

**Solution:**
1. Check `application.yml` syntax
2. Verify property names match exactly:
```yaml
cache:
  system:  # Must be exactly "system"
    caches:
      issue:  # Cache name
        ttl: PT30M  # Must be valid ISO-8601 duration
```

3. Enable debug logging:
```yaml
logging:
  level:
    com.cache: DEBUG
```

---

## Best Practices

### 1. Cache Key Design

Use consistent, descriptive cache keys:

```java
// ✅ Good
"issue:view:" + issueId
"user:profile:" + userId
"comments:issue:" + issueId

// ❌ Bad
issueId.toString()  // Not descriptive
"cache:" + issueId   // Redundant prefix
```

### 2. TTL Selection

Choose TTL based on data update frequency:

```java
// Frequently updated (comments, status)
Duration.ofMinutes(5)

// Moderately updated (issue details)
Duration.ofMinutes(30)

// Rarely updated (user profiles, reference data)
Duration.ofHours(2)
```

### 3. Always Invalidate After Updates

```java
@Transactional
public void updateIssue(Long issueId, IssueUpdate update) {
    issueRepository.save(update);
    cacheService.invalidate("issue", "issue:" + issueId);  // ✅ Critical!
}
```

### 4. Monitor Cache Performance

```java
@Scheduled(fixedRate = 60000)
public void monitorCache() {
    CacheStats stats = cacheService.getStats("issue");
    if (stats.getHitRatio() < 0.7) {
        log.warn("Low cache hit ratio: {}%", stats.getHitRatio() * 100);
    }
}
```

---

## Next Steps

1. **Read the Developer Guide**: See `DEVELOPER_GUIDE.md` for detailed examples
2. **Check Integration Checklist**: See `INTEGRATION_CHECKLIST.md` for step-by-step integration
3. **Review Examples**: See `examples/` directory for code samples
4. **Monitor Performance**: Track cache hit ratios and response times

---

## Support

- **Documentation**: See `DEVELOPER_GUIDE.md` for complete API reference
- **Examples**: Check `examples/` directory
- **Configuration**: See `application.yml` in cache-system project for all options

---

**Happy Caching! 🚀**


