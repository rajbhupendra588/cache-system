package com.cache.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for the cache system loaded from application.yml.
 * 
 * <p>All configuration values are validated on startup. Invalid configurations
 * will cause the application to fail to start with clear error messages.
 * 
 * <p>Note: This class is registered via {@code @EnableConfigurationProperties}
 * in {@link CacheSystemConfiguration}, so {@code @Component} is not needed.
 * 
 * @author Cache System
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "cache.system")
@Validated
public class CacheSystemProperties {
    
    @NotNull
    @Valid
    private ClusterConfig cluster = new ClusterConfig();
    
    @NotNull
    private Duration defaultTtl = Duration.ofHours(1);
    
    @NotNull
    private CacheConfiguration.EvictionPolicy defaultEvictionPolicy = CacheConfiguration.EvictionPolicy.LRU;
    
    @Min(1)
    private long defaultMaxEntries = 10000;
    
    @Min(1)
    private long defaultMemoryCapMb = 1024;
    
    @Valid
    private Map<String, CacheConfig> caches = new HashMap<>();

    public ClusterConfig getCluster() {
        return cluster;
    }

    public void setCluster(ClusterConfig cluster) {
        this.cluster = cluster;
    }

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    public CacheConfiguration.EvictionPolicy getDefaultEvictionPolicy() {
        return defaultEvictionPolicy;
    }

    public void setDefaultEvictionPolicy(CacheConfiguration.EvictionPolicy defaultEvictionPolicy) {
        this.defaultEvictionPolicy = defaultEvictionPolicy;
    }

    public long getDefaultMaxEntries() {
        return defaultMaxEntries;
    }

    public void setDefaultMaxEntries(long defaultMaxEntries) {
        this.defaultMaxEntries = defaultMaxEntries;
    }

    public long getDefaultMemoryCapMb() {
        return defaultMemoryCapMb;
    }

    public void setDefaultMemoryCapMb(long defaultMemoryCapMb) {
        this.defaultMemoryCapMb = defaultMemoryCapMb;
    }

    public Map<String, CacheConfig> getCaches() {
        return caches;
    }

    public void setCaches(Map<String, CacheConfig> caches) {
        this.caches = caches;
    }

    public static class ClusterConfig {
        private boolean enabled = true;
        private String nodeId;
        private DiscoveryConfig discovery = new DiscoveryConfig();
        private HeartbeatConfig heartbeat = new HeartbeatConfig();
        private CommunicationConfig communication = new CommunicationConfig();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }

        public DiscoveryConfig getDiscovery() {
            return discovery;
        }

        public void setDiscovery(DiscoveryConfig discovery) {
            this.discovery = discovery;
        }

        public HeartbeatConfig getHeartbeat() {
            return heartbeat;
        }

        public void setHeartbeat(HeartbeatConfig heartbeat) {
            this.heartbeat = heartbeat;
        }

        public CommunicationConfig getCommunication() {
            return communication;
        }

        public void setCommunication(CommunicationConfig communication) {
            this.communication = communication;
        }
    }

    public static class DiscoveryConfig {
        private String type = "static";
        private StaticDiscoveryConfig staticConfig = new StaticDiscoveryConfig();
        private MulticastDiscoveryConfig multicast = new MulticastDiscoveryConfig();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public StaticDiscoveryConfig getStatic() {
            return staticConfig;
        }

        public void setStatic(StaticDiscoveryConfig staticConfig) {
            this.staticConfig = staticConfig;
        }

        public MulticastDiscoveryConfig getMulticast() {
            return multicast;
        }

        public void setMulticast(MulticastDiscoveryConfig multicast) {
            this.multicast = multicast;
        }
    }

    public static class StaticDiscoveryConfig {
        private String peers = "";

        public String getPeers() {
            return peers;
        }

        public void setPeers(String peers) {
            this.peers = peers;
        }
    }

    public static class MulticastDiscoveryConfig {
        private String group = "230.0.0.1";
        private int port = 5432;

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    public static class HeartbeatConfig {
        private long intervalMs = 5000;
        private long timeoutMs = 15000;

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    @Validated
    public static class CommunicationConfig {
        @Min(1024)
        private int port = 9090;
        private boolean async = true;

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public boolean isAsync() {
            return async;
        }

        public void setAsync(boolean async) {
            this.async = async;
        }
    }

    @Validated
    public static class CacheConfig {
        private Duration ttl;
        private CacheConfiguration.EvictionPolicy evictionPolicy;
        
        @Min(1)
        private long maxEntries;
        
        @Min(1)
        private long memoryCapMb;
        
        private CacheConfiguration.ReplicationMode replicationMode;
        private CacheConfiguration.PersistenceMode persistenceMode;

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public CacheConfiguration.EvictionPolicy getEvictionPolicy() {
            return evictionPolicy;
        }

        public void setEvictionPolicy(CacheConfiguration.EvictionPolicy evictionPolicy) {
            this.evictionPolicy = evictionPolicy;
        }

        public long getMaxEntries() {
            return maxEntries;
        }

        public void setMaxEntries(long maxEntries) {
            this.maxEntries = maxEntries;
        }

        public long getMemoryCapMb() {
            return memoryCapMb;
        }

        public void setMemoryCapMb(long memoryCapMb) {
            this.memoryCapMb = memoryCapMb;
        }

        public CacheConfiguration.ReplicationMode getReplicationMode() {
            return replicationMode;
        }

        public void setReplicationMode(CacheConfiguration.ReplicationMode replicationMode) {
            this.replicationMode = replicationMode;
        }

        public CacheConfiguration.PersistenceMode getPersistenceMode() {
            return persistenceMode;
        }

        public void setPersistenceMode(CacheConfiguration.PersistenceMode persistenceMode) {
            this.persistenceMode = persistenceMode;
        }
    }
}

