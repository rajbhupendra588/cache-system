package com.cache.core;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Core cache service interface for distributed cache operations.
 * Provides typed cache operations with support for loading, invalidation, and statistics.
 */
public interface CacheService {
    
    /**
     * Get a value from cache.
     * 
     * @param cacheName Name of the cache
     * @param key Cache key
     * @param type Expected type of the value
     * @return Optional containing the value if present
     */
    <T> Optional<T> get(String cacheName, String key, Class<T> type);
    
    /**
     * Get a value from cache, or load it using the supplier if not present.
     * Implements thundering herd prevention - only one loader executes per key.
     * 
     * @param cacheName Name of the cache
     * @param key Cache key
     * @param loader Supplier to load the value if cache miss
     * @param ttl Time-to-live for the cached value
     * @return The cached or loaded value
     */
    <T> T getOrLoad(String cacheName, String key, Supplier<T> loader, Duration ttl);
    
    /**
     * Put a value into cache.
     * 
     * @param cacheName Name of the cache
     * @param key Cache key
     * @param value Value to cache
     * @param ttl Time-to-live for the cached value
     */
    void put(String cacheName, String key, Object value, Duration ttl);
    
    /**
     * Put multiple entries into cache atomically.
     * 
     * @param cacheName Name of the cache
     * @param entries Map of key-value pairs
     * @param ttl Time-to-live for all entries
     */
    void putAll(String cacheName, Map<String, Object> entries, Duration ttl);
    
    /**
     * Invalidate a specific key from cache.
     * 
     * @param cacheName Name of the cache
     * @param key Cache key to invalidate
     */
    void invalidate(String cacheName, String key);
    
    /**
     * Invalidate all entries in a cache.
     * 
     * @param cacheName Name of the cache
     */
    void invalidateAll(String cacheName);
    
    /**
     * Prefetch multiple keys into cache.
     * 
     * @param cacheName Name of the cache
     * @param keys Collection of keys to prefetch
     */
    void prefetch(String cacheName, Collection<String> keys);
    
    /**
     * Get statistics for a cache.
     * 
     * @param cacheName Name of the cache
     * @return Cache statistics
     */
    CacheStats getStats(String cacheName);
}

