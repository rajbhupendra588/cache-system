package com.cache.examples;

import com.cache.core.CacheService;
import com.cache.core.CacheStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

/**
 * Example showing how to use the cache system as a library in your application.
 * 
 * This demonstrates:
 * 1. Injecting CacheService
 * 2. Using getOrLoad for automatic caching
 * 3. Manual get/put operations
 * 4. Cache invalidation after updates
 * 5. Monitoring cache statistics
 */
@Service
public class LibraryUsageExample {
    
    @Autowired
    private CacheService cacheService;
    
    // Inject your repositories
    // @Autowired
    // private IssueRepository issueRepository;
    
    /**
     * Example 1: Basic usage with getOrLoad (RECOMMENDED)
     * 
     * This is the simplest and most common pattern.
     * The loader function only executes on cache miss.
     */
    public Issue getIssue(Long issueId) {
        String cacheKey = "issue:" + issueId;
        
        return cacheService.getOrLoad(
            "issue",                    // Cache name (must match config in application.yml)
            cacheKey,                   // Cache key
            () -> {
                // This loader function only executes on cache miss
                // Load from database (your existing slow method)
                return loadIssueFromDatabase(issueId);
            },
            Duration.ofMinutes(30)      // TTL (optional - uses config default if null)
        );
    }
    
    /**
     * Example 2: Manual get/put pattern
     * 
     * Use this when you need more control over caching logic.
     */
    public Issue getIssueManual(Long issueId) {
        String cacheKey = "issue:" + issueId;
        
        // Try cache first
        Optional<Issue> cached = cacheService.get("issue", cacheKey, Issue.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        
        // Cache miss - load from database
        Issue issue = loadIssueFromDatabase(issueId);
        
        // Cache it for future requests
        cacheService.put("issue", cacheKey, issue, Duration.ofMinutes(30));
        
        return issue;
    }
    
    /**
     * Example 3: Update with cache invalidation
     * 
     * CRITICAL: Always invalidate cache after updates to prevent stale data.
     */
    @Transactional
    public Issue updateIssue(Long issueId, IssueUpdate update) {
        // Load issue (may come from cache)
        Issue issue = getIssue(issueId);
        
        // Update issue
        issue.update(update);
        
        // Save to database
        Issue saved = saveIssue(issue);
        
        // CRITICAL: Invalidate cache after update
        cacheService.invalidate("issue", "issue:" + issueId);
        
        return saved;
    }
    
    /**
     * Example 4: Bulk operations with putAll
     * 
     * More efficient than multiple put() calls.
     */
    public void cacheMultipleIssues(java.util.Map<String, Issue> issues) {
        cacheService.putAll("issue", issues, Duration.ofMinutes(30));
    }
    
    /**
     * Example 5: Invalidate all entries in a cache
     * 
     * Use sparingly - only when bulk updates occur.
     */
    @Transactional
    public void bulkUpdateIssues(java.util.List<Long> issueIds, IssueUpdate update) {
        // Update in database
        updateIssuesInDatabase(issueIds, update);
        
        // Option 1: Invalidate individual keys
        for (Long issueId : issueIds) {
            cacheService.invalidate("issue", "issue:" + issueId);
        }
        
        // Option 2: If too many, clear entire cache
        if (issueIds.size() > 1000) {
            cacheService.invalidateAll("issue");
        }
    }
    
    /**
     * Example 6: Monitoring cache performance
     * 
     * Check cache statistics to monitor effectiveness.
     */
    public void monitorCache() {
        CacheStats stats = cacheService.getStats("issue");
        
        System.out.println("Cache Statistics:");
        System.out.println("  Hits: " + stats.getHits());
        System.out.println("  Misses: " + stats.getMisses());
        System.out.println("  Hit Ratio: " + (stats.getHitRatio() * 100) + "%");
        System.out.println("  Size: " + stats.getSize() + " entries");
        System.out.println("  Memory: " + (stats.getMemoryBytes() / 1024 / 1024) + " MB");
        System.out.println("  Evictions: " + stats.getEvictions());
        
        // Alert if hit ratio is low
        if (stats.getHitRatio() < 0.7) {
            System.err.println("WARNING: Low cache hit ratio!");
        }
    }
    
    /**
     * Example 7: Error handling with fallback
     * 
     * Handle cache failures gracefully.
     */
    public Issue getIssueWithFallback(Long issueId) {
        try {
            return cacheService.getOrLoad(
                "issue",
                "issue:" + issueId,
                () -> loadIssueFromDatabase(issueId),
                Duration.ofMinutes(30)
            );
        } catch (Exception e) {
            // Cache operation failed - fallback to direct database query
            System.err.println("Cache failed, using database: " + e.getMessage());
            return loadIssueFromDatabase(issueId);
        }
    }
    
    /**
     * Example 8: Conditional caching
     * 
     * Skip cache for certain scenarios.
     */
    public Issue getIssueConditional(Long issueId, boolean useCache) {
        if (useCache) {
            return getIssue(issueId);  // Use cache
        } else {
            return loadIssueFromDatabase(issueId);  // Skip cache
        }
    }
    
    // Helper methods (simulated)
    private Issue loadIssueFromDatabase(Long issueId) {
        // Your existing implementation that queries 20 tables
        // This is only called on cache miss!
        return new Issue(issueId, "Issue " + issueId);
    }
    
    private Issue saveIssue(Issue issue) {
        // Save to database
        return issue;
    }
    
    private void updateIssuesInDatabase(java.util.List<Long> issueIds, IssueUpdate update) {
        // Bulk update implementation
    }
    
    // Dummy classes for example
    static class Issue {
        Long id;
        String title;
        Issue(Long id, String title) { this.id = id; this.title = title; }
        void update(IssueUpdate update) {}
    }
    
    static class IssueUpdate {}
}


