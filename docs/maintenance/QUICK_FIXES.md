# Quick Fixes for Production Readiness

These are the **most critical** fixes that should be implemented before any production deployment.

## 1. Remove Hardcoded Credentials (CRITICAL - 30 minutes)

**File:** `src/main/java/com/cache/config/SecurityConfiguration.java`

```java
@Bean
public UserDetailsService userDetailsService() {
    String username = System.getenv("CACHE_ADMIN_USERNAME");
    String password = System.getenv("CACHE_ADMIN_PASSWORD");
    
    if (username == null || password == null) {
        throw new IllegalStateException(
            "CACHE_ADMIN_USERNAME and CACHE_ADMIN_PASSWORD environment variables must be set");
    }
    
    UserDetails admin = User.builder()
        .username(username)
        .password(passwordEncoder().encode(password))
        .roles("ADMIN")
        .build();
    
    return new InMemoryUserDetailsManager(admin);
}
```

**Alternative:** Use Spring Boot's `spring.security.user.name` and `spring.security.user.password` in `application.yml` (but still use environment variable substitution).

## 2. Add Socket Timeouts (CRITICAL - 15 minutes)

**File:** `src/main/java/com/cache/cluster/MessageSender.java`

```java
public void sendInvalidation(String peerAddress, InvalidationMessage message) {
    try {
        String[] parts = peerAddress.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : communicationPort;
        
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000); // 5 second timeout
        socket.setSoTimeout(10000); // 10 second read timeout
        
        try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            
            out.writeObject("INVALIDATION");
            out.writeObject(message);
            out.flush();
            
            String ack = (String) in.readObject();
            logger.debug("Received ack from {}: {}", peerAddress, ack);
        } finally {
            socket.close();
        }
    } catch (SocketTimeoutException e) {
        logger.warn("Timeout sending invalidation to {}: {}", peerAddress, e.getMessage());
        // Don't throw - allow async mode to continue
    } catch (Exception e) {
        logger.error("Failed to send invalidation to {}: {}", peerAddress, e.getMessage());
        // Don't throw RuntimeException - log and continue
    }
}
```

**Apply same pattern to `sendReplication()` method.**

## 3. Implement Graceful Shutdown (CRITICAL - 1 hour)

**File:** `src/main/java/com/cache/config/CacheSystemConfiguration.java`

Add shutdown hook:

```java
@Bean
public ApplicationListener<ContextClosedEvent> shutdownListener(
        MessageReceiver messageReceiver,
        ClusterMembership clusterMembership) {
    return event -> {
        logger.info("Shutting down cache system...");
        try {
            // Stop accepting new messages
            messageReceiver.stop();
            
            // Notify peers of shutdown
            // (Add method to ClusterMembership to broadcast shutdown)
            
            // Flush any pending operations
            // (If write-behind is implemented)
            
            logger.info("Cache system shutdown complete");
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        }
    };
}
```

**File:** `src/main/java/com/cache/cluster/ClusterMembership.java`

Add shutdown notification:

```java
public void shutdown() {
    scheduler.shutdown();
    try {
        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
            scheduler.shutdownNow();
        }
    } catch (InterruptedException e) {
        scheduler.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
```

## 4. Add Retry Logic (IMPORTANT - 2 hours)

**Add dependency to `pom.xml`:**

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.1.0</version>
</dependency>
```

**File:** `src/main/java/com/cache/cluster/MessageSender.java`

```java
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

@Component
public class MessageSender {
    private final Retry retry;
    
