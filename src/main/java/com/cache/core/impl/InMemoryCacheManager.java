package com.cache.core.impl;

import com.cache.config.CacheConfiguration;
import com.cache.core.CacheEntry;
import com.cache.core.CacheStats;
import com.cache.metrics.CacheMetrics;
import org.openjdk.jol.info.GraphLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * In-memory cache manager implementing multiple eviction policies and TTL expiration.
 * 
 * <p>This manager provides thread-safe, high-performance caching with the following features:
 * <ul>
 *   <li><b>Multiple Eviction Policies:</b> LRU, LFU, and TTL-only</li>
 *   <li><b>TTL Expiration:</b> Automatic removal of expired entries</li>
 *   <li><b>Memory Management:</b> Configurable memory caps with accurate JOL-based estimation</li>
 *   <li><b>Thread Safety:</b> Uses ConcurrentHashMap and per-cache locks</li>
 *   <li><b>Metrics Integration:</b> Tracks hits, misses, evictions, and memory usage</li>
 * </ul>
 * 
 * <p><b>Performance Characteristics:</b>
 * <ul>
 *   <li>Cache hits: O(1) - constant time lookup</li>
 *   <li>Cache puts: O(1) average, O(n log k) worst case during eviction (LFU)</li>
 *   <li>Eviction: O(n log n) for LRU/TTL, O(n log k) for LFU where k is eviction count</li>
 * </ul>
 * 
 * <p><b>Thread Safety:</b> All public methods are thread-safe. Internal operations use
 * per-cache locks to ensure consistency during eviction and configuration changes.
 * 
 * @author Cache System
 * @since 1.0.0
 */
public class InMemoryCacheManager {
    private static final Logger logger = LoggerFactory.getLogger(InMemoryCacheManager.class);
    
    private final Map<String, Map<String, CacheEntry>> caches = new ConcurrentHashMap<>();
    private final Map<String, CacheConfiguration> configurations = new ConcurrentHashMap<>();
    private final Map<String, LocalCacheMetrics> metrics = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> cacheLocks = new ConcurrentHashMap<>();
    
    private final String nodeId;
    private final long defaultMaxEntries;
    private final long defaultMemoryCapBytes;
    private CacheMetrics cacheMetrics;

    /**
     * Constructs a new InMemoryCacheManager.
     * 
     * @param nodeId the unique identifier for this cache node
     * @param defaultMaxEntries the default maximum number of entries per cache
     * @param defaultMemoryCapBytes the default memory cap in bytes per cache
     */
    public InMemoryCacheManager(String nodeId, long defaultMaxEntries, long defaultMemoryCapBytes) {
        this.nodeId = nodeId;
        this.defaultMaxEntries = defaultMaxEntries;
        this.defaultMemoryCapBytes = defaultMemoryCapBytes;
    }

    /**
     * Sets the Micrometer metrics collector for cache statistics.
     * 
     * @param cacheMetrics the metrics collector
     */
    public void setCacheMetrics(CacheMetrics cacheMetrics) {
        this.cacheMetrics = cacheMetrics;
    }

    /**
     * Configures a cache with the specified configuration.
     * 
     * <p>This method initializes the cache if it doesn't exist, creating the necessary
     * data structures and locks. If the cache already exists, its configuration is updated.
     * 
     * @param cacheName the name of the cache to configure
     * @param config the cache configuration
     */
    public void configureCache(String cacheName, CacheConfiguration config) {
        configurations.put(cacheName, config);
        caches.putIfAbsent(cacheName, new ConcurrentHashMap<>());
        metrics.putIfAbsent(cacheName, new LocalCacheMetrics());
        cacheLocks.putIfAbsent(cacheName, new ReentrantLock());
        logger.info("Configured cache: {} with config: {}", cacheName, config);
    }

