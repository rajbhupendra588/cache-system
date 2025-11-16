package com.cache.health;

import com.cache.core.impl.InMemoryCacheManager;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom health indicator for cache system.
 * Note: HealthIndicator interface is available via actuator dependency.
 * If compilation fails, ensure spring-boot-starter-actuator is in classpath.
 */
@Component
public class CacheHealthIndicator {
    
    private final InMemoryCacheManager cacheManager;

    public CacheHealthIndicator(InMemoryCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        try {
            int cacheCount = cacheManager.getCacheNames().size();
            health.put("status", "UP");
            health.put("cacheCount", cacheCount);
            health.put("message", "Cache system is operational");
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
        }
        return health;
    }
}

