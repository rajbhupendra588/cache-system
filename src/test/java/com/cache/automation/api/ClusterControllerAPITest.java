package com.cache.automation.api;

import com.cache.automation.base.BaseAPITest;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * API automation tests for ClusterController endpoints.
 * Tests cluster management and node operations.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ClusterControllerAPITest extends BaseAPITest {
    private static final Logger logger = LoggerFactory.getLogger(ClusterControllerAPITest.class);

    @Test
    @Order(1)
    @DisplayName("TC-CLUSTER-001: Get cluster status")
    public void testGetClusterStatus() {
        logger.info("Executing TC-CLUSTER-001: Get cluster status");
        
        Response response = given()
            .spec(getAuthenticatedRequest())
            .when()
            .get("/api/cluster")
            .then()
            .statusCode(200)
            .body("nodeId", notNullValue())
            .body("activePeers", notNullValue())
            .body("knownPeers", notNullValue())
            .body("activePeerCount", notNullValue())
            .extract()
            .response();

        Map<String, Object> responseBody = response.jsonPath().getMap("$");
        assertNotNull(responseBody.get("nodeId"));
        assertNotNull(responseBody.get("activePeers"));
        assertNotNull(responseBody.get("knownPeers"));
        
        logger.info("Cluster status - Node ID: {}, Active Peers: {}, Known Peers: {}", 
            responseBody.get("nodeId"), 
            responseBody.get("activePeers"), 
            responseBody.get("knownPeers"));
    }

    @Test
    @Order(2)
    @DisplayName("TC-CLUSTER-002: List cluster nodes")
    public void testListClusterNodes() {
        logger.info("Executing TC-CLUSTER-002: List cluster nodes");
        
        Response response = given()
            .spec(getAuthenticatedRequest())
            .when()
            .get("/api/cluster/nodes")
            .then()
            .statusCode(200)
            .body("currentNode", notNullValue())
            .body("nodes", notNullValue())
            .extract()
            .response();

        Map<String, Object> responseBody = response.jsonPath().getMap("$");
        assertNotNull(responseBody.get("currentNode"));
        assertNotNull(responseBody.get("nodes"));
        
        logger.info("Current node: {}, All nodes: {}", 
            responseBody.get("currentNode"), 
            responseBody.get("nodes"));
    }

    @Test
    @Order(3)
    @DisplayName("TC-CLUSTER-003: Get node details")
    public void testGetNodeDetails() {
        logger.info("Executing TC-CLUSTER-003: Get node details");
        
        // First get cluster status to get a node ID
        Response clusterResponse = given()
            .spec(getAuthenticatedRequest())
            .when()
            .get("/api/cluster")
            .then()
            .statusCode(200)
            .extract()
            .response();

        String nodeId = clusterResponse.jsonPath().getString("nodeId");
        assertNotNull(nodeId);
        
        // Get node details
        Response response = given()
            .spec(getAuthenticatedRequest())
            .when()
            .get("/api/cluster/nodes/{nodeId}", nodeId)
            .then()
            .statusCode(200)
            .body("nodeId", equalTo(nodeId))
            .body("isCurrentNode", notNullValue())
            .body("isActive", notNullValue())
            .extract()
            .response();

        Map<String, Object> responseBody = response.jsonPath().getMap("$");
        assertEquals(nodeId, responseBody.get("nodeId"));
        assertNotNull(responseBody.get("isCurrentNode"));
        assertNotNull(responseBody.get("isActive"));
        
        logger.info("Node details - ID: {}, Is Current: {}, Is Active: {}", 
            responseBody.get("nodeId"),
            responseBody.get("isCurrentNode"),
            responseBody.get("isActive"));
    }

    @Test
    @Order(4)
    @DisplayName("TC-CLUSTER-004: Test cluster authentication")
    public void testClusterAuthentication() {
        logger.info("Executing TC-CLUSTER-004: Test cluster authentication");
        
        given()
            .auth().basic("wronguser", "wrongpass")
            .when()
            .get("/api/cluster")
            .then()
            .statusCode(401);
        
        logger.info("Cluster endpoint authentication verified");
    }
}