    /**
     * Retrieves a value from the cache.
     * 
     * <p>This method performs a thread-safe lookup. If the entry is found and not expired,
     * it updates the access metadata (for LRU/LFU policies) and returns the value.
     * Expired entries are automatically removed.
     * 
     * @param <T> the type of the cached value
     * @param cacheName the name of the cache
     * @param key the cache key
     * @param type the expected type of the cached value
     * @return an Optional containing the cached value if found and not expired, empty otherwise
     */
    public <T> Optional<T> get(String cacheName, String key, Class<T> type) {
        Map<String, CacheEntry> cache = caches.get(cacheName);
        if (cache == null) {
            metrics.computeIfAbsent(cacheName, k -> new LocalCacheMetrics()).recordMiss();
            return Optional.empty();
        }

        CacheEntry entry = cache.get(key);
        if (entry == null || entry.isExpired()) {
            if (entry != null && entry.isExpired()) {
                cache.remove(key);
            }
            metrics.get(cacheName).recordMiss();
            return Optional.empty();
        }

        entry.touch();
        metrics.get(cacheName).recordHit();
        
        @SuppressWarnings("unchecked")
        T value = (T) entry.getValue();
        return Optional.of(value);
    }

    /**
     * Stores a value in the cache.
     * 
     * <p>This method is thread-safe and performs automatic eviction if the cache exceeds
     * its configured limits (max entries or memory cap). The eviction policy is determined
     * by the cache configuration.
     * 
     * @param <T> the type of the value
     * @param cacheName the name of the cache
     * @param key the cache key
     * @param value the value to cache
     * @param ttl the time-to-live for the cached value (overrides cache default if provided)
     * @return the cached value
     */
    public <T> T put(String cacheName, String key, Object value, Duration ttl) {
        CacheConfiguration config = getConfiguration(cacheName);
        Map<String, CacheEntry> cache = caches.computeIfAbsent(cacheName, k -> new ConcurrentHashMap<>());
        LocalCacheMetrics localMetrics = metrics.computeIfAbsent(cacheName, k -> new LocalCacheMetrics());
        
        ReentrantLock lock = cacheLocks.computeIfAbsent(cacheName, k -> new ReentrantLock());
        lock.lock();
        try {
            // Check memory and evict if necessary
            evictIfNeeded(cacheName, cache, config);
            
            Instant expiresAt = Instant.now().plus(ttl != null ? ttl : config.getTtl());
            CacheEntry entry = new CacheEntry(value, expiresAt, nodeId, System.currentTimeMillis());
            
            cache.put(key, entry);
            localMetrics.recordPut();
            
            logger.debug("Put key {} into cache {}", key, cacheName);
            @SuppressWarnings("unchecked")
            T result = (T) value;
            return result;
        } finally {
            lock.unlock();
        }
    }

    public void putAll(String cacheName, Map<String, Object> entries, Duration ttl) {
        CacheConfiguration config = getConfiguration(cacheName);
        Map<String, CacheEntry> cache = caches.computeIfAbsent(cacheName, k -> new ConcurrentHashMap<>());
        LocalCacheMetrics localMetrics = metrics.computeIfAbsent(cacheName, k -> new LocalCacheMetrics());
        
        ReentrantLock lock = cacheLocks.computeIfAbsent(cacheName, k -> new ReentrantLock());
        lock.lock();
        try {
            Instant expiresAt = Instant.now().plus(ttl != null ? ttl : config.getTtl());
            
            for (Map.Entry<String, Object> entry : entries.entrySet()) {
                evictIfNeeded(cacheName, cache, config);
                CacheEntry cacheEntry = new CacheEntry(entry.getValue(), expiresAt, nodeId, System.currentTimeMillis());
                cache.put(entry.getKey(), cacheEntry);
                localMetrics.recordPut();
            }
            
            logger.debug("Put {} entries into cache {}", entries.size(), cacheName);
        } finally {
            lock.unlock();
        }
    }

    public void invalidate(String cacheName, String key) {
        Map<String, CacheEntry> cache = caches.get(cacheName);
        if (cache != null) {
            cache.remove(key);
            logger.debug("Invalidated key {} from cache {}", key, cacheName);
        }
    }

