package com.cache.core.exception;

/**
 * Exception thrown when a cache or cache entry is not found.
 */
public class CacheNotFoundException extends CacheException {
    
    public CacheNotFoundException(String cacheName) {
        super(String.format("Cache '%s' not found", cacheName));
    }
    
    public CacheNotFoundException(String cacheName, String key) {
        super(String.format("Key '%s' not found in cache '%s'", key, cacheName));
    }
}

