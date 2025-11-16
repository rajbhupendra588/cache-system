package com.cache.cluster;

import com.cache.core.impl.InMemoryCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Coordinates cache operations across cluster nodes.
 * Handles invalidation and replication messages.
 */
@Component
public class ClusterCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(ClusterCoordinator.class);
    
    private final ClusterMembership membership;
    private final MessageSender messageSender;
    private final InMemoryCacheManager cacheManager;
    private final boolean asyncMode;

    public ClusterCoordinator(ClusterMembership membership, MessageSender messageSender,
                             InMemoryCacheManager cacheManager, boolean asyncMode) {
        this.membership = membership;
        this.messageSender = messageSender;
        this.cacheManager = cacheManager;
        this.asyncMode = asyncMode;
    }

    /**
     * Send invalidation message to all peers.
     */
    public void sendInvalidation(String cacheName, String key) {
        Set<String> peers = membership.getActivePeers();
        if (peers.isEmpty()) {
            logger.debug("No peers available for invalidation");
            return;
        }

        InvalidationMessage message = new InvalidationMessage(cacheName, key, membership.getNodeId());
        
        if (asyncMode) {
            CompletableFuture.runAsync(() -> {
                for (String peer : peers) {
                    try {
                        messageSender.sendInvalidation(peer, message);
                    } catch (Exception e) {
                        logger.warn("Failed to send invalidation to peer {}: {}", peer, e.getMessage());
                    }
                }
            });
        } else {
            for (String peer : peers) {
                try {
                    messageSender.sendInvalidation(peer, message);
                } catch (Exception e) {
                    logger.warn("Failed to send invalidation to peer {}: {}", peer, e.getMessage());
                }
            }
        }
    }

    /**
     * Send invalidation for all keys in a cache.
     */
    public void sendInvalidationAll(String cacheName) {
        Set<String> peers = membership.getActivePeers();
        if (peers.isEmpty()) {
            return;
        }

        InvalidationMessage message = new InvalidationMessage(cacheName, null, membership.getNodeId());
        message.setInvalidateAll(true);
        
        if (asyncMode) {
            CompletableFuture.runAsync(() -> {
                for (String peer : peers) {
                    try {
                        messageSender.sendInvalidation(peer, message);
                    } catch (Exception e) {
                        logger.warn("Failed to send invalidation-all to peer {}: {}", peer, e.getMessage());
                    }
                }
            });
        } else {
            for (String peer : peers) {
                try {
                    messageSender.sendInvalidation(peer, message);
                } catch (Exception e) {
                    logger.warn("Failed to send invalidation-all to peer {}: {}", peer, e.getMessage());
                }
            }
        }
    }

    /**
     * Send replication message to peers.
     */
    public void sendReplication(String cacheName, String key, Object value, Duration ttl) {
        Set<String> peers = membership.getActivePeers();
        if (peers.isEmpty()) {
            return;
        }

        ReplicationMessage message = new ReplicationMessage(cacheName, key, value, ttl, membership.getNodeId());
        
        if (asyncMode) {
            CompletableFuture.runAsync(() -> {
                for (String peer : peers) {
                    try {
                        messageSender.sendReplication(peer, message);
                    } catch (Exception e) {
                        logger.warn("Failed to send replication to peer {}: {}", peer, e.getMessage());
                    }
                }
            });
        } else {
            for (String peer : peers) {
                try {
                    messageSender.sendReplication(peer, message);
                } catch (Exception e) {
                    logger.warn("Failed to send replication to peer {}: {}", peer, e.getMessage());
                }
            }
        }
    }

    /**
     * Handle incoming invalidation message from peer.
     */
    public void handleInvalidation(InvalidationMessage message) {
        logger.debug("Received invalidation: cache={}, key={}", message.getCacheName(), message.getKey());
        
        if (message.isInvalidateAll()) {
            cacheManager.invalidateAll(message.getCacheName());
        } else {
            cacheManager.invalidate(message.getCacheName(), message.getKey());
        }
    }

    /**
     * Handle incoming replication message from peer.
     */
    public void handleReplication(ReplicationMessage message) {
        logger.debug("Received replication: cache={}, key={}", message.getCacheName(), message.getKey());
        cacheManager.put(message.getCacheName(), message.getKey(), message.getValue(), message.getTtl());
    }
    
    /**
     * Handle incoming heartbeat message from peer.
     */
    public void handleHeartbeat(HeartbeatMessage message) {
        logger.debug("Received heartbeat from node: {}", message.getNodeId());
        // Mark peer as active - need to find peer address from nodeId
        // For now, we'll use nodeId as address (assuming format matches)
        String peerAddress = message.getNodeId();
        membership.recordHeartbeatResponse(peerAddress);
    }
}

