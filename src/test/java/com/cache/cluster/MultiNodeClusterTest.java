package com.cache.cluster;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for multi-node cluster functionality.
 */
public class MultiNodeClusterTest {
    
    private ConsistentHash consistentHash;
    
    @BeforeEach
    void setUp() {
        consistentHash = new ConsistentHash();
    }
    
    @Test
    void testConsistentHashAddNode() {
        consistentHash.addNode("node-1");
        consistentHash.addNode("node-2");
        consistentHash.addNode("node-3");
        
        assertTrue(consistentHash.containsNode("node-1"));
        assertTrue(consistentHash.containsNode("node-2"));
        assertTrue(consistentHash.containsNode("node-3"));
        assertEquals(3, consistentHash.getAllNodes().size());
    }
    
    @Test
    void testConsistentHashRemoveNode() {
        consistentHash.addNode("node-1");
        consistentHash.addNode("node-2");
        
        consistentHash.removeNode("node-1");
        
        assertFalse(consistentHash.containsNode("node-1"));
        assertTrue(consistentHash.containsNode("node-2"));
        assertEquals(1, consistentHash.getAllNodes().size());
    }
    
    @Test
    void testConsistentHashKeyMapping() {
        consistentHash.addNode("node-1");
        consistentHash.addNode("node-2");
        consistentHash.addNode("node-3");
        
        String node1 = consistentHash.getNode("key1");
        String node2 = consistentHash.getNode("key2");
        String node3 = consistentHash.getNode("key3");
        
        assertNotNull(node1);
        assertNotNull(node2);
        assertNotNull(node3);
        assertTrue(consistentHash.getAllNodes().contains(node1));
        assertTrue(consistentHash.getAllNodes().contains(node2));
        assertTrue(consistentHash.getAllNodes().contains(node3));
    }
    
    @Test
    void testConsistentHashReplication() {
        consistentHash.addNode("node-1");
        consistentHash.addNode("node-2");
        consistentHash.addNode("node-3");
        
        var nodes = consistentHash.getNodes("test-key", 2);
        
        assertEquals(2, nodes.size());
        assertTrue(consistentHash.getAllNodes().containsAll(nodes));
    }
    
    @Test
    void testConsistentHashConsistency() {
        consistentHash.addNode("node-1");
        consistentHash.addNode("node-2");
        consistentHash.addNode("node-3");
        
        // Same key should map to same node
        String node1 = consistentHash.getNode("consistent-key");
        String node2 = consistentHash.getNode("consistent-key");
        
        assertEquals(node1, node2);
    }
    
    @Test
    void testConsistentHashRebalancing() {
        consistentHash.addNode("node-1");
        consistentHash.addNode("node-2");
        
        // Get node before adding new node
        consistentHash.getNode("test-key");
        
        consistentHash.addNode("node-3");
        
        // Key should still map to a valid node after rebalancing
        String nodeAfter = consistentHash.getNode("test-key");
        assertNotNull(nodeAfter);
        assertTrue(consistentHash.getAllNodes().contains(nodeAfter));
    }
}

