package com.cache.core.exception;

/**
 * Exception thrown when serialization or deserialization fails.
 */
public class SerializationException extends CacheException {
    
    public SerializationException(String message) {
        super(message);
    }
    
    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}