    public void invalidateAll(String cacheName) {
        Map<String, CacheEntry> cache = caches.get(cacheName);
        if (cache != null) {
            cache.clear();
            logger.info("Cleared all entries from cache {}", cacheName);
        }
    }

    public void invalidateByPrefix(String cacheName, String prefix) {
        Map<String, CacheEntry> cache = caches.get(cacheName);
        if (cache != null) {
            cache.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
            logger.debug("Invalidated keys with prefix {} from cache {}", prefix, cacheName);
        }
    }

    public CacheStats getStats(String cacheName) {
        Map<String, CacheEntry> cache = caches.get(cacheName);
        LocalCacheMetrics localMetrics = metrics.get(cacheName);
        
        if (cache == null || localMetrics == null) {
            return new CacheStats(cacheName, 0, 0, 0, 0, 0, Instant.now());
        }

        long size = cache.size();
        long memoryBytes = estimateMemoryUsage(cache);
        
        CacheStats stats = new CacheStats(
            cacheName,
            localMetrics.getHits(),
            localMetrics.getMisses(),
            localMetrics.getEvictions(),
            size,
            memoryBytes,
            Instant.now()
        );
        
        // Update Micrometer gauges
        if (cacheMetrics != null) {
            cacheMetrics.updateStats(cacheName, stats);
        }
        
        return stats;
    }

    public Collection<String> getKeys(String cacheName, String prefix) {
        Map<String, CacheEntry> cache = caches.get(cacheName);
        if (cache == null) {
            return Collections.emptyList();
        }
        
        if (prefix == null || prefix.isEmpty()) {
            return new ArrayList<>(cache.keySet());
        }
        
        return cache.keySet().stream()
            .filter(key -> key.startsWith(prefix))
            .collect(Collectors.toList());
    }

    private void evictIfNeeded(String cacheName, Map<String, CacheEntry> cache, CacheConfiguration config) {
        // Check max entries
        if (cache.size() >= config.getMaxEntries()) {
            evictByPolicy(cacheName, cache, config, 1);
        }
        
        // Check memory cap (rough estimation)
        long currentMemory = estimateMemoryUsage(cache);
        if (currentMemory >= config.getMemoryCapBytes()) {
            int entriesToEvict = (int) (cache.size() * 0.1); // Evict 10%
            evictByPolicy(cacheName, cache, config, entriesToEvict);
        }
        
        // Remove expired entries
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Evicts entries from the cache based on the configured eviction policy.
     * 
     * <p>This method uses optimized algorithms for each eviction policy:
     * <ul>
     *   <li><b>LRU:</b> Sorts by last accessed time (O(n log n))</li>
     *   <li><b>LFU:</b> Uses a heap-based approach (O(n log k) where k is count to evict)</li>
     *   <li><b>TTL_ONLY:</b> Sorts by expiration time (O(n log n))</li>
     * </ul>
     * 
     * @param cacheName the name of the cache
     * @param cache the cache map to evict from
     * @param config the cache configuration
     * @param count the number of entries to evict
     */
    private void evictByPolicy(String cacheName, Map<String, CacheEntry> cache, 
                              CacheConfiguration config, int count) {
        // Note: cacheMetrics here refers to the Micrometer CacheMetrics instance, not LocalCacheMetrics
        
        if (cache.isEmpty() || count <= 0) {
            return;
        }
        
        LocalCacheMetrics localMetrics = metrics.get(cacheName);
        int entriesToEvict = Math.min(count, cache.size());
        
        switch (config.getEvictionPolicy()) {
            case LRU:
                evictLRU(cache, entriesToEvict, localMetrics, cacheName);
                break;
            case LFU:
                evictLFU(cache, entriesToEvict, localMetrics, cacheName);
                break;
            case TTL_ONLY:
                evictTTL(cache, entriesToEvict, localMetrics, cacheName);
                break;
        }
        
        logger.debug("Evicted {} entries from cache {} using policy {}", entriesToEvict, cacheName, config.getEvictionPolicy());
    }
    
    /**
     * Evicts entries using LRU (Least Recently Used) policy.
     * Sorts all entries by last accessed time.
     * 
     * @param cache the cache map
     * @param count number of entries to evict
     * @param localMetrics local metrics tracker
     * @param cacheName cache name for metrics
     */
    private void evictLRU(Map<String, CacheEntry> cache, int count, 
                         LocalCacheMetrics localMetrics, String cacheName) {
        List<Map.Entry<String, CacheEntry>> entries = new ArrayList<>(cache.entrySet());
        entries.sort(Comparator.comparing(e -> e.getValue().getLastAccessedAt()));
        
        for (int i = 0; i < count; i++) {
            cache.remove(entries.get(i).getKey());
            recordEviction(localMetrics, cacheName);
        }
    }
    
    /**
     * Evicts entries using LFU (Least Frequently Used) policy.
     * Uses a heap-based approach for better performance on large caches.
     * 
     * <p>Performance: O(n log k) where n is cache size and k is count to evict.
     * This is more efficient than sorting all entries (O(n log n)) when k << n.
     * 
     * @param cache the cache map
     * @param count number of entries to evict
     * @param localMetrics local metrics tracker
     * @param cacheName cache name for metrics
     */
    private void evictLFU(Map<String, CacheEntry> cache, int count, 
                         LocalCacheMetrics localMetrics, String cacheName) {
        // Use a max-heap to efficiently find entries with lowest access count
        // PriorityQueue is a min-heap by default, so we reverse the comparator
        PriorityQueue<Map.Entry<String, CacheEntry>> heap = new PriorityQueue<>(
            count + 1,
            Comparator.comparingLong((Map.Entry<String, CacheEntry> e) -> 
                e.getValue().getAccessCount()).reversed()
        );
        
        // Add all entries to heap
        for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            heap.offer(entry);
            // Keep only the 'count' entries with lowest access count
            if (heap.size() > count) {
                heap.poll(); // Remove entry with highest access count
            }
        }
        
        // Evict the entries with lowest access count
        while (!heap.isEmpty()) {
            Map.Entry<String, CacheEntry> entry = heap.poll();
            cache.remove(entry.getKey());
            recordEviction(localMetrics, cacheName);
        }
    }
    
