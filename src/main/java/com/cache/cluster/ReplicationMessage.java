package com.cache.cluster;

import java.time.Duration;

/**
 * Message for cache replication across cluster.
 * Serializable using Kryo (no need for Java Serializable).
 */
public class ReplicationMessage {
    
    private final String cacheName;
    private final String key;
    private final Object value;
    private final Duration ttl;
    private final String originNodeId;

    public ReplicationMessage(String cacheName, String key, Object value, Duration ttl, String originNodeId) {
        this.cacheName = cacheName;
        this.key = key;
        this.value = value;
        this.ttl = ttl;
        this.originNodeId = originNodeId;
    }

    public String getCacheName() {
        return cacheName;
    }

    public String getKey() {
        return key;
    }

    public Object getValue() {
        return value;
    }

    public Duration getTtl() {
        return ttl;
    }

    public String getOriginNodeId() {
        return originNodeId;
    }
}

