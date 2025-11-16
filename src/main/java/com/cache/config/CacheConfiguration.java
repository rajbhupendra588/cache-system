package com.cache.config;

import java.time.Duration;

/**
 * Configuration for a named cache.
 */
public class CacheConfiguration {
    private Duration ttl;
    private EvictionPolicy evictionPolicy;
    private long maxEntries;
    private long memoryCapBytes;
    private ReplicationMode replicationMode;
    private PersistenceMode persistenceMode;

    public CacheConfiguration() {
    }

    public CacheConfiguration(Duration ttl, EvictionPolicy evictionPolicy, 
                             long maxEntries, long memoryCapBytes,
                             ReplicationMode replicationMode, PersistenceMode persistenceMode) {
        this.ttl = ttl;
        this.evictionPolicy = evictionPolicy;
        this.maxEntries = maxEntries;
        this.memoryCapBytes = memoryCapBytes;
        this.replicationMode = replicationMode;
        this.persistenceMode = persistenceMode;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public EvictionPolicy getEvictionPolicy() {
        return evictionPolicy;
    }

    public void setEvictionPolicy(EvictionPolicy evictionPolicy) {
        this.evictionPolicy = evictionPolicy;
    }

    public long getMaxEntries() {
        return maxEntries;
    }

    public void setMaxEntries(long maxEntries) {
        this.maxEntries = maxEntries;
    }

    public long getMemoryCapBytes() {
        return memoryCapBytes;
    }

    public void setMemoryCapBytes(long memoryCapBytes) {
        this.memoryCapBytes = memoryCapBytes;
    }

    public ReplicationMode getReplicationMode() {
        return replicationMode;
    }

    public void setReplicationMode(ReplicationMode replicationMode) {
        this.replicationMode = replicationMode;
    }

    public PersistenceMode getPersistenceMode() {
        return persistenceMode;
    }

    public void setPersistenceMode(PersistenceMode persistenceMode) {
        this.persistenceMode = persistenceMode;
    }

    public enum EvictionPolicy {
        LRU, LFU, TTL_ONLY
    }

    public enum ReplicationMode {
        NONE, INVALIDATE, REPLICATE
    }

    public enum PersistenceMode {
        NONE, WRITE_THROUGH, WRITE_BACK
    }
}

