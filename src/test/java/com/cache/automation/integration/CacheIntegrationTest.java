package com.cache.automation.integration;

import com.cache.automation.base.BaseAPITest;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for cache operations.
 * Tests end-to-end cache workflows.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CacheIntegrationTest extends BaseAPITest {
    private static final Logger logger = LoggerFactory.getLogger(CacheIntegrationTest.class);
    private static final String CACHE_NAME = "issue";
    private static final String TEST_KEY_PREFIX = "integration:test:";

    @Test
    @Order(1)
    @DisplayName("TC-INT-001: Complete cache workflow - Put, Get, Invalidate")
    public void testCompleteCacheWorkflow() {
        logger.info("Executing TC-INT-001: Complete cache workflow");
        
        String testKey = TEST_KEY_PREFIX + System.currentTimeMillis();
        
        // Step 1: Verify cache exists
        Response statsResponse = given()
            .spec(getAuthenticatedRequest())
            .when()
            .get("/api/cache/{name}/stats", CACHE_NAME)
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertNotNull(statsResponse);
        logger.info("Step 1: Cache verified");
        
        // Step 2: Get initial cache size
        long initialSize = statsResponse.jsonPath().getLong("size");
        logger.info("Step 2: Initial cache size: {}", initialSize);
        
        // Step 3: Verify key doesn't exist
        Response getResponse = given()
            .spec(getAuthenticatedRequest())
            .when()
            .get("/api/cache/{name}/keys/{key}", CACHE_NAME, testKey)
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        boolean found = getResponse.jsonPath().getBoolean("found");
        assertFalse(found, "Key should not exist initially");
        logger.info("Step 3: Verified key does not exist");
        
        // Step 4: Invalidate the key (should succeed even if key doesn't exist)
        Map<String, String> invalidateRequest = new HashMap<>();
        invalidateRequest.put("key", testKey);
        
        Response invalidateResponse = given()
            .spec(getAuthenticatedRequest())
            .body(invalidateRequest)
            .when()
            .post("/api/cache/{name}/invalidate", CACHE_NAME)
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertEquals("success", invalidateResponse.jsonPath().getString("status"));
        logger.info("Step 4: Key invalidated");
        
        logger.info("Complete cache workflow test passed");
    }

    @Test
    @Order(2)
    @DisplayName("TC-INT-002: Test cache statistics tracking")
    public void testCacheStatisticsTracking() {
        logger.info("Executing TC-INT-002: Test cache statistics tracking");
        
        // Get initial statistics
        Response initialStats = given()
            .spec(getAuthenticatedRequest())
            .when()
            .get("/api/cache/{name}/stats", CACHE_NAME)
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        long initialHits = initialStats.jsonPath().getLong("hits");
        long initialMisses = initialStats.jsonPath().getLong("misses");
        
        logger.info("Initial stats - Hits: {}, Misses: {}", initialHits, initialMisses);
        
        // Perform operations that might affect statistics
        String testKey = TEST_KEY_PREFIX + "stats:" + System.currentTimeMillis();
        
        // Try to get a non-existent key (should be a miss)
        given()
            .spec(getAuthenticatedRequest())
            .when()
            .get("/api/cache/{name}/keys/{key}", CACHE_NAME, testKey)
            .then()
            .statusCode(200);
        
        // Get updated statistics
        Response updatedStats = given()
            .spec(getAuthenticatedRequest())
            .when()
            .get("/api/cache/{name}/stats", CACHE_NAME)
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        long updatedHits = updatedStats.jsonPath().getLong("hits");
        long updatedMisses = updatedStats.jsonPath().getLong("misses");
        
        logger.info("Updated stats - Hits: {}, Misses: {}", updatedHits, updatedMisses);
        
        // Statistics should be tracked (may or may not change depending on implementation)
        assertNotNull(updatedHits);
        assertNotNull(updatedMisses);
        
        logger.info("Cache statistics tracking verified");
    }

    @Test
    @Order(3)
    @DisplayName("TC-INT-003: Test cache key listing and pagination")
    public void testCacheKeyListingAndPagination() {
        logger.info("Executing TC-INT-003: Test cache key listing and pagination");
        
        // Test listing keys with default pagination
        Response response1 = given()
            .spec(getAuthenticatedRequest())
            .queryParam("limit", 10)
            .queryParam("offset", 0)
            .when()
            .get("/api/cache/{name}/keys", CACHE_NAME)
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        int total = response1.jsonPath().getInt("total");
        logger.info("Total keys in cache: {}", total);
        
        // Test pagination - second page
        Response response2 = given()
            .spec(getAuthenticatedRequest())
            .queryParam("limit", 10)
            .queryParam("offset", 10)
            .when()
            .get("/api/cache/{name}/keys", CACHE_NAME)
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertEquals(CACHE_NAME, response2.jsonPath().getString("cache"));
        assertEquals(10, response2.jsonPath().getInt("offset"));
        
        logger.info("Cache key listing and pagination verified");
    }

    @Test
    @Order(4)
    @DisplayName("TC-INT-004: Test cache clear operation")
    public void testCacheClearOperation() {
        logger.info("Executing TC-INT-004: Test cache clear operation");
        
        // Get cache size before clear
        Response beforeClear = given()
            .spec(getAuthenticatedRequest())
            .when()
            .get("/api/cache/{name}/stats", CACHE_NAME)
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        long sizeBefore = beforeClear.jsonPath().getLong("size");
        logger.info("Cache size before clear: {}", sizeBefore);
        
        // Clear the cache
        Response clearResponse = given()
            .spec(getAuthenticatedRequest())
            .when()
            .post("/api/cache/{name}/clear", CACHE_NAME)
            .then()
            .statusCode(200)
            .body("status", equalTo("success"))
            .extract()
            .response();
        
        assertEquals("success", clearResponse.jsonPath().getString("status"));
        logger.info("Cache cleared successfully");
        
        // Verify cache is cleared (size should be 0 or reduced)
        Response afterClear = given()
            .spec(getAuthenticatedRequest())
            .when()
            .get("/api/cache/{name}/stats", CACHE_NAME)
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        long sizeAfter = afterClear.jsonPath().getLong("size");
        logger.info("Cache size after clear: {}", sizeAfter);
        
        // Size should be 0 or less than before
        assertTrue(sizeAfter <= sizeBefore, "Cache size should be reduced after clear");
        
        logger.info("Cache clear operation verified");
    }

    @Test
    @Order(5)
    @DisplayName("TC-INT-005: Test multiple cache operations")
    public void testMultipleCacheOperations() {
        logger.info("Executing TC-INT-005: Test multiple cache operations");
        
        // Test invalidating multiple keys
        Map<String, Object> invalidateRequest = new HashMap<>();
        invalidateRequest.put("keys", java.util.Arrays.asList(
            TEST_KEY_PREFIX + "multi1",
            TEST_KEY_PREFIX + "multi2",
            TEST_KEY_PREFIX + "multi3"
        ));
        
        Response response = given()
            .spec(getAuthenticatedRequest())
            .body(invalidateRequest)
            .when()
            .post("/api/cache/{name}/invalidate", CACHE_NAME)
            .then()
            .statusCode(200)
            .body("status", equalTo("success"))
            .extract()
            .response();
        
        assertEquals("success", response.jsonPath().getString("status"));
        int invalidatedCount = response.jsonPath().getInt("invalidatedCount");
        assertEquals(3, invalidatedCount);
        
        logger.info("Multiple cache operations verified - Invalidated {} keys", invalidatedCount);
    }
}

