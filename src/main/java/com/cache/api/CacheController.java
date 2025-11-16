package com.cache.api;

import com.cache.config.CacheConfiguration;
import com.cache.core.CacheService;
import com.cache.core.CacheStats;
import com.cache.core.impl.InMemoryCacheManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.UUID;

/**
 * REST API for cache management operations.
 * Includes input validation and audit logging.
 * 
 * <p>This controller provides endpoints for:
 * <ul>
 *   <li>Listing and browsing caches</li>
 *   <li>Viewing cache statistics</li>
 *   <li>Invalidating cache entries</li>
 *   <li>Clearing caches</li>
 *   <li>Retrieving cache values</li>
 * </ul>
 * 
 * @author Cache System
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/cache")
@Tag(name = "Cache Management", description = "Cache management operations")
@SecurityRequirement(name = "basicAuth")
public class CacheController {
    private static final Logger logger = LoggerFactory.getLogger(CacheController.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
    
    private final CacheService cacheService;
    private final InMemoryCacheManager cacheManager;

    public CacheController(CacheService cacheService, InMemoryCacheManager cacheManager) {
        this.cacheService = cacheService;
        this.cacheManager = cacheManager;
    }

    /**
     * Lists all configured caches with their statistics.
     * 
     * @return map containing all caches and their statistics
     */
    @Operation(
        summary = "List all caches",
        description = "Returns a list of all configured caches with their current statistics including hits, misses, size, and memory usage."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Successfully retrieved cache list",
        content = @Content(schema = @Schema(implementation = Map.class))
    )
    @GetMapping
    public ResponseEntity<Map<String, Object>> listCaches() {
        try {
            String requestId = UUID.randomUUID().toString();
            MDC.put("requestId", requestId);
            logger.debug("Listing all caches");
            
            Map<String, Object> response = new HashMap<>();
            Set<String> cacheNames = cacheManager.getCacheNames();
            
            Map<String, CacheStats> stats = new HashMap<>();
            for (String cacheName : cacheNames) {
                stats.put(cacheName, cacheService.getStats(cacheName));
            }
            
            response.put("caches", stats);
            response.put("count", cacheNames.size());
            return ResponseEntity.ok(response);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Gets detailed information about a specific cache.
     * 
     * @param name the cache name
     * @return cache details including statistics and configuration
     */
    @Operation(
        summary = "Get cache details",
        description = "Returns detailed information about a specific cache including statistics and configuration."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cache details retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Cache not found")
    })
    @GetMapping("/{name}")
    public ResponseEntity<Map<String, Object>> getCacheDetails(
            @Parameter(description = "Cache name", required = true, example = "issue")
            @PathVariable String name) {
        CacheStats stats = cacheService.getStats(name);
        CacheConfiguration config = cacheManager.getConfiguration(name);
        
        Map<String, Object> response = new HashMap<>();
        response.put("name", name);
        response.put("stats", stats);
        response.put("configuration", config);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Lists keys in a cache with optional filtering and pagination.
     * 
     * @param name the cache name
     * @param prefix optional prefix to filter keys
     * @param limit maximum number of keys to return (default: 100)
     * @param offset pagination offset (default: 0)
     * @return paginated list of cache keys
     */
    @Operation(
        summary = "List cache keys",
        description = "Returns a paginated list of keys in the specified cache. Supports prefix filtering."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Keys retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Cache not found")
    })
    @GetMapping("/{name}/keys")
    public ResponseEntity<Map<String, Object>> listKeys(
            @Parameter(description = "Cache name", required = true, example = "issue")
            @PathVariable String name,
            @Parameter(description = "Key prefix filter (optional)", example = "issue:")
            @RequestParam(required = false) String prefix,
            @Parameter(description = "Maximum number of keys to return", example = "100")
            @RequestParam(defaultValue = "100") int limit,
            @Parameter(description = "Pagination offset", example = "0")
            @RequestParam(defaultValue = "0") int offset) {
        
        Collection<String> keys = cacheManager.getKeys(name, prefix);
        List<String> keyList = new ArrayList<>(keys);
        
        int end = Math.min(offset + limit, keyList.size());
        List<String> paginatedKeys = keyList.subList(Math.min(offset, keyList.size()), end);
        
        Map<String, Object> response = new HashMap<>();
        response.put("cache", name);
        response.put("total", keys.size());
        response.put("keys", paginatedKeys);
        response.put("offset", offset);
        response.put("limit", limit);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Invalidates cache entries by key, keys, or prefix.
     * 
     * @param name the cache name
     * @param request invalidation request containing key(s) or prefix
     * @return invalidation result with count of invalidated entries
     */
    @Operation(
        summary = "Invalidate cache entries",
        description = "Invalidates cache entries by key, multiple keys, or prefix. Supports single key, multiple keys, or prefix-based invalidation."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Invalidation completed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "Cache not found")
    })
    @PostMapping("/{name}/invalidate")
    public ResponseEntity<Map<String, Object>> invalidate(
            @Parameter(description = "Cache name", required = true, example = "issue")
            @PathVariable @NotBlank @Size(max = 100) String name,
            @Parameter(description = "Invalidation request", required = true)
            @RequestBody @Valid InvalidateRequest request) {
        
        // Validate cache exists
        if (!cacheManager.getCacheNames().contains(name)) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Cache not found: " + name));
        }
        
        // Validate request
        if (request.getKeys() != null && request.getKeys().size() > 1000) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Too many keys (maximum 1000 allowed)"));
        }
        
        int invalidatedCount = 0;
        try {
            if (request.getKeys() != null && !request.getKeys().isEmpty()) {
                for (String key : request.getKeys()) {
                    cacheService.invalidate(name, key);
                    invalidatedCount++;
                }
                auditLogger.info("Invalidated {} keys in cache {}: {}", invalidatedCount, name, request.getKeys());
            } else if (request.getKey() != null) {
                cacheService.invalidate(name, request.getKey());
                invalidatedCount = 1;
                auditLogger.info("Invalidated key {} in cache {}", request.getKey(), name);
            } else if (request.getPrefix() != null) {
                cacheManager.invalidateByPrefix(name, request.getPrefix());
                auditLogger.info("Invalidated keys with prefix {} in cache {}", request.getPrefix(), name);
            } else {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Must provide 'key', 'keys', or 'prefix'"));
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Invalidation completed");
            response.put("invalidatedCount", invalidatedCount);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error invalidating cache", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to invalidate cache: " + e.getMessage()));
        }
    }

    /**
     * Clears all entries from a cache.
     * 
     * @param name the cache name
     * @return clear operation result
     */
    @Operation(
        summary = "Clear cache",
        description = "Removes all entries from the specified cache. This operation is logged in the audit trail."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cache cleared successfully"),
        @ApiResponse(responseCode = "404", description = "Cache not found")
    })
    @PostMapping("/{name}/clear")
    public ResponseEntity<Map<String, Object>> clearCache(
            @Parameter(description = "Cache name", required = true, example = "issue")
            @PathVariable @NotBlank @Size(max = 100) String name) {
        
        // Validate cache exists
        if (!cacheManager.getCacheNames().contains(name)) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Cache not found: " + name));
        }
        
        try {
            cacheService.invalidateAll(name);
            auditLogger.warn("Cache {} cleared by admin", name);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Cache cleared");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error clearing cache", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to clear cache: " + e.getMessage()));
        }
    }

    @PostMapping("/{name}/config")
    public ResponseEntity<Map<String, Object>> updateConfig(
            @PathVariable @NotBlank @Size(max = 100) String name,
            @RequestBody @Valid CacheConfiguration config) {
        
        try {
            // Validate configuration
            if (config.getMaxEntries() < 1) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "maxEntries must be at least 1"));
            }
            if (config.getMemoryCapBytes() < 1024) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "memoryCapBytes must be at least 1024"));
            }
            
            cacheManager.configureCache(name, config);
            auditLogger.info("Configuration updated for cache {}: {}", name, config);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Configuration updated");
            response.put("configuration", config);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating configuration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to update configuration: " + e.getMessage()));
        }
    }

    /**
     * Gets statistics for a cache.
     * 
     * @param name the cache name
     * @return cache statistics
     */
    @Operation(
        summary = "Get cache statistics",
        description = "Returns comprehensive statistics for a cache including hits, misses, evictions, size, and memory usage."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Cache not found")
    })
    @GetMapping("/{name}/stats")
    public ResponseEntity<CacheStats> getStats(
            @Parameter(description = "Cache name", required = true, example = "issue")
            @PathVariable String name) {
        CacheStats stats = cacheService.getStats(name);
        return ResponseEntity.ok(stats);
    }

    /**
     * Retrieves a cached value by key.
     * 
     * @param name the cache name
     * @param key the cache key
     * @return the cached value if found
     */
    @Operation(
        summary = "Get cache value",
        description = "Retrieves the value stored for a specific key in the cache. Returns the value if found, or indicates if the key is not found or expired."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Value retrieved successfully (may be null if not found)"),
        @ApiResponse(responseCode = "400", description = "Invalid cache name or key"),
        @ApiResponse(responseCode = "404", description = "Cache not found")
    })
    @GetMapping("/{name}/keys/{key}")
    public ResponseEntity<Map<String, Object>> getValue(
            @Parameter(description = "Cache name", required = true, example = "issue")
            @PathVariable @NotBlank String name,
            @Parameter(description = "Cache key", required = true, example = "issue:123")
            @PathVariable @NotBlank @Size(max = 500) String key) {
        
        // Validate cache exists
        if (!cacheManager.getCacheNames().contains(name)) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Cache not found: " + name));
        }
        
        try {
            Optional<Object> value = cacheService.get(name, key, Object.class);
            
            if (value.isPresent()) {
                Map<String, Object> response = new HashMap<>();
                response.put("cache", name);
                response.put("key", key);
                response.put("value", value.get());
                response.put("found", true);
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.ok(Map.of(
                    "cache", name,
                    "key", key,
                    "found", false,
                    "message", "Key not found or expired"
                ));
            }
        } catch (Exception e) {
            logger.error("Error retrieving cache value", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to retrieve value: " + e.getMessage()));
        }
    }

    // Request DTOs
    public static class InvalidateRequest {
        @Schema(description = "Single key to invalidate", example = "issue:123", maxLength = 500)
        @Size(max = 500, message = "Key too long (max 500 characters)")
        private String key;
        
        @Schema(description = "Multiple keys to invalidate (max 1000)", example = "[\"issue:123\", \"issue:456\"]", maxLength = 1000)
        @Size(max = 1000, message = "Too many keys (max 1000)")
        private List<@Size(max = 500) String> keys;
        
        @Schema(description = "Key prefix to invalidate all matching keys", example = "issue:", maxLength = 500)
        @Size(max = 500, message = "Prefix too long (max 500 characters)")
        private String prefix;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public List<String> getKeys() {
            return keys;
        }

        public void setKeys(List<String> keys) {
            this.keys = keys;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }
    }
}

