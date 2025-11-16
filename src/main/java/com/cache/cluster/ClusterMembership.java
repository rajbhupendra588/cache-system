package com.cache.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages cluster membership and peer discovery with active health monitoring.
 * 
 * <p>This class implements production-grade cluster membership management:
 * <ul>
 *   <li>Active heartbeat pinging to verify peer connectivity</li>
 *   <li>Automatic failure detection based on heartbeat timeouts</li>
 *   <li>Network connectivity monitoring</li>
 *   <li>Peer health status tracking</li>
 * </ul>
 */
@Component
public class ClusterMembership {
    private static final Logger logger = LoggerFactory.getLogger(ClusterMembership.class);
    
    private final String nodeId;
    private final Set<String> knownPeers = ConcurrentHashMap.newKeySet();
    private final Set<String> activePeers = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> lastHeartbeatTime = new ConcurrentHashMap<>();
    private final Map<String, Integer> consecutiveFailures = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private final long heartbeatIntervalMs;
    private final long heartbeatTimeoutMs;
    private final MessageSender messageSender;

    public ClusterMembership(String nodeId, Set<String> initialPeers, 
                           long heartbeatIntervalMs, long heartbeatTimeoutMs,
                           MessageSender messageSender) {
        this.nodeId = nodeId;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.heartbeatTimeoutMs = heartbeatTimeoutMs;
        this.messageSender = messageSender;
        
        if (initialPeers != null) {
            this.knownPeers.addAll(initialPeers);
            this.activePeers.addAll(initialPeers);
            // Initialize heartbeat tracking
            for (String peer : initialPeers) {
                lastHeartbeatTime.put(peer, System.currentTimeMillis());
                consecutiveFailures.put(peer, 0);
            }
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
        lastHeartbeatTime.put(peerAddress, System.currentTimeMillis());
        consecutiveFailures.put(peerAddress, 0);
        logger.info("Added peer: {}", peerAddress);
    }

    public void removePeer(String peerAddress) {
        knownPeers.remove(peerAddress);
        activePeers.remove(peerAddress);
        lastHeartbeatTime.remove(peerAddress);
        consecutiveFailures.remove(peerAddress);
        logger.info("Removed peer: {}", peerAddress);
    }
    
    public Map<String, Long> getLastHeartbeatTimes() {
        return Collections.unmodifiableMap(lastHeartbeatTime);
    }
    
    public Map<String, Integer> getConsecutiveFailures() {
        return Collections.unmodifiableMap(consecutiveFailures);
    }

    public void markPeerActive(String peerAddress) {
        if (knownPeers.contains(peerAddress)) {
            activePeers.add(peerAddress);
            lastHeartbeatTime.put(peerAddress, System.currentTimeMillis());
            consecutiveFailures.put(peerAddress, 0);
            logger.info("Marked peer as active: {}", peerAddress);
        }
    }

    public void markPeerInactive(String peerAddress) {
        if (activePeers.remove(peerAddress)) {
            logger.warn("Marked peer as inactive: {} (consecutive failures: {})", 
                peerAddress, consecutiveFailures.getOrDefault(peerAddress, 0));
        }
    }
    
    public void recordHeartbeatResponse(String peerAddress) {
        lastHeartbeatTime.put(peerAddress, System.currentTimeMillis());
        consecutiveFailures.put(peerAddress, 0);
        if (!activePeers.contains(peerAddress) && knownPeers.contains(peerAddress)) {
            markPeerActive(peerAddress);
        }
    }
    
    public void recordHeartbeatFailure(String peerAddress) {
        int failures = consecutiveFailures.merge(peerAddress, 1, Integer::sum);
        logger.debug("Heartbeat failure for peer {} (consecutive: {})", peerAddress, failures);
        
        // Mark inactive after 3 consecutive failures
        if (failures >= 3) {
            markPeerInactive(peerAddress);
        }
    }
    
    public boolean isPeerHealthy(String peerAddress) {
        if (!knownPeers.contains(peerAddress)) {
            return false;
        }
        
        Long lastHeartbeat = lastHeartbeatTime.get(peerAddress);
        if (lastHeartbeat == null) {
            return false;
        }
        
        long timeSinceLastHeartbeat = System.currentTimeMillis() - lastHeartbeat;
        return timeSinceLastHeartbeat < heartbeatTimeoutMs;
    }

    private void startHeartbeat() {
        // Send heartbeats to all known peers
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 
            heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
        
        // Check peer health based on last heartbeat time
        scheduler.scheduleAtFixedRate(this::checkPeerHealth,
            heartbeatTimeoutMs / 2, heartbeatTimeoutMs / 2, TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeat() {
        if (knownPeers.isEmpty()) {
            return;
        }
        
        HeartbeatMessage heartbeat = new HeartbeatMessage(nodeId);
        
        for (String peer : knownPeers) {
            try {
                messageSender.sendHeartbeat(peer, heartbeat);
                recordHeartbeatResponse(peer);
            } catch (Exception e) {
                logger.debug("Failed to send heartbeat to {}: {}", peer, e.getMessage());
                recordHeartbeatFailure(peer);
            }
        }
    }

    private void checkPeerHealth() {
        long now = System.currentTimeMillis();
        
        for (String peer : new java.util.HashSet<>(knownPeers)) {
            Long lastHeartbeat = lastHeartbeatTime.get(peer);
            if (lastHeartbeat == null) {
                continue;
            }
            
            long timeSinceLastHeartbeat = now - lastHeartbeat;
            
            if (timeSinceLastHeartbeat > heartbeatTimeoutMs) {
                if (activePeers.contains(peer)) {
                    logger.warn("Peer {} has not responded to heartbeat for {}ms (timeout: {}ms). Marking inactive.", 
                        peer, timeSinceLastHeartbeat, heartbeatTimeoutMs);
                    markPeerInactive(peer);
                }
            }
        }
        
        logger.debug("Health check complete. Active peers: {}/{}", 
            activePeers.size(), knownPeers.size());
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

