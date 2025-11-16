package com.cache.config;

import com.cache.cluster.ClusterCoordinator;
import com.cache.cluster.ClusterMembership;
import com.cache.cluster.MessageReceiver;
import com.cache.cluster.MessageSender;
import com.cache.core.CacheService;
import com.cache.core.impl.CacheServiceImpl;
import com.cache.core.impl.InMemoryCacheManager;
import com.cache.metrics.CacheMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Spring configuration for cache system components.
 */
@Configuration
@EnableConfigurationProperties(CacheSystemProperties.class)
public class CacheSystemConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(CacheSystemConfiguration.class);

    @Bean
    public InMemoryCacheManager cacheManager(CacheSystemProperties properties, CacheMetrics cacheMetrics) {
        long defaultMaxEntries = properties.getDefaultMaxEntries();
        long defaultMemoryCapBytes = properties.getDefaultMemoryCapMb() * 1024 * 1024;
        
        String nodeId = properties.getCluster().getNodeId();
        if (nodeId == null || nodeId.isEmpty()) {
            nodeId = "node-" + System.currentTimeMillis();
        }
        
        InMemoryCacheManager manager = new InMemoryCacheManager(nodeId, defaultMaxEntries, defaultMemoryCapBytes);
        manager.setCacheMetrics(cacheMetrics);
        
        // Configure caches from properties
        for (Map.Entry<String, CacheSystemProperties.CacheConfig> entry : properties.getCaches().entrySet()) {
            String cacheName = entry.getKey();
            CacheSystemProperties.CacheConfig cacheConfig = entry.getValue();
            
            CacheConfiguration config = new CacheConfiguration(
                cacheConfig.getTtl() != null ? cacheConfig.getTtl() : properties.getDefaultTtl(),
                cacheConfig.getEvictionPolicy() != null ? cacheConfig.getEvictionPolicy() : properties.getDefaultEvictionPolicy(),
                cacheConfig.getMaxEntries() > 0 ? cacheConfig.getMaxEntries() : defaultMaxEntries,
                cacheConfig.getMemoryCapMb() > 0 ? cacheConfig.getMemoryCapMb() * 1024 * 1024 : defaultMemoryCapBytes,
                cacheConfig.getReplicationMode() != null ? cacheConfig.getReplicationMode() : CacheConfiguration.ReplicationMode.NONE,
                cacheConfig.getPersistenceMode() != null ? cacheConfig.getPersistenceMode() : CacheConfiguration.PersistenceMode.NONE
            );
            
            manager.configureCache(cacheName, config);
            logger.info("Configured cache: {} from properties", cacheName);
        }
        
        return manager;
    }

    @Bean
    public ClusterMembership clusterMembership(CacheSystemProperties properties, MessageSender messageSender) {
        String nodeId = properties.getCluster().getNodeId();
        if (nodeId == null || nodeId.isEmpty()) {
            nodeId = "node-" + System.currentTimeMillis();
        }
        
        Set<String> initialPeers = new HashSet<>();
        if (properties.getCluster().getDiscovery().getType().equals("static")) {
            String peersStr = properties.getCluster().getDiscovery().getStatic().getPeers();
            if (peersStr != null && !peersStr.isEmpty()) {
                String[] peers = peersStr.split(",");
                for (String peer : peers) {
                    String trimmed = peer.trim();
                    if (!trimmed.isEmpty() && !trimmed.equals(nodeId)) {
                        // Ensure peer address includes communication port if not specified
                        if (!trimmed.contains(":")) {
                            trimmed = trimmed + ":" + properties.getCluster().getCommunication().getPort();
                        }
                        initialPeers.add(trimmed);
                    }
                }
            }
        }
        
        long heartbeatInterval = properties.getCluster().getHeartbeat().getIntervalMs();
        long heartbeatTimeout = properties.getCluster().getHeartbeat().getTimeoutMs();
        
        return new ClusterMembership(nodeId, initialPeers, heartbeatInterval, heartbeatTimeout, messageSender);
    }

    @Bean
    public MessageSender messageSender(CacheSystemProperties properties) {
        int port = properties.getCluster().getCommunication().getPort();
        return new MessageSender(port);
    }

    @Bean
    public MessageReceiver messageReceiver(CacheSystemProperties properties, 
                                          ClusterCoordinator clusterCoordinator) {
        int port = properties.getCluster().getCommunication().getPort();
        MessageReceiver receiver = new MessageReceiver(port, clusterCoordinator);
        receiver.start();
        return receiver;
    }

    @Bean
    public ClusterCoordinator clusterCoordinator(ClusterMembership membership,
                                                MessageSender messageSender,
                                                InMemoryCacheManager cacheManager,
                                                CacheSystemProperties properties) {
        boolean async = properties.getCluster().getCommunication().isAsync();
        return new ClusterCoordinator(membership, messageSender, cacheManager, async);
    }

    @Bean
    public CacheService cacheService(InMemoryCacheManager cacheManager, 
                                    ClusterCoordinator clusterCoordinator,
                                    CacheMetrics cacheMetrics) {
        return new CacheServiceImpl(cacheManager, clusterCoordinator, cacheMetrics);
    }

    /**
     * Graceful shutdown listener to clean up resources.
     */
    @Bean
    public ApplicationListener<ContextClosedEvent> shutdownListener(
            MessageReceiver messageReceiver,
            ClusterMembership clusterMembership) {
        return event -> {
            logger.info("Shutting down cache system gracefully...");
            try {
                // Stop accepting new messages
                messageReceiver.stop();
                
                // Shutdown cluster membership
                clusterMembership.shutdown();
                
                logger.info("Cache system shutdown complete");
            } catch (Exception e) {
                logger.error("Error during graceful shutdown", e);
            }
        };
    }
}

