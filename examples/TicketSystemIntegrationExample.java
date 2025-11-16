package com.cache.examples;

import com.cache.core.CacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Complete example for integrating cache into a Jira-like ticket management system.
 * 
 * PROBLEM: Loading an issue queries ~20 tables and takes 4-5 seconds
 * SOLUTION: Cache the complete issue view DTO
 * RESULT: <2ms for cache hits, 95%+ reduction in database load
 */
@Service
public class TicketSystemIntegrationExample {
    
    @Autowired
    private CacheService cacheService;
    
    // Inject all your repositories (20+ repositories)
    // @Autowired private IssueRepository issueRepository;
    // @Autowired private CommentRepository commentRepository;
    // ... etc
    
    /**
     * MAIN METHOD: Get issue view with caching.
     * Replace your existing slow method with this.
     * 
     * Performance:
     * - First call: ~5 seconds (cache miss - loads from 20 tables)
     * - Subsequent calls: <2ms (cache hit) ⚡
     */
    public IssueViewDTO getIssueView(Long issueId) {
        String cacheKey = "issue:view:" + issueId;
        
        return cacheService.getOrLoad(
            "issue",                    // Cache name
            cacheKey,                   // Cache key
            () -> loadIssueFrom20Tables(issueId),  // Loader - only called on cache miss
            Duration.ofMinutes(30)      // Cache for 30 minutes
        );
    }
    
    /**
     * Your existing slow method - now wrapped with caching.
     * This aggregates data from ~20 tables.
     * Only executes on cache miss (first request or after invalidation).
     */
    private IssueViewDTO loadIssueFrom20Tables(Long issueId) {
        long startTime = System.currentTimeMillis();
        
        // Load from all 20 tables
        // Table 1: Core issue
        // Issue issue = issueRepository.findById(issueId).orElseThrow();
        
        // Table 2: Comments
        // List<Comment> comments = commentRepository.findByIssueId(issueId);
        
        // Table 3: Attachments
        // List<Attachment> attachments = attachmentRepository.findByIssueId(issueId);
        
        // ... load from remaining 17 tables ...
        
        // Build complete DTO
        IssueViewDTO dto = IssueViewDTO.builder()
            // .issueId(issue.getId())
            // .comments(comments.stream().map(CommentDTO::from).collect(Collectors.toList()))
            // ... set all fields from 20 tables ...
            .build();
        
        long loadTime = System.currentTimeMillis() - startTime;
        System.out.println("Loaded issue " + issueId + " from 20 tables in " + loadTime + "ms");
        
        return dto;
    }
    
    /**
     * Update issue - MUST invalidate cache.
     */
    @Transactional
    public void updateIssue(Long issueId, IssueUpdate update) {
        // Update in database
        // issueRepository.save(update);
        
        // CRITICAL: Invalidate cache
        cacheService.invalidate("issue", "issue:view:" + issueId);
    }
    
    /**
     * Add comment - MUST invalidate cache.
     */
    @Transactional
    public void addComment(Long issueId, Comment comment) {
        // Save comment
        // commentRepository.save(comment);
        
        // CRITICAL: Invalidate cache
        cacheService.invalidate("issue", "issue:view:" + issueId);
    }
    
    /**
     * Add attachment - MUST invalidate cache.
     */
    @Transactional
    public void addAttachment(Long issueId, Attachment attachment) {
        // Save attachment
        // attachmentRepository.save(attachment);
        
        // CRITICAL: Invalidate cache
        cacheService.invalidate("issue", "issue:view:" + issueId);
    }
    
    /**
     * Change status - MUST invalidate cache.
     */
    @Transactional
    public void changeStatus(Long issueId, String newStatus) {
        // Update status in database
        // issueRepository.updateStatus(issueId, newStatus);
        // statusHistoryRepository.save(new StatusTransition(...));
        
        // CRITICAL: Invalidate cache
        cacheService.invalidate("issue", "issue:view:" + issueId);
    }
    
    /**
     * Update ANY field from ANY of the 20 tables - invalidate cache.
     */
    @Transactional
    public void updateAnyField(Long issueId, String fieldType, Object value) {
        // Update in appropriate table
        // switch (fieldType) {
        //     case "customField": customFieldRepository.update(...); break;
        //     case "label": labelRepository.update(...); break;
        //     // ... etc
        // }
        
        // CRITICAL: Always invalidate cache when ANY related data changes
        cacheService.invalidate("issue", "issue:view:" + issueId);
    }
    
    // DTO classes (simplified)
    static class IssueViewDTO {
        Long issueId;
        String key;
        String title;
        List<CommentDTO> comments;
        List<AttachmentDTO> attachments;
        // ... fields from all 20 tables
        
        static Builder builder() { return new Builder(); }
        static class Builder {
            Builder issueId(Long id) { return this; }
            Builder key(String k) { return this; }
            Builder title(String t) { return this; }
            Builder comments(List<CommentDTO> c) { return this; }
            Builder attachments(List<AttachmentDTO> a) { return this; }
            IssueViewDTO build() { return new IssueViewDTO(); }
        }
    }
    
    static class CommentDTO {}
    static class AttachmentDTO {}
    static class IssueUpdate {}
    static class Comment {}
    static class Attachment {}
}

