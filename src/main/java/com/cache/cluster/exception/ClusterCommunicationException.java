package com.cache.cluster.exception;

/**
 * Exception thrown when cluster communication fails.
 */
public class ClusterCommunicationException extends RuntimeException {
    
    public ClusterCommunicationException(String peerAddress, String operation, Throwable cause) {
        super(String.format("Failed to %s with peer %s: %s", 
            operation, peerAddress, cause.getMessage()), cause);
    }
    
    public ClusterCommunicationException(String message) {
        super(message);
    }
    
    public ClusterCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}

