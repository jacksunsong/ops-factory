/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.annotation.PostConstruct;

/**
 * Manages cluster-level relations including topology graph data, neighbor resolution, and cascade deletes.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Service
public class ClusterRelationService {
    private static final String MEMBERSHIP_RELATION = "包含";

    private static final Logger log = LoggerFactory.getLogger(ClusterRelationService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GatewayProperties properties;

    private Path relationsDir;

    private HostService hostService;

    private ClusterService clusterService;

    private ClusterTypeService clusterTypeService;

    private BusinessServiceService businessServiceService;

    /**
     * Creates the cluster relation service instance.
     */
    public ClusterRelationService(GatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * Sets the host service via lazy injection.
     *
     * @param hostService the host service via lazy injection
     */
    @Lazy
    @Autowired
    public void setHostService(HostService hostService) {
        this.hostService = hostService;
    }

    /**
     * Sets the cluster service via lazy injection.
     *
     * @param clusterService the cluster service via lazy injection
     */
    @Lazy
    @Autowired
    public void setClusterService(ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    /**
     * Sets the cluster type service via lazy injection.
     *
     * @param clusterTypeService the cluster type service via lazy injection
     */
    @Lazy
    @Autowired
    public void setClusterTypeService(ClusterTypeService clusterTypeService) {
        this.clusterTypeService = clusterTypeService;
    }

    /**
     * Sets the business service service via lazy injection.
     *
     * @param businessServiceService the business service service via lazy injection
     */
    @Lazy
    @Autowired
    public void setBusinessServiceService(BusinessServiceService businessServiceService) {
        this.businessServiceService = businessServiceService;
    }

    /**
     * Initializes the cluster relations data directory at startup.
     */
    @PostConstruct
    public void init() {
        Path gatewayRoot = properties.getGatewayRootPath();
        this.relationsDir = gatewayRoot.resolve("data").resolve("cluster-relations");
        try {
            Files.createDirectories(relationsDir);
        } catch (IOException e) {
            log.error("Failed to create cluster-relations directory: {}", relationsDir, e);
        }
        log.info("ClusterRelationService initialized, relationsDir={}", relationsDir);
    }

    // ── CRUD Operations ──────────────────────────────────────────────

    /**
     * Lists cluster relations optionally filtered by cluster ID.
     *
     * @param clusterId cluster identifier
     * @return the result
     */
    public List<Map<String, Object>> listRelations(String clusterId) {
        List<Map<String, Object>> relations = new ArrayList<>();
        if (!Files.isDirectory(relationsDir)) {
            return relations;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(relationsDir, "*.json")) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                Map<String, Object> rel = readFile(file);
                if (rel == null) {
                    continue;
                }
                if (clusterId != null && !clusterId.isEmpty()) {
                    String sourceId = (String) rel.get("sourceId");
                    String targetId = (String) rel.get("targetId");
                    String sourceType = (String) rel.getOrDefault("sourceType", "cluster");
                    boolean match =
                        clusterId.equals(targetId) || (clusterId.equals(sourceId) && "cluster".equals(sourceType));
                    if (!match) {
                        continue;
                    }
                }
                relations.add(rel);
            }
        } catch (IOException e) {
            log.error("Failed to list cluster-relations from {}", relationsDir, e);
        }
        return relations;
    }

    /**
     * Creates a new cluster relation from the provided field map.
     *
     * @param body request body
     * @return the result
     */
    public Map<String, Object> createRelation(Map<String, Object> body) {
        String sourceType = (String) body.getOrDefault("sourceType", "cluster");
        String sourceId = (String) body.get("sourceId");
        String targetId = (String) body.get("targetId");

        if (sourceId == null || sourceId.isEmpty()) {
            throw new IllegalArgumentException("sourceId is required");
        }
        if (targetId == null || targetId.isEmpty()) {
            throw new IllegalArgumentException("targetId is required");
        }

        // Validate source
        if ("business-service".equals(sourceType)) {
            try {
                businessServiceService.getBusinessService(sourceId);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Source business service not found: " + sourceId);
            }
        } else {
            try {
                clusterService.getCluster(sourceId);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Source cluster not found: " + sourceId);
            }
        }

        // Validate target (always a cluster)
        try {
            clusterService.getCluster(targetId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Target cluster not found: " + targetId);
        }

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        Map<String, Object> relation = new LinkedHashMap<>();
        relation.put("id", id);
        relation.put("sourceType", sourceType);
        relation.put("sourceId", sourceId);
        relation.put("targetId", targetId);
        relation.put("description", body.getOrDefault("description", ""));
        relation.put("createdAt", now);
        relation.put("updatedAt", now);

        writeEntityFile(id, relation);
        log.info("Created cluster relation: id={}, sourceType={}, source={}, target={}", id, sourceType, sourceId,
            targetId);

        // Sync hostIds on the business service
        if ("business-service".equals(sourceType) && businessServiceService != null) {
            businessServiceService.syncHostIdsFromClusterRelations(sourceId);
        }

        return relation;
    }

    /**
     * Updates an existing cluster relation with the provided field map.
     *
     * @param id an existing cluster relation with the provided field map
     * @param body an existing cluster relation with the provided field map
     * @return the result
     */
    public Map<String, Object> updateRelation(String id, Map<String, Object> body) {
        Path file = relationsDir.resolve(id + ".json");
        Map<String, Object> relation = readFile(file);
        if (relation == null) {
            throw new IllegalArgumentException("Cluster relation not found: " + id);
        }

        String currentSourceType = (String) relation.getOrDefault("sourceType", "cluster");

        if (body.containsKey("description")) {
            relation.put("description", body.get("description"));
        }
        if (body.containsKey("sourceId")) {
            String sourceId = (String) body.get("sourceId");
            if ("business-service".equals(currentSourceType)) {
                try {
                    businessServiceService.getBusinessService(sourceId);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Source business service not found: " + sourceId);
                }
            } else {
                try {
                    clusterService.getCluster(sourceId);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Source cluster not found: " + sourceId);
                }
            }
            relation.put("sourceId", sourceId);
        }
        if (body.containsKey("targetId")) {
            String targetId = (String) body.get("targetId");
            try {
                clusterService.getCluster(targetId);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Target cluster not found: " + targetId);
            }
            relation.put("targetId", targetId);
        }

        relation.put("updatedAt", Instant.now().toString());
        writeEntityFile(id, relation);
        log.info("Updated cluster relation: id={}", id);

        // Sync hostIds if this is a business-service relation
        if ("business-service".equals(currentSourceType) && businessServiceService != null) {
            businessServiceService.syncHostIdsFromClusterRelations((String) relation.get("sourceId"));
        }

        return relation;
    }

    /**
     * Deletes a cluster relation by its ID.
     *
     * @param id entity identifier
     * @return the result
     */
    public boolean deleteRelation(String id) {
        Path file = relationsDir.resolve(id + ".json");
        try {
            if (Files.exists(file)) {
                Map<String, Object> rel = readFile(file);
                String sourceType = rel != null ? (String) rel.getOrDefault("sourceType", "cluster") : "cluster";
                String sourceId = rel != null ? (String) rel.get("sourceId") : null;

                Files.delete(file);
                log.info("Deleted cluster relation: id={}", id);

                if ("business-service".equals(sourceType) && sourceId != null && businessServiceService != null) {
                    businessServiceService.syncHostIdsFromClusterRelations(sourceId);
                }
                return true;
            }
            return false;
        } catch (IOException e) {
            log.error("Failed to delete cluster-relation file: {}", file, e);
            return false;
        }
    }

    /**
     * Delete all relations involving a specific cluster (cascade delete).
     *
     * @param clusterId cluster identifier
     */
    public void deleteRelationsByCluster(String clusterId) {
        List<Map<String, Object>> all = listRelations(null);
        int count = 0;
        for (Map<String, Object> rel : all) {
            String sourceId = (String) rel.get("sourceId");
            String targetId = (String) rel.get("targetId");
            String sourceType = (String) rel.getOrDefault("sourceType", "cluster");
            boolean match = clusterId.equals(targetId) || (clusterId.equals(sourceId) && "cluster".equals(sourceType));
            if (match) {
                deleteRelation((String) rel.get("id"));
                count++;
            }
        }
        if (count > 0) {
            log.info("Cascade deleted {} cluster relations for cluster {}", count, clusterId);
        }
    }

    /**
     * Delete all relations where source is a specific business service (cascade delete).
     *
     * @param bsId bs id
     */
    public void deleteRelationsByBusinessService(String bsId) {
        List<Map<String, Object>> all = listRelations(null);
        int count = 0;
        for (Map<String, Object> rel : all) {
            String sourceId = (String) rel.get("sourceId");
            String sourceType = (String) rel.getOrDefault("sourceType", "cluster");
            if ("business-service".equals(sourceType) && bsId.equals(sourceId)) {
                deleteRelation((String) rel.get("id"));
                count++;
            }
        }
        if (count > 0) {
            log.info("Cascade deleted {} cluster relations for business service {}", count, bsId);
        }
    }

    // ── Cluster→Host membership relation ─────────────────────────────

    /**
     * Sync the membership relation for a host (cluster contains host).
     * - If no relation exists and clusterId is non-null → create one.
     * - If relation exists and clusterId changed → update sourceId (the cluster).
     * - If relation exists and clusterId cleared → delete relation.
     * - If relation exists and same clusterId → no-op.
     *
     * @param hostId host identifier
     * @param clusterId cluster identifier
     */
    public void syncHostClusterRelation(String hostId, String clusterId) {
        List<Map<String, Object>> all = listRelations(null);
        Map<String, Object> existing = null;
        for (Map<String, Object> rel : all) {
            String st = (String) rel.getOrDefault("sourceType", "cluster");
            String tid = (String) rel.get("targetId");
            String desc = (String) rel.getOrDefault("description", "");
            if ("cluster".equals(st) && hostId.equals(tid) && MEMBERSHIP_RELATION.equals(desc)) {
                existing = rel;
                break;
            }
        }

        boolean clusterIdEmpty = clusterId == null || clusterId.isEmpty();

        if (existing == null && !clusterIdEmpty) {
            // Create new membership relation: cluster → host
            String id = UUID.randomUUID().toString();
            String now = Instant.now().toString();
            Map<String, Object> relation = new LinkedHashMap<>();
            relation.put("id", id);
            relation.put("sourceType", "cluster");
            relation.put("sourceId", clusterId);
            relation.put("targetId", hostId);
            relation.put("description", MEMBERSHIP_RELATION);
            relation.put("createdAt", now);
            relation.put("updatedAt", now);
            writeEntityFile(id, relation);
            log.info("Created membership relation: cluster={} -> host={}", clusterId, hostId);
        } else if (existing != null && clusterIdEmpty) {
            // Delete relation
            deleteRelation((String) existing.get("id"));
            log.info("Deleted membership relation for host={} (clusterId cleared)", hostId);
        } else if (existing != null && !clusterIdEmpty) {
            String currentSource = (String) existing.get("sourceId");
            if (!clusterId.equals(currentSource)) {
                // Update sourceId (the cluster)
                existing.put("sourceId", clusterId);
                existing.put("updatedAt", Instant.now().toString());
                writeEntityFile((String) existing.get("id"), existing);
                log.info("Updated membership relation: cluster={} -> host={} (was cluster={})", clusterId, hostId,
                    currentSource);
            }
        }
    }

    /**
     * Delete the membership relation for a host (used on host delete).
     *
     * @param hostId host identifier
     */
    public void deleteConstituteRelationByHost(String hostId) {
        List<Map<String, Object>> all = listRelations(null);
        for (Map<String, Object> rel : all) {
            String st = (String) rel.getOrDefault("sourceType", "cluster");
            String tid = (String) rel.get("targetId");
            String desc = (String) rel.getOrDefault("description", "");
            if ("cluster".equals(st) && hostId.equals(tid) && MEMBERSHIP_RELATION.equals(desc)) {
                deleteRelation((String) rel.get("id"));
                log.info("Deleted membership relation for host={} on host delete", hostId);
                return;
            }
        }
    }

    // ── Graph Data ───────────────────────────────────────────────────

    /**
     * Build cluster-level graph data for a given group.
     *
     * @param groupId group identifier
     * @return cluster-level graph data for the group
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getGraphData(String groupId) {
        // Collect clusters in group
        List<Map<String, Object>> groupClusters;
        if (groupId != null && !groupId.isEmpty()) {
            groupClusters = clusterService.listClusters(groupId, null);
            // Also include clusters from sub-groups
            collectSubGroupClusters(groupId, groupClusters);
        } else {
            groupClusters = clusterService.listClusters(null, null);
        }

        Map<String, Map<String, Object>> clusterMap = new LinkedHashMap<>();
        for (Map<String, Object> c : groupClusters) {
            clusterMap.put((String) c.get("id"), c);
        }

        // Scan all cluster-relations for matching edges (+1 hop)
        List<Map<String, Object>> allRelations = listRelations(null);
        List<Map<String, Object>> matchedEdges = new ArrayList<>();

        // Track business service nodes to add
        Map<String, Map<String, Object>> bsNodes = new LinkedHashMap<>();

        // Track host nodes for flat topology
        Map<String, Map<String, Object>> hostNodes = new LinkedHashMap<>();

        for (Map<String, Object> rel : allRelations) {
            String sourceId = (String) rel.get("sourceId");
            String targetId = (String) rel.get("targetId");
            String sourceType = (String) rel.getOrDefault("sourceType", "cluster");

            boolean sourceInGroup = clusterMap.containsKey(sourceId);
            boolean targetInGroup = clusterMap.containsKey(targetId);

            if (sourceInGroup || targetInGroup) {
                // Add +1 hop clusters
                if (sourceInGroup && !clusterMap.containsKey(targetId)) {
                    try {
                        Map<String, Object> tc = clusterService.getCluster(targetId);
                        clusterMap.put(targetId, tc);
                    } catch (IllegalArgumentException e) {
                        log.debug("Skipping missing target cluster {} while building topology", targetId);
                    }
                }
                if (targetInGroup && "cluster".equals(sourceType) && !clusterMap.containsKey(sourceId)) {
                    try {
                        Map<String, Object> sc = clusterService.getCluster(sourceId);
                        clusterMap.put(sourceId, sc);
                    } catch (IllegalArgumentException e) {
                        log.debug("Skipping missing source cluster {} while building topology", sourceId);
                    }
                }

                // Track business service nodes
                if ("business-service".equals(sourceType) && !bsNodes.containsKey(sourceId)) {
                    try {
                        Map<String, Object> bs = businessServiceService.getBusinessService(sourceId);
                        bsNodes.put(sourceId, bs);
                    } catch (IllegalArgumentException e) {
                        log.debug("Skipping missing business service {} while building topology", sourceId);
                    }
                }

                matchedEdges.add(rel);
            }

            // Track cluster→host membership relations for topology
            if ("cluster".equals(sourceType)) {
                String desc = (String) rel.getOrDefault("description", "");
                if (MEMBERSHIP_RELATION.equals(desc) && clusterMap.containsKey(sourceId)) {
                    matchedEdges.add(rel);
                    if (!hostNodes.containsKey(targetId)) {
                        try {
                            Map<String, Object> h = hostService.getHost(targetId);
                            hostNodes.put(targetId, h);
                        } catch (IllegalArgumentException e) {
                            log.debug("Skipping missing host {} while building topology", targetId);
                        }
                    }
                }
            }
        }

        // Build nodes
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Map<String, Object> c : clusterMap.values()) {
            String cId = (String) c.get("id");
            String typeName = c.get("type") != null ? c.get("type").toString() : "";
            List<Map<String, Object>> clusterHosts = hostService.listHostsByCluster(cId);
            String mode = resolveClusterTypeMode(typeName);

            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", cId);
            node.put("name", c.get("name"));
            node.put("type", typeName);
            node.put("mode", mode);
            node.put("groupId", c.get("groupId"));
            node.put("hostCount", clusterHosts.size());
            node.put("nodeType", "cluster");
            nodes.add(node);

            // Add host nodes for this cluster
            for (Map<String, Object> h : clusterHosts) {
                String hId = (String) h.get("id");
                if (!hostNodes.containsKey(hId)) {
                    hostNodes.put(hId, h);
                }
            }
        }

        // Add host nodes
        for (Map<String, Object> h : hostNodes.values()) {
            String hClusterId = h.get("clusterId") != null ? h.get("clusterId").toString() : null;
            String hClusterType = null;
            if (hClusterId != null) {
                Map<String, Object> parentCluster = clusterMap.get(hClusterId);
                if (parentCluster != null && parentCluster.get("type") != null) {
                    hClusterType = parentCluster.get("type").toString();
                }
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", h.get("id"));
            node.put("name", h.get("name"));
            node.put("ip", h.get("ip"));
            node.put("clusterId", hClusterId);
            node.put("role", h.get("role"));
            node.put("clusterType", hClusterType);
            node.put("groupId", h.get("groupId") != null ? h.get("groupId") : null);
            node.put("hostCount", 0);
            node.put("nodeType", "host");
            nodes.add(node);
        }

        for (Map<String, Object> bs : bsNodes.values()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", bs.get("id"));
            node.put("name", bs.get("name"));
            node.put("type", "");
            node.put("groupId", bs.get("groupId"));
            node.put("hostCount", 0);
            node.put("nodeType", "business-service");
            nodes.add(node);
        }

        // Build edges
        List<Map<String, Object>> edges = new ArrayList<>();
        for (Map<String, Object> rel : matchedEdges) {
            String sourceType = (String) rel.getOrDefault("sourceType", "cluster");
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("source", rel.get("sourceId"));
            edge.put("target", rel.get("targetId"));
            edge.put("description", rel.get("description"));
            String desc = (String) rel.getOrDefault("description", "");
            if (MEMBERSHIP_RELATION.equals(desc)) {
                edge.put("type", "constitute");
            } else {
                edge.put("type", "business-service".equals(sourceType) ? "business-entry" : "cluster-relation");
            }
            edges.add(edge);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }

    // ── Neighbor Resolution ──────────────────────────────────────────

    /**
     * Get 1-hop neighbors (upstream + downstream) for a given cluster.
     *
     * @param clusterId cluster identifier
     * @return 1-hop neighbors (upstream + downstream) for a given cluster
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getClusterNeighbors(String clusterId) {
        // Validate cluster exists
        Map<String, Object> cluster = clusterService.getCluster(clusterId);
        String typeName = cluster.get("type") != null ? cluster.get("type").toString() : "";
        String mode = resolveClusterTypeMode(typeName);

        List<Map<String, Object>> hosts = hostService.listHostsByCluster(clusterId);
        // For primary-backup mode, filter to primary hosts only
        List<Map<String, Object>> activeHosts = "primary-backup".equals(mode) ? filterPrimaryHosts(hosts) : hosts;

        List<Map<String, Object>> allRelations = listRelations(null);
        List<Map<String, Object>> upstream = new ArrayList<>();
        List<Map<String, Object>> downstream = new ArrayList<>();

        for (Map<String, Object> rel : allRelations) {
            String sourceId = (String) rel.get("sourceId");
            String targetId = (String) rel.get("targetId");
            String sourceType = (String) rel.getOrDefault("sourceType", "cluster");

            // Skip business-service relations for cluster topology
            if ("business-service".equals(sourceType)) {
                continue;
            }

            if (clusterId.equals(sourceId)) {
                // Current cluster is source -> downstream
                try {
                    Map<String, Object> targetCluster = clusterService.getCluster(targetId);
                    String tn = targetCluster.get("type") != null ? targetCluster.get("type").toString() : "";
                    String tm = resolveClusterTypeMode(tn);
                    List<Map<String, Object>> targetHosts = hostService.listHostsByCluster(targetId);
                    List<Map<String, Object>> activeTargetHosts =
                        "primary-backup".equals(tm) ? filterPrimaryHosts(targetHosts) : targetHosts;

                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("cluster", buildClusterNode(targetCluster, tm, activeTargetHosts.size()));
                    entry.put("hosts", buildHostNodes(activeTargetHosts));
                    entry.put("direction", "outgoing");
                    entry.put("description", rel.get("description"));
                    downstream.add(entry);
                } catch (IllegalArgumentException e) {
                    log.debug("Skipping missing downstream cluster {}", targetId);
                }
            }
            if (clusterId.equals(targetId)) {
                // Current cluster is target -> upstream
                try {
                    Map<String, Object> sourceCluster = clusterService.getCluster(sourceId);
                    String sn = sourceCluster.get("type") != null ? sourceCluster.get("type").toString() : "";
                    String sm = resolveClusterTypeMode(sn);
                    List<Map<String, Object>> sourceHosts = hostService.listHostsByCluster(sourceId);
                    List<Map<String, Object>> activeSourceHosts =
                        "primary-backup".equals(sm) ? filterPrimaryHosts(sourceHosts) : sourceHosts;

                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("cluster", buildClusterNode(sourceCluster, sm, activeSourceHosts.size()));
                    entry.put("hosts", buildHostNodes(activeSourceHosts));
                    entry.put("direction", "incoming");
                    entry.put("description", rel.get("description"));
                    upstream.add(entry);
                } catch (IllegalArgumentException e) {
                    log.debug("Skipping missing upstream cluster {}", sourceId);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cluster", buildClusterNode(cluster, mode, activeHosts.size()));
        result.put("upstream", upstream);
        result.put("downstream", downstream);
        return result;
    }

    /**
     * Get host neighbors via cluster relations: resolve host -> cluster -> cluster neighbors -> flatten to host list.
     *
     * @param hostId host identifier
     *        to host list
     * @return host neighbors via cluster relations: resolve host -> cluster -> cluster neighbors -> flatten to host
     *         list
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getHostNeighborsByCluster(String hostId) {
        // 1. Resolve host -> clusterId
        Map<String, Object> host = hostService.getHost(hostId);
        Object clusterIdObj = host.get("clusterId");
        if (clusterIdObj == null || clusterIdObj.toString().isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("host", buildHostNode(host));
            result.put("upstream", List.of());
            result.put("downstream", List.of());
            result.put("totalNeighbors", 0);
            return result;
        }

        String clusterId = clusterIdObj.toString();

        // 2. Get cluster neighbors
        Map<String, Object> clusterNeighbors = getClusterNeighbors(clusterId);

        // 3. Flatten neighbor clusters' host lists
        List<Map<String, Object>> upstreamHosts = new ArrayList<>();
        for (Map<String, Object> up : (List<Map<String, Object>>) clusterNeighbors.get("upstream")) {
            upstreamHosts.addAll((List<Map<String, Object>>) up.get("hosts"));
        }

        List<Map<String, Object>> downstreamHosts = new ArrayList<>();
        for (Map<String, Object> down : (List<Map<String, Object>>) clusterNeighbors.get("downstream")) {
            downstreamHosts.addAll((List<Map<String, Object>>) down.get("hosts"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("host", buildHostNode(host));
        result.put("upstream", upstreamHosts);
        result.put("downstream", downstreamHosts);
        result.put("totalNeighbors", upstreamHosts.size() + downstreamHosts.size());
        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private String resolveClusterTypeMode(String typeName) {
        if (typeName == null || typeName.isEmpty() || clusterTypeService == null) {
            return "peer";
        }
        for (Map<String, Object> ct : clusterTypeService.listClusterTypes()) {
            String ctName = ct.get("name") != null ? ct.get("name").toString() : "";
            if (typeName.equals(ctName)) {
                Object modeObj = ct.get("mode");
                return modeObj != null ? modeObj.toString() : "peer";
            }
        }
        return "peer";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> filterPrimaryHosts(List<Map<String, Object>> hosts) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> h : hosts) {
            Object role = h.get("role");
            if (role == null || "primary".equals(role.toString())) {
                result.add(h);
            }
        }
        return result;
    }

    private void collectSubGroupClusters(String groupId, List<Map<String, Object>> clusters) {
        try {
            List<Map<String, Object>> allClusters = clusterService.listClusters(null, null);
            // Already collected direct clusters; nothing more needed since listClusters(groupId) already handles that
        } catch (IllegalArgumentException e) {
            log.debug("Skipping subgroup cluster collection for group {}", groupId);
        }
    }

    private Map<String, Object> buildClusterNode(Map<String, Object> cluster, String mode, int hostCount) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", cluster.get("id"));
        node.put("name", cluster.get("name"));
        node.put("type", cluster.get("type"));
        node.put("mode", mode);
        node.put("groupId", cluster.get("groupId"));
        node.put("hostCount", hostCount);
        node.put("nodeType", "cluster");
        return node;
    }

    private List<Map<String, Object>> buildHostNodes(List<Map<String, Object>> hosts) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> h : hosts) {
            result.add(buildHostNode(h));
        }
        return result;
    }

    private Map<String, Object> buildHostNode(Map<String, Object> h) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", h.get("id"));
        node.put("name", h.get("name"));
        node.put("ip", h.get("ip"));
        node.put("businessIp", h.get("businessIp"));
        String clusterId = h.get("clusterId") != null ? h.get("clusterId").toString() : null;
        node.put("clusterId", clusterId);
        // Resolve cluster info
        if (clusterId != null && clusterService != null) {
            try {
                Map<String, Object> cluster = clusterService.getCluster(clusterId);
                node.put("clusterType", cluster.get("type"));
                node.put("clusterName", cluster.get("name"));
            } catch (IllegalArgumentException e) {
                node.put("clusterType", null);
                node.put("clusterName", null);
            }
        } else {
            node.put("clusterType", null);
            node.put("clusterName", null);
        }
        node.put("role", h.get("role"));
        node.put("purpose", h.get("purpose"));
        node.put("groupId", clusterId != null ? getClusterGroupId(clusterId) : null);
        node.put("tags", h.get("tags"));
        return node;
    }

    private String getClusterGroupId(String clusterId) {
        try {
            Map<String, Object> cluster = clusterService.getCluster(clusterId);
            Object gid = cluster.get("groupId");
            return gid != null ? gid.toString() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── File I/O Helpers ─────────────────────────────────────────────

    private Map<String, Object> readFile(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (IOException e) {
            log.error("Failed to read cluster-relation file: {}", file, e);
            return null;
        }
    }

    private void writeEntityFile(String id, Map<String, Object> entity) {
        try {
            Files.createDirectories(relationsDir);
            Path file = relationsDir.resolve(id + ".json");
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(entity);
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to write cluster-relation file for id={}", id, e);
            throw new RuntimeException("Failed to save cluster relation", e);
        }
    }
}