    /**
     * Evicts entries using TTL (Time To Live) policy.
     * Sorts all entries by expiration time.
     * 
     * @param cache the cache map
     * @param count number of entries to evict
     * @param localMetrics local metrics tracker
     * @param cacheName cache name for metrics
     */
    private void evictTTL(Map<String, CacheEntry> cache, int count, 
                         LocalCacheMetrics localMetrics, String cacheName) {
        List<Map.Entry<String, CacheEntry>> entries = new ArrayList<>(cache.entrySet());
        entries.sort(Comparator.comparing(e -> e.getValue().getExpiresAt()));
        
        for (int i = 0; i < count; i++) {
            cache.remove(entries.get(i).getKey());
            recordEviction(localMetrics, cacheName);
        }
    }
    
    /**
     * Records an eviction in both local and Micrometer metrics.
     * 
     * @param localMetrics local metrics tracker
     * @param cacheName cache name for metrics
     */
    private void recordEviction(LocalCacheMetrics localMetrics, String cacheName) {
            if (localMetrics != null) {
                localMetrics.recordEviction();
            }
            if (cacheMetrics != null) {
            CacheConfiguration config = getConfiguration(cacheName);
                cacheMetrics.recordEviction(cacheName, config.getEvictionPolicy().name());
            }
        }
        
