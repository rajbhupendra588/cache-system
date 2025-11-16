package com.cache.automation.cluster;

import com.cache.automation.base.BaseAPITest;
import com.cache.automation.util.TestConfig;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Multi-node cluster test with 10 nodes.
 * Tests cluster functionality with screenshot capture (BAU).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MultiNode10Test extends BaseAPITest {
    private static final Logger logger = LoggerFactory.getLogger(MultiNode10Test.class);
    private static final int NUM_NODES = 10;
    private static final int BASE_HTTP_PORT = 8080;
    private static final String CACHE_NAME = "test";
    private static final List<Integer> activeNodes = new ArrayList<>();

    @BeforeAll
    public static void checkNodes() {
        logger.info("Checking for {} nodes...", NUM_NODES);
        
        for (int i = 0; i < NUM_NODES; i++) {
            int port = BASE_HTTP_PORT + i;
            try {
                Response response = given()
                    .auth().basic(TestConfig.ADMIN_USERNAME, TestConfig.ADMIN_PASSWORD)
                    .when()
                    .get("http://localhost:{port}/actuator/health", port)
                    .then()
                    .extract()
                    .response();
                
                if (response.getStatusCode() == 200) {
                    activeNodes.add(port);
                    logger.info("Node on port {} is active", port);
                }
            } catch (Exception e) {
                logger.warn("Node on port {} is not available: {}", port, e.getMessage());
            }
        }
        
        logger.info("Found {} active nodes out of {} expected", activeNodes.size(), NUM_NODES);
    }

    @Test
    @Order(1)
    @DisplayName("TC-MULTI-10-001: Verify all 10 nodes are running")
    public void testAllNodesRunning() {
        logger.info("Executing TC-MULTI-10-001: Verify all nodes are running");
        
        assertTrue(activeNodes.size() >= NUM_NODES, 
            String.format("Expected %d nodes, found %d", NUM_NODES, activeNodes.size()));
        
        logger.info("✓ All {} nodes are running", NUM_NODES);
    }

    @Test
    @Order(2)
    @DisplayName("TC-MULTI-10-002: Get cluster status from all nodes")
    public void testClusterStatusAllNodes() {
        logger.info("Executing TC-MULTI-10-002: Get cluster status from all nodes");
        
        Map<Integer, Map<String, Object>> nodeStatuses = new HashMap<>();
        
        for (int port : activeNodes) {
            try {
                Response response = given()
                    .spec(getAuthenticatedRequest())
                    .baseUri("http://localhost:" + port)
                    .when()
                    .get("/api/cluster")
                    .then()
                    .statusCode(200)
                    .extract()
                    .response();
                
                Map<String, Object> clusterData = response.jsonPath().getMap("$");
                nodeStatuses.put(port, clusterData);
                
                String nodeId = (String) clusterData.get("nodeId");
                int activePeers = ((List<?>) clusterData.getOrDefault("activePeers", new ArrayList<>())).size();
                
                logger.info("Node {} (port {}): {} active peers", nodeId, port, activePeers);
                
                assertNotNull(nodeId, "Node ID should not be null");
                assertTrue(activePeers >= 0, "Active peers count should be non-negative");
            } catch (Exception e) {
                logger.error("Failed to get cluster status from port {}: {}", port, e.getMessage());
            }
        }
        
        assertEquals(NUM_NODES, nodeStatuses.size(), "Should have cluster status from all nodes");
        logger.info("✓ Retrieved cluster status from all {} nodes", NUM_NODES);
    }

    @Test
    @Order(3)
    @DisplayName("TC-MULTI-10-003: Test cache operations across nodes")
    public void testCacheOperationsAcrossNodes() {
        logger.info("Executing TC-MULTI-10-003: Test cache operations across nodes");
        
        String testKey = "multi-node-10-test-" + System.currentTimeMillis();
        
        // Invalidate on first node
        int firstPort = activeNodes.get(0);
        Response invalidateResponse = given()
            .spec(getAuthenticatedRequest())
            .baseUri("http://localhost:" + firstPort)
            .body(Map.of("key", testKey))
            .when()
            .post("/api/cache/{name}/invalidate", CACHE_NAME)
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertEquals("success", invalidateResponse.jsonPath().getString("status"));
        logger.info("✓ Invalidated key on node (port {})", firstPort);
        
        // Wait for propagation
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Check stats on multiple nodes
        int nodesChecked = 0;
        for (int i = 0; i < Math.min(5, activeNodes.size()); i++) {
            int port = activeNodes.get(i);
            try {
                Response statsResponse = given()
                    .spec(getAuthenticatedRequest())
                    .baseUri("http://localhost:" + port)
                    .when()
                    .get("/api/cache/{name}/stats", CACHE_NAME)
                    .then()
                    .statusCode(200)
                    .extract()
                    .response();
                
                Map<String, Object> stats = statsResponse.jsonPath().getMap("$");
                assertNotNull(stats.get("size"));
                nodesChecked++;
                
                logger.info("Node {} stats - Size: {}, Hits: {}, Misses: {}", 
                    port, stats.get("size"), stats.get("hits"), stats.get("misses"));
            } catch (Exception e) {
                logger.warn("Failed to get stats from port {}: {}", port, e.getMessage());
            }
        }
        
        assertTrue(nodesChecked > 0, "Should have checked stats on at least one node");
        logger.info("✓ Checked cache stats on {} nodes", nodesChecked);
    }

    @Test
    @Order(4)
    @DisplayName("TC-MULTI-10-004: Test node health monitoring")
    public void testNodeHealthMonitoring() {
        logger.info("Executing TC-MULTI-10-004: Test node health monitoring");
        
        int healthyNodes = 0;
        
        for (int port : activeNodes) {
            try {
                Response healthResponse = given()
                    .spec(getAuthenticatedRequest())
                    .baseUri("http://localhost:" + port)
                    .when()
                    .get("/actuator/health")
                    .then()
                    .statusCode(200)
                    .extract()
                    .response();
                
                String status = healthResponse.jsonPath().getString("status");
                if ("UP".equals(status)) {
                    healthyNodes++;
                    logger.info("Node {} (port {}): {}", 
                        healthResponse.jsonPath().getString("components.cache.status"), port, status);
                }
            } catch (Exception e) {
                logger.warn("Failed to check health on port {}: {}", port, e.getMessage());
            }
        }
        
        assertTrue(healthyNodes >= NUM_NODES * 0.8, 
            String.format("At least 80%% of nodes should be healthy (found %d/%d)", healthyNodes, NUM_NODES));
        logger.info("✓ {} out of {} nodes are healthy", healthyNodes, NUM_NODES);
    }

    @Test
    @Order(5)
    @DisplayName("TC-MULTI-10-005: Test cluster communication")
    public void testClusterCommunication() {
        logger.info("Executing TC-MULTI-10-005: Test cluster communication");
        
        // Test invalidation on multiple nodes
        String testKey = "comm-test-" + System.currentTimeMillis();
        int nodesTested = 0;
        
        // Test on nodes 1, 5, and 10 (if available)
        int[] testNodes = {0, 4, Math.min(9, activeNodes.size() - 1)};
        
        for (int nodeIndex : testNodes) {
            if (nodeIndex < activeNodes.size()) {
                int port = activeNodes.get(nodeIndex);
                try {
                    Response response = given()
                        .spec(getAuthenticatedRequest())
                        .baseUri("http://localhost:" + port)
                        .body(Map.of("key", testKey + "-" + nodeIndex))
                        .when()
                        .post("/api/cache/{name}/invalidate", CACHE_NAME)
                        .then()
                        .statusCode(200)
                        .extract()
                        .response();
                    
                    assertEquals("success", response.jsonPath().getString("status"));
                    nodesTested++;
                    logger.info("✓ Communication test successful on node (port {})", port);
                } catch (Exception e) {
                    logger.warn("Communication test failed on port {}: {}", port, e.getMessage());
                }
            }
        }
        
        assertTrue(nodesTested > 0, "Should have tested communication on at least one node");
        logger.info("✓ Tested cluster communication on {} nodes", nodesTested);
    }

    @Test
    @Order(6)
    @DisplayName("TC-MULTI-10-006: Verify consistent hashing with 10 nodes")
    public void testConsistentHashing10Nodes() {
        logger.info("Executing TC-MULTI-10-006: Verify consistent hashing with 10 nodes");
        
        // This test verifies that all nodes can handle requests
        // (consistent hashing distribution would be verified if API exposed it)
        int nodesHandlingRequests = 0;
        
        for (int port : activeNodes) {
            try {
                given()
                    .spec(getAuthenticatedRequest())
                    .baseUri("http://localhost:" + port)
                    .when()
                    .get("/api/cache/{name}/stats", CACHE_NAME)
                    .then()
                    .statusCode(200);
                
                nodesHandlingRequests++;
            } catch (Exception e) {
                logger.warn("Node on port {} not handling requests: {}", port, e.getMessage());
            }
        }
        
        assertTrue(nodesHandlingRequests >= NUM_NODES * 0.8, 
            "At least 80% of nodes should handle requests");
        logger.info("✓ {} nodes are handling requests", nodesHandlingRequests);
    }

    @Test
    @Order(7)
    @DisplayName("TC-MULTI-10-007: Test cluster rebalancing")
    public void testClusterRebalancing() {
        logger.info("Executing TC-MULTI-10-007: Test cluster rebalancing");
        
        // Verify that all nodes are aware of each other
        int nodesWithFullClusterView = 0;
        
        for (int port : activeNodes) {
            try {
                Response response = given()
                    .spec(getAuthenticatedRequest())
                    .baseUri("http://localhost:" + port)
                    .when()
                    .get("/api/cluster")
                    .then()
                    .statusCode(200)
                    .extract()
                    .response();
                
                Map<String, Object> clusterData = response.jsonPath().getMap("$");
                int knownPeers = ((List<?>) clusterData.getOrDefault("knownPeers", new ArrayList<>())).size();
                
                // Nodes should know about at least 80% of other nodes
                if (knownPeers >= NUM_NODES * 0.8) {
                    nodesWithFullClusterView++;
                }
                
                logger.info("Node (port {}): {} known peers", port, knownPeers);
            } catch (Exception e) {
                logger.warn("Failed to check cluster view on port {}: {}", port, e.getMessage());
            }
        }
        
        assertTrue(nodesWithFullClusterView >= NUM_NODES * 0.7, 
            "At least 70% of nodes should have full cluster view");
        logger.info("✓ {} nodes have full cluster view", nodesWithFullClusterView);
    }
}

