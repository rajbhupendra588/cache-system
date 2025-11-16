package com.cache.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages cluster membership and peer discovery.
 */
@Component
public class ClusterMembership {
    private static final Logger logger = LoggerFactory.getLogger(ClusterMembership.class);
    
    private final String nodeId;
    private final Set<String> knownPeers = ConcurrentHashMap.newKeySet();
    private final Set<String> activePeers = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final long heartbeatIntervalMs;
    private final long heartbeatTimeoutMs;

    public ClusterMembership(String nodeId, Set<String> initialPeers, 
                           long heartbeatIntervalMs, long heartbeatTimeoutMs) {
        this.nodeId = nodeId;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.heartbeatTimeoutMs = heartbeatTimeoutMs;
        
        if (initialPeers != null) {
            this.knownPeers.addAll(initialPeers);
            this.activePeers.addAll(initialPeers);
        }
        
        startHeartbeat();
    }

    public String getNodeId() {
        return nodeId;
    }

    public Set<String> getActivePeers() {
        return Collections.unmodifiableSet(activePeers);
    }

    public Set<String> getKnownPeers() {
        return Collections.unmodifiableSet(knownPeers);
    }

    public void addPeer(String peerAddress) {
        knownPeers.add(peerAddress);
        activePeers.add(peerAddress);
        logger.info("Added peer: {}", peerAddress);
    }

    public void removePeer(String peerAddress) {
        knownPeers.remove(peerAddress);
        activePeers.remove(peerAddress);
        logger.info("Removed peer: {}", peerAddress);
    }

    public void markPeerActive(String peerAddress) {
        if (knownPeers.contains(peerAddress)) {
            activePeers.add(peerAddress);
        }
    }

    public void markPeerInactive(String peerAddress) {
        activePeers.remove(peerAddress);
        logger.warn("Marked peer as inactive: {}", peerAddress);
    }

    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 
            heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
        
        scheduler.scheduleAtFixedRate(this::checkPeerHealth,
            heartbeatTimeoutMs, heartbeatTimeoutMs / 2, TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeat() {
        // In a real implementation, this would send heartbeat messages to peers
        // For now, we assume peers are active if they're in the known list
        logger.debug("Sending heartbeat from node: {}", nodeId);
    }

    private void checkPeerHealth() {
        // In a real implementation, this would check last heartbeat time
        // and mark peers as inactive if timeout exceeded
        logger.debug("Checking peer health. Active peers: {}", activePeers.size());
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

