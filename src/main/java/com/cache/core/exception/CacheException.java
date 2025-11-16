package com.cache.core.exception;

/**
 * Base exception for all cache-related errors.
 */
public class CacheException extends RuntimeException {
    
    public CacheException(String message) {
        super(message);
    }
    
    public CacheException(String message, Throwable cause) {
        super(message, cause);
    }
}

