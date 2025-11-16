package com.cache.cluster;

import com.cache.cluster.exception.ClusterCommunicationException;
import com.cache.util.SerializationUtil;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Sends cache coordination messages to peer nodes.
 * Uses TCP sockets with connection pooling, timeouts, retry logic, and circuit breaker.
 * 
 * <p>This class implements connection pooling to reuse sockets across multiple messages,
 * significantly improving performance by avoiding the overhead of establishing new connections
 * for each message.
 * 
 * <p><b>Features:</b>
 * <ul>
 *   <li>Connection pooling per peer address</li>
 *   <li>Automatic connection health checking</li>
 *   <li>Retry logic with exponential backoff</li>
 *   <li>Circuit breaker pattern for fault tolerance</li>
 *   <li>Configurable timeouts</li>
 * </ul>
 * 
 * @author Cache System
 * @since 1.0.0
 */
@Component
public class MessageSender {
    private static final Logger logger = LoggerFactory.getLogger(MessageSender.class);
    
    private final int communicationPort;
    private final int connectTimeoutMs;
    private final int socketTimeoutMs;
    private final ConcurrentMap<String, Retry> retryMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CircuitBreaker> circuitBreakerMap = new ConcurrentHashMap<>();
    
    // Connection pool: one socket per peer address
    private final ConcurrentMap<String, PooledConnection> connectionPool = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ReentrantLock> connectionLocks = new ConcurrentHashMap<>();

    public MessageSender(int communicationPort) {
        this.communicationPort = communicationPort;
        this.connectTimeoutMs = 5000; // 5 seconds
        this.socketTimeoutMs = 10000; // 10 seconds
        
        // Retry and circuit breaker instances are created per-peer on demand
        // in getOrCreateRetry() and getOrCreateCircuitBreaker() methods
    }