    public MessageSender(int communicationPort) {
        this.communicationPort = communicationPort;
        
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(100))
            .retryOnException(e -> e instanceof IOException)
            .build();
        this.retry = Retry.of("messageSender", config);
    }
    
    public void sendInvalidation(String peerAddress, InvalidationMessage message) {
        Retry.decorateRunnable(retry, () -> {
            try {
                // ... existing send logic ...
            } catch (Exception e) {
                logger.warn("Retry attempt failed for {}: {}", peerAddress, e.getMessage());
                throw e; // Will be retried
            }
        }).run();
    }
}
```

## 5. Add Basic Input Validation (IMPORTANT - 1 hour)

**File:** `src/main/java/com/cache/api/CacheController.java`

```java
@PostMapping("/{name}/invalidate")
public ResponseEntity<Map<String, Object>> invalidate(
        @PathVariable @NotBlank String name,
        @RequestBody @Valid InvalidateRequest request) {
    
    // Validate cache name exists
    if (!cacheManager.getCacheNames().contains(name)) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "Cache not found: " + name));
    }
    
    // Validate request
    if (request.getKeys() != null) {
        if (request.getKeys().size() > 1000) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Too many keys (max 1000)"));
        }
    }
    
    // ... rest of method ...
}
```

**File:** `src/main/java/com/cache/api/CacheController.java`

Add validation annotations:

```java
public static class InvalidateRequest {
    @Size(max = 1000, message = "Maximum 1000 keys allowed")
    private List<@NotBlank String> keys;
    
    @Length(max = 500, message = "Key too long")
    private String key;
    
    @Length(max = 500, message = "Prefix too long")
    private String prefix;
    
    // ... getters/setters ...
}
```

## 6. Add Basic Unit Tests (CRITICAL - 4 hours)

**File:** `src/test/java/com/cache/core/impl/CacheServiceImplTest.java`

```java
@SpringBootTest
class CacheServiceImplTest {
    
    @Autowired
    private CacheService cacheService;
    
    @Test
    void testGetOrLoad_ThunderingHerdPrevention() throws InterruptedException {
        AtomicInteger loadCount = new AtomicInteger(0);
        int concurrentRequests = 100;
        CountDownLatch latch = new CountDownLatch(concurrentRequests);
        
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        
        for (int i = 0; i < concurrentRequests; i++) {
            executor.submit(() -> {
                try {
                    cacheService.getOrLoad("test", "key1", () -> {
                        loadCount.incrementAndGet();
                        return "value1";
                    }, Duration.ofMinutes(1));
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(5, TimeUnit.SECONDS);
        
        // Should only load once despite 100 concurrent requests
        assertEquals(1, loadCount.get());
    }
    
    @Test
    void testPutAndGet() {
        cacheService.put("test", "key1", "value1", Duration.ofMinutes(1));
        Optional<String> result = cacheService.get("test", "key1", String.class);
        assertTrue(result.isPresent());
        assertEquals("value1", result.get());
    }
}
```

## 7. Replace Java Serialization (IMPORTANT - 3 hours)

**File:** `src/main/java/com/cache/cluster/SerializationUtil.java` (new file)

```java
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class SerializationUtil {
    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        return kryo;
    });
    
    public static byte[] serialize(Object obj) {
        Kryo kryo = kryoThreadLocal.get();
        try (Output output = new Output(1024, -1)) {
            kryo.writeClassAndObject(output, obj);
            return output.toBytes();
        }
    }
    
    public static Object deserialize(byte[] data) {
        Kryo kryo = kryoThreadLocal.get();
        try (Input input = new Input(data)) {
            return kryo.readClassAndObject(input);
        }
    }
}
```

**Update `MessageSender` and `MessageReceiver` to use Kryo instead of Java serialization.**

## Priority Order

1. **Day 1**: Remove hardcoded credentials (#1)
2. **Day 1**: Add socket timeouts (#2)
3. **Day 2**: Implement graceful shutdown (#3)
4. **Day 3**: Add basic unit tests (#6)
5. **Week 1**: Add retry logic (#4) and input validation (#5)
6. **Week 2**: Replace serialization (#7)

## Testing After Fixes

After implementing these fixes, verify:

```bash
# 1. Test with environment variables
export CACHE_ADMIN_USERNAME=myuser
export CACHE_ADMIN_PASSWORD=mypass
mvn spring-boot:run

# 2. Test graceful shutdown
# Send SIGTERM and verify clean shutdown in logs

# 3. Test timeout handling
# Block port 9090 with firewall and verify no hanging

# 4. Run unit tests
mvn test
```

---

**Note:** These are quick fixes to address the most critical issues. For full production readiness, refer to `PRODUCTION_READINESS.md` for comprehensive checklist.

