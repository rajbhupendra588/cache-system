# Quick Start Guide - 5 Minute Setup

Get your cache system up and running in 5 minutes!

## Step 1: Add Dependency (1 minute)

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>com.cache</groupId>
    <artifactId>distributed-cache-system</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Step 2: Configure Cache (1 minute)

Add to your `application.yml`:

```yaml
cache:
  system:
    caches:
      issue:
        ttl: PT30M
        eviction-policy: LRU
        max-entries: 50000
```

## Step 3: Inject CacheService (1 minute)

```java
@Service
public class YourService {
    
    @Autowired
    private CacheService cacheService;
    
    // Your code here
}
```

## Step 4: Use Cache (2 minutes)

### Replace this:
```java
public Issue getIssue(Long id) {
    return issueRepository.findById(id).orElseThrow();
}
```

### With this:
```java
public Issue getIssue(Long id) {
    String key = "issue:" + id;
    return cacheService.getOrLoad(
        "issue",
        key,
        () -> issueRepository.findById(id).orElseThrow(),
        Duration.ofMinutes(30)
    );
}
```

## Step 5: Invalidate After Updates

```java
public Issue updateIssue(Issue issue) {
    Issue saved = issueRepository.save(issue);
    cacheService.invalidate("issue", "issue:" + saved.getId());
    return saved;
}
```

## Done! ✅

Your cache is now active. Check performance:

```java
CacheStats stats = cacheService.getStats("issue");
System.out.println("Hit ratio: " + stats.getHitRatio());
```

**Expected Results:**
- First call: Cache miss (loads from DB)
- Subsequent calls: Cache hit (<2ms)
- Hit ratio should be >70% after warm-up

---

## Common Patterns

### Pattern 1: Simple Entity Caching
```java
public Entity getEntity(Long id) {
    return cacheService.getOrLoad(
        "entity",
        "entity:" + id,
        () -> repository.findById(id).orElseThrow(),
        Duration.ofMinutes(30)
    );
}
```

### Pattern 2: Complex Aggregation
```java
public ComplexDTO getComplexData(Long id) {
    return cacheService.getOrLoad(
        "complex",
        "complex:" + id,
        () -> {
            // Your complex loading logic
            return buildComplexDTO(id);
        },
        Duration.ofMinutes(15)
    );
}
```

### Pattern 3: Cache-Aside
```java
public Entity getEntity(Long id) {
    Optional<Entity> cached = cacheService.get("entity", "entity:" + id, Entity.class);
    if (cached.isPresent()) {
        return cached.get();
    }
    
    Entity entity = repository.findById(id).orElseThrow();
    cacheService.put("entity", "entity:" + id, entity, Duration.ofMinutes(30));
    return entity;
}
```

---

## Next Steps

- Read [../guides/DEVELOPER_GUIDE.md](../guides/DEVELOPER_GUIDE.md) for detailed documentation
- Check [../production/PRODUCTION_DEPLOYMENT.md](../production/PRODUCTION_DEPLOYMENT.md) for production setup
- Review examples in the guide for advanced usage

---

**Questions?** See the FAQ section in [../guides/DEVELOPER_GUIDE.md](../guides/DEVELOPER_GUIDE.md)

