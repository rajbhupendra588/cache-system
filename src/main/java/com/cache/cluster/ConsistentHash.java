package com.cache.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Consistent hashing implementation for data sharding across cluster nodes.
 * 
 * <p>This class provides production-grade consistent hashing with:
 * <ul>
 *   <li>Virtual nodes for better distribution</li>
 *   <li>Thread-safe operations</li>
 *   <li>Automatic rebalancing when nodes join/leave</li>
 *   <li>Efficient key-to-node mapping</li>
 * </ul>
 * 
 * @author Cache System
 * @since 1.0.0
 */
public class ConsistentHash {
    private static final int VIRTUAL_NODES_PER_NODE = 150;
    private static final Logger logger = LoggerFactory.getLogger(ConsistentHash.class);
    
    private final TreeMap<Long, String> ring = new TreeMap<>();
    private final Map<String, Set<Long>> nodeToVirtualNodes = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final MessageDigest md5;
    
    public ConsistentHash() {
        try {
            this.md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
    
    /**
     * Adds a node to the consistent hash ring.
     * 
     * @param nodeId the node identifier
     */
    public void addNode(String nodeId) {
        lock.writeLock().lock();
        try {
            if (nodeToVirtualNodes.containsKey(nodeId)) {
                logger.debug("Node {} already in ring", nodeId);
                return;
            }
            
            Set<Long> virtualNodes = new HashSet<>();
            for (int i = 0; i < VIRTUAL_NODES_PER_NODE; i++) {
                String virtualNodeName = nodeId + "#" + i;
                long hash = hash(virtualNodeName);
                ring.put(hash, nodeId);
                virtualNodes.add(hash);
            }
            
            nodeToVirtualNodes.put(nodeId, virtualNodes);
            logger.info("Added node {} to consistent hash ring ({} virtual nodes)", nodeId, VIRTUAL_NODES_PER_NODE);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Removes a node from the consistent hash ring.
     * 
     * @param nodeId the node identifier
     */
    public void removeNode(String nodeId) {
        lock.writeLock().lock();
        try {
            Set<Long> virtualNodes = nodeToVirtualNodes.remove(nodeId);
            if (virtualNodes == null) {
                logger.debug("Node {} not in ring", nodeId);
                return;
            }
            
            for (Long hash : virtualNodes) {
                ring.remove(hash);
            }
            
            logger.info("Removed node {} from consistent hash ring", nodeId);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Gets the node responsible for a given key.
     * 
     * @param key the cache key
     * @return the node identifier responsible for this key
     */
    public String getNode(String key) {
        lock.readLock().lock();
        try {
            if (ring.isEmpty()) {
                return null;
            }
            
            long hash = hash(key);
            Map.Entry<Long, String> entry = ring.ceilingEntry(hash);
            
            // Wrap around if no entry found (circular ring)
            if (entry == null) {
                entry = ring.firstEntry();
            }
            
            return entry.getValue();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets all nodes responsible for a key (for replication).
     * 
     * @param key the cache key
     * @param replicationFactor number of nodes to return
     * @return list of node identifiers
     */
    public List<String> getNodes(String key, int replicationFactor) {
        lock.readLock().lock();
        try {
            if (ring.isEmpty()) {
                return Collections.emptyList();
            }
            
            long hash = hash(key);
            Set<String> nodes = new LinkedHashSet<>();
            NavigableMap<Long, String> tailMap = ring.tailMap(hash, true);
            
            // Collect nodes from tail
            for (String node : tailMap.values()) {
                if (nodes.size() >= replicationFactor) {
                    break;
                }
                nodes.add(node);
            }
            
            // Wrap around if needed
            if (nodes.size() < replicationFactor) {
                for (String node : ring.values()) {
                    if (nodes.size() >= replicationFactor) {
                        break;
                    }
                    nodes.add(node);
                }
            }
            
            return new ArrayList<>(nodes);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets all nodes in the ring.
     * 
     * @return set of node identifiers
     */
    public Set<String> getAllNodes() {
        lock.readLock().lock();
        try {
            return new HashSet<>(nodeToVirtualNodes.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Checks if a node is in the ring.
     * 
     * @param nodeId the node identifier
     * @return true if the node is in the ring
     */
    public boolean containsNode(String nodeId) {
        lock.readLock().lock();
        try {
            return nodeToVirtualNodes.containsKey(nodeId);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Calculates MD5 hash of a string.
     * 
     * @param input the input string
     * @return the hash value
     */
    private long hash(String input) {
        synchronized (md5) {
            md5.reset();
            byte[] digest = md5.digest(input.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.wrap(digest);
            return buffer.getLong();
        }
    }
}

