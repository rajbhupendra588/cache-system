package com.cache.cluster;

/**
 * Message for cache invalidation across cluster.
 * Serializable using Kryo (no need for Java Serializable).
 */
public class InvalidationMessage {
    
    private final String cacheName;
    private final String key;
    private final String originNodeId;
    private boolean invalidateAll;

    public InvalidationMessage(String cacheName, String key, String originNodeId) {
        this.cacheName = cacheName;
        this.key = key;
        this.originNodeId = originNodeId;
        this.invalidateAll = false;
    }

    public String getCacheName() {
        return cacheName;
    }

    public String getKey() {
        return key;
    }

    public String getOriginNodeId() {
        return originNodeId;
    }

    public boolean isInvalidateAll() {
        return invalidateAll;
    }

    public void setInvalidateAll(boolean invalidateAll) {
        this.invalidateAll = invalidateAll;
    }
}

