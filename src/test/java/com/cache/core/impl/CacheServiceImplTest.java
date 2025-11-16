package com.cache.core.impl;

import com.cache.cluster.ClusterCoordinator;
import com.cache.config.CacheConfiguration;
import com.cache.core.CacheService;
import com.cache.core.CacheStats;
import com.cache.metrics.CacheMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheServiceImplTest {

    @Mock
    private InMemoryCacheManager cacheManager;
    
    @Mock
    private ClusterCoordinator clusterCoordinator;
    
    @Mock
    private MeterRegistry meterRegistry;
    
    private CacheService cacheService;
    private CacheMetrics cacheMetrics;

    @BeforeEach
    void setUp() {
        cacheMetrics = new CacheMetrics(meterRegistry);
        cacheService = new CacheServiceImpl(cacheManager, clusterCoordinator, cacheMetrics);
        
        // Setup default configuration
        CacheConfiguration config = new CacheConfiguration(
            Duration.ofMinutes(30),
            CacheConfiguration.EvictionPolicy.LRU,
            10000,
            1024 * 1024,
            CacheConfiguration.ReplicationMode.NONE,
            CacheConfiguration.PersistenceMode.NONE
        );
        when(cacheManager.getConfiguration(anyString())).thenReturn(config);
    }

    @Test
    void testGet_CacheHit() {
        String cacheName = "test";
        String key = "key1";
        String value = "value1";
        
        when(cacheManager.get(cacheName, key, String.class))
            .thenReturn(Optional.of(value));
        
        Optional<String> result = cacheService.get(cacheName, key, String.class);
        
        assertTrue(result.isPresent());
        assertEquals(value, result.get());
    }

    @Test
    void testGet_CacheMiss() {
        String cacheName = "test";
        String key = "key1";
        
        when(cacheManager.get(cacheName, key, String.class))
            .thenReturn(Optional.empty());
        
        Optional<String> result = cacheService.get(cacheName, key, String.class);
        
        assertFalse(result.isPresent());
    }

    @Test
    void testGetOrLoad_CacheHit() {
        String cacheName = "test";
        String key = "key1";
        String value = "value1";
        
        when(cacheManager.get(cacheName, key, Object.class))
            .thenReturn(Optional.of(value));
        
        String result = cacheService.getOrLoad(cacheName, key, () -> "loaded", Duration.ofMinutes(1));
        
        assertEquals(value, result);
        verify(cacheManager, never()).put(anyString(), anyString(), any(), any());
    }

    @Test
    void testGetOrLoad_CacheMiss() {
        String cacheName = "test";
        String key = "key1";
        String loadedValue = "loaded";
        
        when(cacheManager.get(cacheName, key, Object.class))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.empty()); // Double-check also misses
        
        String result = cacheService.getOrLoad(cacheName, key, () -> loadedValue, Duration.ofMinutes(1));
        
        assertEquals(loadedValue, result);
        verify(cacheManager).put(cacheName, key, loadedValue, Duration.ofMinutes(1));
    }

    @Test
    void testGetOrLoad_ThunderingHerdPrevention() throws InterruptedException {
        String cacheName = "test";
        String key = "key1";
        AtomicInteger loadCount = new AtomicInteger(0);
        int concurrentRequests = 100;
        CountDownLatch latch = new CountDownLatch(concurrentRequests);
        
        when(cacheManager.get(cacheName, key, Object.class))
            .thenReturn(Optional.empty()); // Always miss
        
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        
        for (int i = 0; i < concurrentRequests; i++) {
            executor.submit(() -> {
                try {
                    cacheService.getOrLoad(cacheName, key, () -> {
                        loadCount.incrementAndGet();
                        try {
                            Thread.sleep(10); // Simulate slow load
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "value";
                    }, Duration.ofMinutes(1));
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS), "All requests should complete");
        
        // Should only load once despite 100 concurrent requests
        assertEquals(1, loadCount.get(), "Loader should only execute once");
        
        executor.shutdown();
    }

    @Test
    void testPut_InvalidationMode() {
        String cacheName = "test";
        String key = "key1";
        String value = "value1";
        
        CacheConfiguration config = new CacheConfiguration(
            Duration.ofMinutes(30),
            CacheConfiguration.EvictionPolicy.LRU,
            10000,
            1024 * 1024,
            CacheConfiguration.ReplicationMode.INVALIDATE,
            CacheConfiguration.PersistenceMode.NONE
        );
        when(cacheManager.getConfiguration(cacheName)).thenReturn(config);
        
        cacheService.put(cacheName, key, value, Duration.ofMinutes(1));
        
        verify(cacheManager).put(cacheName, key, value, Duration.ofMinutes(1));
        verify(clusterCoordinator).sendInvalidation(cacheName, key);
    }

    @Test
    void testPut_ReplicationMode() {
        String cacheName = "test";
        String key = "key1";
        String value = "value1";
        
        CacheConfiguration config = new CacheConfiguration(
            Duration.ofMinutes(30),
            CacheConfiguration.EvictionPolicy.LRU,
            10000,
            1024 * 1024,
            CacheConfiguration.ReplicationMode.REPLICATE,
            CacheConfiguration.PersistenceMode.NONE
        );
        when(cacheManager.getConfiguration(cacheName)).thenReturn(config);
        
        cacheService.put(cacheName, key, value, Duration.ofMinutes(1));
        
        verify(cacheManager).put(cacheName, key, value, Duration.ofMinutes(1));
        verify(clusterCoordinator).sendReplication(eq(cacheName), eq(key), eq(value), any());
    }

    @Test
    void testInvalidate() {
        String cacheName = "test";
        String key = "key1";
        
        cacheService.invalidate(cacheName, key);
        
        verify(cacheManager).invalidate(cacheName, key);
        verify(clusterCoordinator).sendInvalidation(cacheName, key);
    }

    @Test
    void testInvalidateAll() {
        String cacheName = "test";
        
        cacheService.invalidateAll(cacheName);
        
        verify(cacheManager).invalidateAll(cacheName);
        verify(clusterCoordinator).sendInvalidationAll(cacheName);
    }

    @Test
    void testGetStats() {
        String cacheName = "test";
        CacheStats expectedStats = new CacheStats(
            cacheName, 100, 50, 10, 1000, 1024000, 
            java.time.Instant.now()
        );
        
        when(cacheManager.getStats(cacheName)).thenReturn(expectedStats);
        
        CacheStats result = cacheService.getStats(cacheName);
        
        assertEquals(expectedStats, result);
    }
}

