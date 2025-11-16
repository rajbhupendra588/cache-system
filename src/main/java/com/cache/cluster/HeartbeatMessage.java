package com.cache.cluster;

import java.io.Serializable;

/**
 * Heartbeat message sent between cluster nodes to verify connectivity.
 */
public class HeartbeatMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String nodeId;
    private long timestamp;
    private String messageType = "HEARTBEAT";
    
    public HeartbeatMessage() {
    }
    
    public HeartbeatMessage(String nodeId) {
        this.nodeId = nodeId;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getNodeId() {
        return nodeId;
    }
    
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getMessageType() {
        return messageType;
    }
    
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
}