    /**
     * Estimates memory usage of the cache using JOL (Java Object Layout).
     * 
     * <p>This method uses JOL to accurately measure the memory footprint of cache entries.
     * For large caches, it samples a subset of entries to avoid performance overhead.
     * 
     * @param cache the cache map to estimate
     * @return estimated memory usage in bytes
     */
    private long estimateMemoryUsage(Map<String, CacheEntry> cache) {
        if (cache.isEmpty()) {
            return 0;
        }
        
        // For small caches, measure all entries
        if (cache.size() <= 100) {
            try {
                GraphLayout layout = GraphLayout.parseInstance(cache);
                return layout.totalSize();
            } catch (Exception e) {
                logger.debug("JOL measurement failed, falling back to estimation", e);
                return estimateMemoryUsageFallback(cache);
            }
        }
        
        // For large caches, sample entries to avoid performance overhead
        int sampleSize = Math.min(100, cache.size());
        List<Map.Entry<String, CacheEntry>> sample = new ArrayList<>(cache.entrySet())
            .subList(0, sampleSize);
        
        try {
            Map<String, CacheEntry> sampleMap = new HashMap<String, CacheEntry>();
            for (Map.Entry<String, CacheEntry> entry : sample) {
                sampleMap.put(entry.getKey(), entry.getValue());
            }
            GraphLayout layout = GraphLayout.parseInstance(sampleMap);
            long sampleSizeBytes = layout.totalSize();
            // Extrapolate to full cache size
            return (sampleSizeBytes * cache.size()) / sampleSize;
        } catch (Exception e) {
            logger.debug("JOL measurement failed, falling back to estimation", e);
            return estimateMemoryUsageFallback(cache);
    }
    }
    
    /**
     * Fallback memory estimation when JOL is unavailable or fails.
     * Uses conservative estimates based on typical object sizes.
     * 
     * @param cache the cache map to estimate
     * @return estimated memory usage in bytes
     */
    private long estimateMemoryUsageFallback(Map<String, CacheEntry> cache) {
        // Conservative estimate: 64 bytes overhead per entry + key size + value size
        long baseOverhead = 64;
        long total = 0;
        
        for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            long keySize = entry.getKey().length() * 2; // UTF-16 chars = 2 bytes
            Object value = entry.getValue().getValue();
            long valueSize = estimateValueSize(value);
            total += baseOverhead + keySize + valueSize;
        }
        
        return total;
    }
    
    /**
     * Estimates the size of a cached value object.
     * 
     * @param value the value object
     * @return estimated size in bytes
     */
    private long estimateValueSize(Object value) {
        if (value == null) {
            return 0;
        }
        
        // Common types with known sizes
        if (value instanceof String) {
            return ((String) value).length() * 2; // UTF-16
        } else if (value instanceof Number) {
            if (value instanceof Long || value instanceof Double) {
                return 24; // 8 bytes + object overhead
            } else {
                return 16; // 4 bytes + object overhead
            }
        } else if (value instanceof Boolean) {
            return 16;
        } else if (value instanceof Collection) {
            return 64 + ((Collection<?>) value).size() * 16; // Rough estimate
        } else if (value instanceof Map) {
            return 64 + ((Map<?, ?>) value).size() * 32; // Rough estimate
        }
        
        // Default: try JOL for this object
        try {
            return GraphLayout.parseInstance(value).totalSize();
        } catch (Exception e) {
            // Fallback: 128 bytes for unknown objects
            return 128;
        }
    }

    public CacheConfiguration getConfiguration(String cacheName) {
        return configurations.getOrDefault(cacheName, getDefaultConfiguration());
    }

    private CacheConfiguration getDefaultConfiguration() {
        return new CacheConfiguration(
            Duration.ofHours(1),
            CacheConfiguration.EvictionPolicy.LRU,
            defaultMaxEntries,
            defaultMemoryCapBytes,
            CacheConfiguration.ReplicationMode.NONE,
            CacheConfiguration.PersistenceMode.NONE
        );
    }

    public Set<String> getCacheNames() {
        return new HashSet<>(caches.keySet());
    }

    // Inner class for local metrics tracking
    private static class LocalCacheMetrics {
        private long hits = 0;
        private long misses = 0;
        private long evictions = 0;

        public synchronized void recordHit() {
            hits++;
        }

        public synchronized void recordMiss() {
            misses++;
        }

        public synchronized void recordEviction() {
            evictions++;
        }

        public synchronized void recordPut() {
            // Put operations are tracked via cache size, no separate counter needed
        }

        public long getHits() {
            return hits;
        }

        public long getMisses() {
            return misses;
        }

        public long getEvictions() {
            return evictions;
        }
    }
}

