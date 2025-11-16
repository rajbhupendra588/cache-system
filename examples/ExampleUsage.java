package com.cache.examples;

import com.cache.core.CacheService;
import com.cache.core.CacheStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Example usage of CacheService for developers.
 * These examples demonstrate common patterns and best practices.
 */
@Service
public class ExampleUsage {
    
    @Autowired
    private CacheService cacheService;
    
    // Example 1: Basic caching with getOrLoad (RECOMMENDED)
    public Issue getIssue(Long issueId) {
        String cacheKey = "issue:" + issueId;
        
        return cacheService.getOrLoad(
            "issue",                    // Cache name
            cacheKey,                   // Cache key
            () -> loadIssueFromDB(issueId),  // Loader function (only called on cache miss)
            Duration.ofMinutes(30)      // TTL
        );
    }
    
    // Example 2: Cache-aside pattern (explicit control)
    public Issue getIssueExplicit(Long issueId) {
        String cacheKey = "issue:" + issueId;
        
        // Try cache first
        Optional<Issue> cached = cacheService.get("issue", cacheKey, Issue.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        
        // Cache miss - load from database
        Issue issue = loadIssueFromDB(issueId);
        
        // Cache for future requests
        cacheService.put("issue", cacheKey, issue, Duration.ofMinutes(30));
        
        return issue;
    }
    
    // Example 3: Caching complex aggregations
    public IssueDetailDTO getIssueDetail(Long issueId) {
        String cacheKey = "issue:detail:" + issueId;
        
        return cacheService.getOrLoad(
            "issue",
            cacheKey,
            () -> {
                // Complex loading - only executes on cache miss
                Issue issue = getIssue(issueId);  // Uses cache
                List<Comment> comments = getComments(issueId);  // Uses cache
                List<Attachment> attachments = getAttachments(issueId);
                
                return IssueDetailDTO.builder()
                    .issue(issue)
                    .comments(comments)
                    .attachments(attachments)
                    .build();
            },
            Duration.ofMinutes(15)  // Shorter TTL for frequently updated data
        );
    }
    
    // Example 4: Bulk loading with putAll
    public Map<String, Comment> getAllComments(Long issueId) {
        String cacheKey = "issue:comments:" + issueId;
        
        return cacheService.getOrLoad(
            "comments",
            cacheKey,
            () -> {
                List<Comment> comments = loadCommentsFromDB(issueId);
                
                // Cache individual comments too
                Map<String, Comment> commentMap = comments.stream()
                    .collect(Collectors.toMap(
                        c -> "comment:" + c.getId(),
                        c -> c
                    ));
                cacheService.putAll("comments", commentMap, Duration.ofHours(1));
                
                return commentMap;
            },
            Duration.ofHours(1)
        );
    }
    
    // Example 5: Invalidation after updates
    public Issue updateIssue(Long issueId, IssueUpdate update) {
        Issue issue = getIssue(issueId);
        issue.applyUpdate(update);
        
        Issue saved = saveIssue(issue);
        
        // CRITICAL: Invalidate cache after update
        cacheService.invalidate("issue", "issue:" + issueId);
        cacheService.invalidate("issue", "issue:detail:" + issueId);  // Also invalidate detail cache
        
        return saved;
    }
    
    // Example 6: Bulk invalidation
    public void bulkUpdateIssues(List<Long> issueIds, IssueUpdate update) {
        // Update in database
        updateIssuesInDB(issueIds, update);
        
        // Invalidate all affected caches
        for (Long issueId : issueIds) {
            cacheService.invalidate("issue", "issue:" + issueId);
            cacheService.invalidate("issue", "issue:detail:" + issueId);
        }
    }
    
    // Example 7: Cache warming on startup
    public void warmCache() {
        // Load top 100 most accessed issues
        List<Issue> topIssues = loadTop100Issues();
        
        Map<String, Issue> issueMap = topIssues.stream()
            .collect(Collectors.toMap(
                i -> "issue:" + i.getId(),
                i -> i
            ));
        
        cacheService.putAll("issue", issueMap, Duration.ofMinutes(30));
    }
    
    // Example 8: Monitoring cache effectiveness
    public void logCacheStats() {
        CacheStats stats = cacheService.getStats("issue");
        
        System.out.println("Cache 'issue' Statistics:");
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
    
    // Example 9: Error handling
    public Issue getIssueWithErrorHandling(Long issueId) {
        String cacheKey = "issue:" + issueId;
        
        try {
            return cacheService.getOrLoad(
                "issue",
                cacheKey,
                () -> loadIssueFromDB(issueId),
                Duration.ofMinutes(30)
            );
        } catch (Exception e) {
            // Log error and fallback to direct DB query
            System.err.println("Cache operation failed: " + e.getMessage());
            return loadIssueFromDB(issueId);  // Fallback
        }
    }
    
    // Example 10: Conditional caching
    public Issue getIssueConditional(Long issueId, boolean useCache) {
        String cacheKey = "issue:" + issueId;
        
        if (useCache) {
            return cacheService.getOrLoad(
                "issue",
                cacheKey,
                () -> loadIssueFromDB(issueId),
                Duration.ofMinutes(30)
            );
        } else {
            // Bypass cache
            return loadIssueFromDB(issueId);
        }
    }
    
    // Helper methods (simulated)
    private Issue loadIssueFromDB(Long issueId) {
        // Simulate database query
        return new Issue(issueId, "Issue " + issueId);
    }
    
    private Issue saveIssue(Issue issue) {
        // Simulate save
        return issue;
    }
    
    private List<Comment> getComments(Long issueId) {
        return List.of(new Comment(1L, "Comment 1"));
    }
    
    private List<Attachment> getAttachments(Long issueId) {
        return List.of(new Attachment(1L, "file.pdf"));
    }
    
    private List<Comment> loadCommentsFromDB(Long issueId) {
        return List.of(new Comment(1L, "Comment 1"));
    }
    
    private void updateIssuesInDB(List<Long> issueIds, IssueUpdate update) {
        // Simulate bulk update
    }
    
    private List<Issue> loadTop100Issues() {
        return List.of(new Issue(1L, "Top Issue"));
    }
    
    // Dummy classes for examples
    static class Issue {
        Long id;
        String title;
        Issue(Long id, String title) { this.id = id; this.title = title; }
        void applyUpdate(IssueUpdate update) {}
    }
    
    static class IssueUpdate {}
    
    static class IssueDetailDTO {
        Issue issue;
        List<Comment> comments;
        List<Attachment> attachments;
        static Builder builder() { return new Builder(); }
        static class Builder {
            Builder issue(Issue i) { return this; }
            Builder comments(List<Comment> c) { return this; }
            Builder attachments(List<Attachment> a) { return this; }
            IssueDetailDTO build() { return new IssueDetailDTO(); }
        }
    }
    
    static class Comment {
        Long id;
        String text;
        Comment(Long id, String text) { this.id = id; this.text = text; }
    }
    
    static class Attachment {
        Long id;
        String filename;
        Attachment(Long id, String filename) { this.id = id; this.filename = filename; }
    }
}

