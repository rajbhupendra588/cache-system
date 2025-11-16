package com.cache.core.exception;

/**
 * Exception thrown when a cache loader fails to load a value.
 */
public class CacheLoadException extends CacheException {
    
    public CacheLoadException(String cacheName, String key, Throwable cause) {
        super(String.format("Failed to load value for cache '%s' key '%s': %s", 
            cacheName, key, cause.getMessage()), cause);
    }
    
    public CacheLoadException(String message) {
        super(message);
    }
    
    public CacheLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}