    private Retry getOrCreateRetry(String peerAddress) {
        return retryMap.computeIfAbsent(peerAddress, addr -> {
            RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(100))
                .retryOnException(e -> e instanceof IOException || e instanceof SocketTimeoutException)
                .build();
            return Retry.of("messageSender-" + addr, retryConfig);
        });
    }

    private CircuitBreaker getOrCreateCircuitBreaker(String peerAddress) {
        return circuitBreakerMap.computeIfAbsent(peerAddress, addr -> {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .build();
            return CircuitBreaker.of("messageSender-" + addr, config);
        });
    }

    public void sendInvalidation(String peerAddress, InvalidationMessage message) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(peerAddress);
        Retry retry = getOrCreateRetry(peerAddress);
        
        Runnable sendOperation = () -> {
            try {
                sendMessage(peerAddress, "INVALIDATION", message);
            } catch (Exception e) {
                logger.warn("Failed to send invalidation to {}: {}", peerAddress, e.getMessage());
                throw new ClusterCommunicationException(peerAddress, "send invalidation", e);
            }
        };
        
        try {
            Runnable decorated = Retry.decorateRunnable(retry, 
                CircuitBreaker.decorateRunnable(circuitBreaker, sendOperation));
            decorated.run();
        } catch (Exception e) {
            logger.error("Circuit breaker open or retry exhausted for {}: {}", peerAddress, e.getMessage());
            // Don't throw - allow async mode to continue
        }
    }

    public void sendReplication(String peerAddress, ReplicationMessage message) {
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(peerAddress);
        Retry retry = getOrCreateRetry(peerAddress);
        
        Runnable sendOperation = () -> {
            try {
                sendMessage(peerAddress, "REPLICATION", message);
            } catch (Exception e) {
                logger.warn("Failed to send replication to {}: {}", peerAddress, e.getMessage());
                throw new ClusterCommunicationException(peerAddress, "send replication", e);
            }
        };
        
        try {
            Runnable decorated = Retry.decorateRunnable(retry, 
                CircuitBreaker.decorateRunnable(circuitBreaker, sendOperation));
            decorated.run();
        } catch (Exception e) {
            logger.error("Circuit breaker open or retry exhausted for {}: {}", peerAddress, e.getMessage());
            // Don't throw - allow async mode to continue
        }
    }

    /**
     * Sends a heartbeat message to a peer node.
     * Used for health monitoring and connectivity verification.
     * 
     * @param peerAddress the peer address (host:port)
     * @param message the heartbeat message
     * @throws ClusterCommunicationException if the heartbeat cannot be sent
     */
    public void sendHeartbeat(String peerAddress, HeartbeatMessage message) {
        try {
            sendMessage(peerAddress, "HEARTBEAT", message);
        } catch (Exception e) {
            throw new ClusterCommunicationException(peerAddress, "send heartbeat", e);
        }
    }

    /**
     * Sends a message to a peer using connection pooling.
     * 
     * <p>This method reuses existing connections when possible, creating new ones only
     * when necessary. Connections are automatically validated before use.
     * 
     * @param peerAddress the peer address (host:port)
     * @param messageType the type of message (INVALIDATION or REPLICATION)
     * @param message the message object to send
     * @throws IOException if the message cannot be sent
     */
    private void sendMessage(String peerAddress, String messageType, Object message) throws IOException {
        String[] parts = peerAddress.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : communicationPort;
        String fullAddress = host + ":" + port;
        
        // Get or create connection pool entry
        ReentrantLock lock = connectionLocks.computeIfAbsent(fullAddress, k -> new ReentrantLock());
        lock.lock();
        try {
            PooledConnection pooled = connectionPool.get(fullAddress);
            
            // Check if connection is valid
            if (pooled == null || !isConnectionValid(pooled.socket)) {
                // Create new connection
                if (pooled != null) {
                    closeConnection(pooled.socket);
                }
                pooled = createConnection(host, port);
                connectionPool.put(fullAddress, pooled);
            }
            
            // Use the pooled connection
            sendMessageOnSocket(pooled.socket, messageType, message, peerAddress);
            pooled.lastUsed = System.currentTimeMillis();
            
        } catch (IOException e) {
            // Connection failed, remove from pool
            PooledConnection pooled = connectionPool.remove(fullAddress);
            if (pooled != null) {
                closeConnection(pooled.socket);
            }
            throw e;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Creates a new connection to the specified host and port.
     * 
     * @param host the host address
     * @param port the port number
     * @return a pooled connection wrapper
     * @throws IOException if the connection cannot be established
     */
    private PooledConnection createConnection(String host, int port) throws IOException {
        Socket socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            socket.setSoTimeout(socketTimeoutMs);
        socket.setKeepAlive(true); // Enable TCP keep-alive
        logger.debug("Created new connection to {}:{}", host, port);
        return new PooledConnection(socket, System.currentTimeMillis());
    }
    
    /**
     * Checks if a socket connection is still valid.
     * 
     * @param socket the socket to check
     * @return true if the connection is valid, false otherwise
     */
    private boolean isConnectionValid(Socket socket) {
        if (socket == null || socket.isClosed()) {
            return false;
        }
        // Check if socket is still connected
        if (!socket.isConnected()) {
            return false;
        }
        // Check if socket is not in error state
        try {
            socket.getInputStream().available(); // This will throw if socket is broken
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Sends a message on an existing socket connection.
     * 
     * @param socket the socket to use
     * @param messageType the type of message
     * @param message the message object
     * @param peerAddress the peer address for logging
     * @throws IOException if the message cannot be sent
     */
    private void sendMessageOnSocket(Socket socket, String messageType, Object message, String peerAddress) throws IOException {
            // Serialize message using Kryo
            byte[] messageBytes = SerializationUtil.serialize(message);
            byte[] typeBytes = messageType.getBytes("UTF-8");
            
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        DataInputStream in = new DataInputStream(socket.getInputStream());
                
        try {
                // Send message type length and type
                out.writeInt(typeBytes.length);
                out.write(typeBytes);
                
                // Send message length and message
                out.writeInt(messageBytes.length);
                out.write(messageBytes);
                out.flush();
                
                // Wait for acknowledgment
                String ack = in.readUTF();
                if (!"OK".equals(ack)) {
                    throw new IOException("Received error acknowledgment: " + ack);
                }
                
                logger.debug("Successfully sent {} to {}", messageType, peerAddress);
        } catch (SocketTimeoutException e) {
            logger.warn("Timeout sending {} to {}: {}", messageType, peerAddress, e.getMessage());
            throw e;
        } catch (IOException e) {
            logger.warn("IO error sending {} to {}: {}", messageType, peerAddress, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Closes a socket connection safely.
     * 
     * @param socket the socket to close
     */
    private void closeConnection(Socket socket) {
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    logger.debug("Error closing socket", e);
                }
            }
    }
    
    /**
     * Represents a pooled socket connection with metadata.
     */
    private static class PooledConnection {
        final Socket socket;
        @SuppressWarnings("unused") // Reserved for future connection pool cleanup logic
        long lastUsed;
        
        PooledConnection(Socket socket, long lastUsed) {
            this.socket = socket;
            this.lastUsed = lastUsed;
        }
    }
}
