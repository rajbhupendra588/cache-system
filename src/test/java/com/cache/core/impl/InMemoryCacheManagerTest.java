package com.cache.core.impl;

import com.cache.config.CacheConfiguration;
import com.cache.core.CacheStats;
import com.cache.metrics.CacheMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class InMemoryCacheManagerTest {

    private InMemoryCacheManager cacheManager;
    private CacheMetrics cacheMetrics;

    @BeforeEach
    void setUp() {
        cacheManager = new InMemoryCacheManager("test-node", 1000, 1024 * 1024);
        cacheMetrics = new CacheMetrics(mock(MeterRegistry.class));
        cacheManager.setCacheMetrics(cacheMetrics);
        
        CacheConfiguration config = new CacheConfiguration(
            Duration.ofMinutes(30),
            CacheConfiguration.EvictionPolicy.LRU,
            1000,
            1024 * 1024,
            CacheConfiguration.ReplicationMode.NONE,
            CacheConfiguration.PersistenceMode.NONE
        );
        cacheManager.configureCache("test", config);
    }

    @Test
    void testPutAndGet() {
        String cacheName = "test";
        String key = "key1";
        String value = "value1";
        
        cacheManager.put(cacheName, key, value, Duration.ofMinutes(1));
        Optional<String> result = cacheManager.get(cacheName, key, String.class);
        
        assertTrue(result.isPresent());
        assertEquals(value, result.get());
    }

    @Test
    void testGet_ExpiredEntry() {
        String cacheName = "test";
        String key = "key1";
        String value = "value1";
        
        // Put with very short TTL
        cacheManager.put(cacheName, key, value, Duration.ofNanos(1));
        
        // Wait a bit
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        Optional<String> result = cacheManager.get(cacheName, key, String.class);
        
        assertFalse(result.isPresent(), "Expired entry should not be returned");
    }

    @Test
    void testInvalidate() {
        String cacheName = "test";
        String key = "key1";
        String value = "value1";
        
        cacheManager.put(cacheName, key, value, Duration.ofMinutes(1));
        cacheManager.invalidate(cacheName, key);
        
        Optional<String> result = cacheManager.get(cacheName, key, String.class);
        assertFalse(result.isPresent());
    }

    @Test
    void testInvalidateAll() {
        String cacheName = "test";
        
        cacheManager.put(cacheName, "key1", "value1", Duration.ofMinutes(1));
        cacheManager.put(cacheName, "key2", "value2", Duration.ofMinutes(1));
        cacheManager.invalidateAll(cacheName);
        
        assertFalse(cacheManager.get(cacheName, "key1", String.class).isPresent());
        assertFalse(cacheManager.get(cacheName, "key2", String.class).isPresent());
    }

    @Test
    void testEviction_MaxEntries() {
        CacheConfiguration config = new CacheConfiguration(
            Duration.ofMinutes(30),
            CacheConfiguration.EvictionPolicy.LRU,
            5, // Very small max entries
            1024 * 1024,
            CacheConfiguration.ReplicationMode.NONE,
            CacheConfiguration.PersistenceMode.NONE
        );
        cacheManager.configureCache("small", config);
        
        // Add more entries than max
        for (int i = 0; i < 10; i++) {
            cacheManager.put("small", "key" + i, "value" + i, Duration.ofMinutes(1));
        }
        
        // Oldest entries should be evicted
        CacheStats stats = cacheManager.getStats("small");
        assertTrue(stats.getSize() <= 5, "Cache size should not exceed max entries");
    }

    @Test
    void testGetStats() {
        String cacheName = "test";
        
        cacheManager.put(cacheName, "key1", "value1", Duration.ofMinutes(1));
        cacheManager.get(cacheName, "key1", String.class); // Hit
        cacheManager.get(cacheName, "key2", String.class); // Miss
        
        CacheStats stats = cacheManager.getStats(cacheName);
        
        assertEquals(cacheName, stats.getCacheName());
        assertTrue(stats.getHits() > 0);
        assertTrue(stats.getMisses() > 0);
        assertTrue(stats.getSize() > 0);
    }

    @Test
    void testGetKeys_WithPrefix() {
        String cacheName = "test";
        
        cacheManager.put(cacheName, "issue:123", "value1", Duration.ofMinutes(1));
        cacheManager.put(cacheName, "issue:456", "value2", Duration.ofMinutes(1));
        cacheManager.put(cacheName, "user:789", "value3", Duration.ofMinutes(1));
        
        var keys = cacheManager.getKeys(cacheName, "issue:");
        
        assertEquals(2, keys.size());
        assertTrue(keys.contains("issue:123"));
        assertTrue(keys.contains("issue:456"));
    }
}

