package com.cache.api;

import com.cache.cluster.ClusterMembership;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API for cluster management and monitoring.
 * 
 * <p>This controller provides endpoints for:
 * <ul>
 *   <li>Viewing cluster status</li>
 *   <li>Listing cluster nodes</li>
 *   <li>Getting node details</li>
 * </ul>
 * 
 * @author Cache System
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/cluster")
@Tag(name = "Cluster Management", description = "Cluster coordination and node management")
@SecurityRequirement(name = "basicAuth")
public class ClusterController {
    
    private final ClusterMembership clusterMembership;

    public ClusterController(ClusterMembership clusterMembership) {
        this.clusterMembership = clusterMembership;
    }

    /**
     * Gets the current cluster status including node information and peer lists.
     * 
     * @return cluster status with node ID, active peers, and known peers
     */
    @Operation(
        summary = "Get cluster status",
        description = "Returns the current cluster status including the current node ID, list of active peer nodes, and known peer nodes."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Cluster status retrieved successfully"
    )
    @GetMapping
    public ResponseEntity<Map<String, Object>> getClusterStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("nodeId", clusterMembership.getNodeId());
        response.put("activePeers", clusterMembership.getActivePeers());
        response.put("knownPeers", clusterMembership.getKnownPeers());
        response.put("activePeerCount", clusterMembership.getActivePeers().size());
        response.put("lastHeartbeatTimes", clusterMembership.getLastHeartbeatTimes());
        response.put("consecutiveFailures", clusterMembership.getConsecutiveFailures());
        
        // Calculate health status
        Map<String, Boolean> peerHealth = new HashMap<>();
        for (String peer : clusterMembership.getKnownPeers()) {
            peerHealth.put(peer, clusterMembership.isPeerHealthy(peer));
        }
        response.put("peerHealth", peerHealth);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Lists all nodes in the cluster.
     * 
     * @return list of cluster nodes
     */
    @Operation(
        summary = "List cluster nodes",
        description = "Returns a list of all nodes in the cluster including the current node and active peers."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Node list retrieved successfully"
    )
    @GetMapping("/nodes")
    public ResponseEntity<Map<String, Object>> listNodes() {
        Map<String, Object> response = new HashMap<>();
        response.put("currentNode", clusterMembership.getNodeId());
        response.put("nodes", clusterMembership.getActivePeers());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Gets detailed information about a specific cluster node.
     * 
     * @param nodeId the node identifier
     * @return node details including whether it's the current node and active status
     */
    @Operation(
        summary = "Get node details",
        description = "Returns detailed information about a specific cluster node including whether it's the current node and its active status."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Node details retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Node not found")
    })
    @GetMapping("/nodes/{nodeId}")
    public ResponseEntity<Map<String, Object>> getNodeDetails(
            @Parameter(description = "Node identifier", required = true, example = "node-1")
            @PathVariable String nodeId) {
        Map<String, Object> response = new HashMap<>();
        response.put("nodeId", nodeId);
        response.put("isCurrentNode", nodeId.equals(clusterMembership.getNodeId()));
        response.put("isActive", clusterMembership.getActivePeers().contains(nodeId));
        
        return ResponseEntity.ok(response);
    }
}

