package com.cache.core;

import java.time.Instant;

/**
 * Statistics for a cache instance.
 */
public class CacheStats {
    private final String cacheName;
    private final long hits;
    private final long misses;
    private final long evictions;
    private final long size;
    private final long memoryBytes;
    private final double hitRatio;
    private final Instant lastUpdated;

    public CacheStats(String cacheName, long hits, long misses, long evictions, 
                     long size, long memoryBytes, Instant lastUpdated) {
        this.cacheName = cacheName;
        this.hits = hits;
        this.misses = misses;
        this.evictions = evictions;
        this.size = size;
        this.memoryBytes = memoryBytes;
        this.lastUpdated = lastUpdated;
        long total = hits + misses;
        this.hitRatio = total > 0 ? (double) hits / total : 0.0;
    }

    public String getCacheName() {
        return cacheName;
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

    public long getSize() {
        return size;
    }

    public long getMemoryBytes() {
        return memoryBytes;
    }

    public double getHitRatio() {
        return hitRatio;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }
}

