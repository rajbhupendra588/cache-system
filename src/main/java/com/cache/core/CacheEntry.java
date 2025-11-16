package com.cache.core;

import java.time.Instant;

/**
 * Internal cache entry with metadata.
 */
public class CacheEntry {
    private final Object value;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final String originNodeId;
    private final long version;
    private Instant lastAccessedAt;
    private long accessCount;

    public CacheEntry(Object value, Instant expiresAt, String originNodeId, long version) {
        this.value = value;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
        this.originNodeId = originNodeId;
        this.version = version;
        this.lastAccessedAt = Instant.now();
        this.accessCount = 0;
    }

    public Object getValue() {
        return value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public String getOriginNodeId() {
        return originNodeId;
    }

    public long getVersion() {
        return version;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void touch() {
        this.lastAccessedAt = Instant.now();
        this.accessCount++;
    }

    public long getAccessCount() {
        return accessCount;
    }
}

