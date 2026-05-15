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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

/**
 * Provides CRUD operations, topology queries, and host-id synchronization for business services.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Service
public class BusinessServiceService {
    private static final Logger log = LoggerFactory.getLogger(BusinessServiceService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GatewayProperties properties;

    private Path businessServicesDir;

    private ClusterService clusterService;

    private HostService hostService;

    private HostRelationService hostRelationService;

    private ClusterRelationService clusterRelationService;

    /**
     * Creates the business service service instance.
     */
    public BusinessServiceService(GatewayProperties properties) {
        this.properties = properties;
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
     * Sets the host relation service via lazy injection.
     *
     * @param hostRelationService the host relation service via lazy injection
     */
    @Lazy
    @Autowired
    public void setHostRelationService(HostRelationService hostRelationService) {
        this.hostRelationService = hostRelationService;
    }

    /**
     * Sets the cluster relation service via lazy injection.
     *
     * @param clusterRelationService the cluster relation service via lazy injection
     */
    @Lazy
    @Autowired
    public void setClusterRelationService(ClusterRelationService clusterRelationService) {
        this.clusterRelationService = clusterRelationService;
    }

    /**
     * Initializes the business services data directory at startup.
     */
    @PostConstruct
    public void init() {
        Path gatewayRoot = properties.getGatewayRootPath();
        this.businessServicesDir = gatewayRoot.resolve("data").resolve("business-services");
        try {
            Files.createDirectories(businessServicesDir);
        } catch (IOException e) {
            log.error("Failed to create business-services directory: {}", businessServicesDir, e);
        }
        log.info("BusinessServiceService initialized, businessServicesDir={}", businessServicesDir);
    }

    // ── CRUD Operations ──────────────────────────────────────────────

    /**
     * Lists business services optionally filtered by group ID and host ID.
     *
     * @param groupId group identifier
     * @param hostId host identifier
     * @return the result
     */
    public List<Map<String, Object>> listBusinessServices(String groupId, String hostId) {
        List<Map<String, Object>> services = new ArrayList<>();
        if (!Files.isDirectory(businessServicesDir)) {
            return services;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(businessServicesDir, "*.json")) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                Map<String, Object> bs = readFile(file);
                if (bs == null) {
                    continue;
                }
                // Filter by groupId
                if (groupId != null && !groupId.isEmpty()) {
                    Object bsGroupId = bs.get("groupId");
                    if (!groupId.equals(bsGroupId)) {
                        continue;
                    }
                }
                // Filter by hostId — check if hostIds list contains it
                if (hostId != null && !hostId.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    List<String> hostIds = (List<String>) bs.get("hostIds");
                    if (hostIds == null || !hostIds.contains(hostId)) {
                        continue;
                    }
                }
                services.add(bs);
            }
        } catch (IOException e) {
            log.error("Failed to list business-services from {}", businessServicesDir, e);
        }
        return services;
    }

    /**
     * Gets a business service by its ID.
     *
     * @param id entity identifier
     * @return a business service by its ID
     */
    public Map<String, Object> getBusinessService(String id) {
        Path file = businessServicesDir.resolve(id + ".json");
        Map<String, Object> bs = readFile(file);
        if (bs == null) {
            throw new IllegalArgumentException("Business service not found: " + id);
        }
        return bs;
    }

    /**
     * Creates a new business service from the provided field map.
     *
     * @param body request body
     * @return the result
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createBusinessService(Map<String, Object> body) {
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        Map<String, Object> bs = new LinkedHashMap<>();
        bs.put("id", id);
        bs.put("name", body.getOrDefault("name", ""));
        bs.put("code", body.getOrDefault("code", ""));
        bs.put("groupId", body.getOrDefault("groupId", null));
        bs.put("description", body.getOrDefault("description", ""));
        bs.put("hostIds", body.getOrDefault("hostIds", new ArrayList<String>()));
        bs.put("tags", body.getOrDefault("tags", new ArrayList<String>()));
        bs.put("priority", body.getOrDefault("priority", ""));
        bs.put("contactInfo", body.getOrDefault("contactInfo", ""));
        bs.put("businessTypeId", body.getOrDefault("businessTypeId", null));
        bs.put("enabled", body.getOrDefault("enabled", true));
        bs.put("createdAt", now);
        bs.put("updatedAt", now);

        writeEntityFile(id, bs);
        log.info("Created business service: id={}, name={}, code={}", id, bs.get("name"), bs.get("code"));
        return bs;
    }

    /**
     * Updates an existing business service with the provided field map.
     *
     * @param id an existing business service with the provided field map
     * @param body an existing business service with the provided field map
     * @return the result
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> updateBusinessService(String id, Map<String, Object> body) {
        Path file = businessServicesDir.resolve(id + ".json");
        Map<String, Object> bs = readFile(file);
        if (bs == null) {
            throw new IllegalArgumentException("Business service not found: " + id);
        }

        if (body.containsKey("name")) {
            bs.put("name", body.get("name"));
        }
        if (body.containsKey("code")) {
            bs.put("code", body.get("code"));
        }
        if (body.containsKey("groupId")) {
            bs.put("groupId", body.get("groupId"));
        }
        if (body.containsKey("description")) {
            bs.put("description", body.get("description"));
        }
        if (body.containsKey("hostIds")) {
            bs.put("hostIds", body.get("hostIds"));
        }
        if (body.containsKey("tags")) {
            bs.put("tags", body.get("tags"));
        }
        if (body.containsKey("priority")) {
            bs.put("priority", body.get("priority"));
        }
        if (body.containsKey("contactInfo")) {
            bs.put("contactInfo", body.get("contactInfo"));
        }
        if (body.containsKey("businessTypeId")) {
            bs.put("businessTypeId", body.get("businessTypeId"));
        }
        if (body.containsKey("enabled")) {
            bs.put("enabled", body.get("enabled"));
        }

        bs.put("updatedAt", Instant.now().toString());
        writeEntityFile(id, bs);
        log.info("Updated business service: id={}", id);
        return bs;
    }

    /**
     * Deletes a business service by ID, cascading to related host and cluster relations.
     *
     * @param id entity identifier
     * @return the result
     */
    public boolean deleteBusinessService(String id) {
        // Cascade delete related HostRelation records
        if (hostRelationService != null) {
            hostRelationService.deleteRelationsByBusinessService(id);
        }
        // Cascade delete related ClusterRelation records
        if (clusterRelationService != null) {
            clusterRelationService.deleteRelationsByBusinessService(id);
        }

        Path file = businessServicesDir.resolve(id + ".json");
        try {
            if (Files.exists(file)) {
                Files.delete(file);
                log.info("Deleted business service: id={}", id);
                return true;
            }
            return false;
        } catch (IOException e) {
            log.error("Failed to delete business-service file: {}", file, e);
            return false;
        }
    }

    // ── Query Methods ────────────────────────────────────────────────

    /**
     * Get business service with resolved host info.
     *
     * @param id entity identifier
     * @return business service with resolved host info
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getWithResolvedHosts(String id) {
        Map<String, Object> bs = getBusinessService(id);

        List<String> hostIds = (List<String>) bs.getOrDefault("hostIds", new ArrayList<>());
        List<Map<String, Object>> resolvedHosts = new ArrayList<>();

        for (String hid : hostIds) {
            try {
                Map<String, Object> host = hostService.getHost(hid);
                resolvedHosts.add(host);
            } catch (IllegalArgumentException e) {
                log.warn("Host {} not found for business service {}", hid, id);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>(bs);
        result.put("resolvedHosts", resolvedHosts);
        result.put("totalHostCount", resolvedHosts.size());
        return result;
    }

    /**
     * Get hosts for the entry resources of a business service.
     *
     * @param id entity identifier
     * @return hosts for the entry resources of a business service
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getHostsForBusinessService(String id) {
        Map<String, Object> bs = getBusinessService(id);
        List<String> hostIds = (List<String>) bs.getOrDefault("hostIds", new ArrayList<>());

        List<Map<String, Object>> hosts = new ArrayList<>();
        for (String hid : hostIds) {
            try {
                hosts.add(hostService.getHost(hid));
            } catch (IllegalArgumentException e) {
                log.warn("Host {} not found for business service {}", hid, id);
            }
        }
        return hosts;
    }

    /**
     * Get topology for a business service: entry hosts + N-hop downstream expansion.
     * Returns { nodes, edges }
     *
     * @param id entity identifier
     * @return topology for a business service: entry hosts + N-hop downstream expansion
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getTopologyForBusinessService(String id) {
        Map<String, Object> bs = getBusinessService(id);
        List<String> entryHostIdsList = (List<String>) bs.getOrDefault("hostIds", new ArrayList<>());

        // 1. Get entry hosts directly
        Map<String, Map<String, Object>> hostMap = new LinkedHashMap<>();
        Map<String, Boolean> entryHostIds = new LinkedHashMap<>();

        for (String hid : entryHostIdsList) {
            try {
                Map<String, Object> h = hostService.getHost(hid);
                hostMap.put(hid, h);
                entryHostIds.put(hid, true);
            } catch (IllegalArgumentException e) {
                log.warn("Entry host {} not found for business service {}", hid, id);
            }
        }

        // 2. BFS along outgoing relations for up to 5 hops
        int maxHops = 5;
        LinkedHashSet<String> frontier = new LinkedHashSet<>(entryHostIds.keySet());
        List<Map<String, Object>> allRelations = listAllRelations();

        for (int hop = 0; hop < maxHops && !frontier.isEmpty(); hop++) {
            LinkedHashSet<String> nextFrontier = new LinkedHashSet<>();
            for (Map<String, Object> rel : allRelations) {
                String sourceId = (String) rel.get("sourceHostId");
                String targetId = (String) rel.get("targetHostId");
                if (frontier.contains(sourceId) && !hostMap.containsKey(targetId)) {
                    try {
                        Map<String, Object> th = hostService.getHost(targetId);
                        hostMap.put(targetId, th);
                        nextFrontier.add(targetId);
                    } catch (IllegalArgumentException e) {
                        log.debug("Skipping missing downstream host {} for business service {}", targetId, id);
                    }
                }
            }
            frontier = nextFrontier;
        }

        // 3. Collect all edges between discovered hosts
        List<Map<String, Object>> edges = new ArrayList<>();
        for (Map<String, Object> rel : allRelations) {
            String sourceId = (String) rel.get("sourceHostId");
            String targetId = (String) rel.get("targetHostId");
            if (hostMap.containsKey(sourceId) && hostMap.containsKey(targetId)) {
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("source", sourceId);
                edge.put("target", targetId);
                edge.put("description", rel.get("description"));
                edges.add(edge);
            }
        }

        // 4. Build cluster lookup
        Map<String, Map<String, Object>> clusterMap = new LinkedHashMap<>();
        for (Map<String, Object> c : clusterService.listClusters(null, null)) {
            clusterMap.put((String) c.get("id"), c);
        }

        // 5. Build nodes with isEntry flag
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : hostMap.entrySet()) {
            Map<String, Object> node = buildHostNode(entry.getValue(), clusterMap);
            node.put("isEntry", entryHostIds.containsKey(entry.getKey()));
            nodes.add(node);
        }

        // 6. Add BS node as topology root and BS→entry host edges
        Map<String, Object> bsNode = new LinkedHashMap<>();
        bsNode.put("id", bs.get("id"));
        bsNode.put("name", bs.get("name"));
        bsNode.put("ip", null);
        bsNode.put("clusterType", null);
        bsNode.put("clusterName", null);
        bsNode.put("purpose", null);
        bsNode.put("groupId", bs.get("groupId"));
        bsNode.put("nodeType", "business-service");
        nodes.add(0, bsNode);

        List<Map<String, Object>> bsRelations =
            hostRelationService.listRelations(null, null, null, "business-service", id);
        for (Map<String, Object> rel : bsRelations) {
            String targetId = (String) rel.get("targetHostId");
            if (targetId != null && hostMap.containsKey(targetId)) {
                Map<String, Object> bsEdge = new LinkedHashMap<>();
                bsEdge.put("source", bs.get("id"));
                bsEdge.put("target", targetId);
                bsEdge.put("description", rel.getOrDefault("description", ""));
                bsEdge.put("type", "business-entry");
                edges.add(0, bsEdge);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }

    /**
     * Migrate from Host.business field: group by (businessName, groupId) -> create BusinessService.
     *
     * @return the migrate from Host.business field: group by (businessName, groupId) -> create BusinessService
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> migrateFromBusinessField() {
        List<Map<String, Object>> allHosts = hostService.listHosts(new String[0]);
        List<Map<String, Object>> allClusters = clusterService.listClusters(null, null);

        // Build cluster -> groupId map
        Map<String, String> clusterGroupMap = new LinkedHashMap<>();
        for (Map<String, Object> c : allClusters) {
            String cid = (String) c.get("id");
            String gid = (String) c.get("groupId");
            if (cid != null && gid != null) {
                clusterGroupMap.put(cid, gid);
            }
        }

        // Group hosts by (businessName, groupId)
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> host : allHosts) {
            String business = (String) host.get("business");
            if (business == null || business.trim().isEmpty()) {
                continue;
            }
            String clusterId = (String) host.get("clusterId");
            String groupId = clusterId != null ? clusterGroupMap.get(clusterId) : null;
            String key = business + "@" + (groupId != null ? groupId : "unknown");

            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(host);
        }

        List<Map<String, Object>> created = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            List<Map<String, Object>> hosts = entry.getValue();
            String business = (String) hosts.get(0).get("business");

            // Collect unique hostIds
            LinkedHashSet<String> hostIds = new LinkedHashSet<>();
            String groupId = null;
            for (Map<String, Object> h : hosts) {
                String hid = (String) h.get("id");
                if (hid != null) {
                    hostIds.add(hid);
                }
                String cid = (String) h.get("clusterId");
                if (cid != null && groupId == null) {
                    groupId = clusterGroupMap.get(cid);
                }
            }

            // Check if a business service with same name+groupId already exists
            boolean exists = false;
            for (Map<String, Object> existing : listBusinessServices(groupId, null)) {
                if (business.equals(existing.get("name"))) {
                    exists = true;
                    break;
                }
            }
            if (exists) {
                continue;
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", business);
            body.put("code", "");
            body.put("groupId", groupId);
            body.put("description", business);
            body.put("hostIds", new ArrayList<>(hostIds));
            body.put("tags", List.of(business));
            body.put("priority", "");

            Map<String, Object> createdBs = createBusinessService(body);
            created.add(createdBs);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("migrated", created.size());
        result.put("businessServices", created);
        log.info("Migration complete: created {} business services", created.size());
        return result;
    }

    // ── Keyword search ───────────────────────────────────────────────

    /**
     * Sync hostIds on a business service from its HostRelation records.
     *
     * @param bsId bs id
     */
    @SuppressWarnings("unchecked")
    public void syncHostIdsFromRelations(String bsId) {
        Map<String, Object> bs = getBusinessService(bsId);
        List<Map<String, Object>> rels = hostRelationService.listRelations(null, null, null, "business-service", bsId);
        List<String> newHostIds = rels.stream().map(r -> (String) r.get("targetHostId")).collect(Collectors.toList());
        bs.put("hostIds", newHostIds);
        bs.put("updatedAt", Instant.now().toString());
        writeEntityFile(bsId, bs);
    }

    /**
     * Sync hostIds on a business service from its ClusterRelation records.
     * Derives entry hosts from ClusterRelation where sourceType="business-service" and sourceId=bsId.
     * Resolves targetId (cluster) -> get cluster's hosts -> populate BS.hostIds.
     *
     * @param bsId bs id
     */
    @SuppressWarnings("unchecked")
    public void syncHostIdsFromClusterRelations(String bsId) {
        Map<String, Object> bs = getBusinessService(bsId);
        List<Map<String, Object>> allClusterRels = clusterRelationService.listRelations(null);
        List<String> newHostIds = new ArrayList<>();
        for (Map<String, Object> rel : allClusterRels) {
            String sourceType = (String) rel.getOrDefault("sourceType", "cluster");
            String sourceId = (String) rel.get("sourceId");
            if (!"business-service".equals(sourceType) || !bsId.equals(sourceId)) {
                continue;
            }
            String targetClusterId = (String) rel.get("targetId");
            if (targetClusterId == null) {
                continue;
            }
            List<Map<String, Object>> clusterHosts = hostService.listHostsByCluster(targetClusterId);
            for (Map<String, Object> h : clusterHosts) {
                String hid = (String) h.get("id");
                if (hid != null && !newHostIds.contains(hid)) {
                    newHostIds.add(hid);
                }
            }
        }
        bs.put("hostIds", newHostIds);
        bs.put("updatedAt", Instant.now().toString());
        writeEntityFile(bsId, bs);
    }

    /**
     * Remove a host from all business services' hostIds (called when a host is deleted).
     *
     * @param hostId remove a host from all business services' hostIds (called when a host is deleted)
     */
    @SuppressWarnings("unchecked")
    public void removeHostFromAllBusinessServices(String hostId) {
        List<Map<String, Object>> allBs = listBusinessServices(null, null);
        for (Map<String, Object> bs : allBs) {
            List<String> hostIds = (List<String>) bs.getOrDefault("hostIds", new ArrayList<>());
            if (hostIds.remove(hostId)) {
                bs.put("hostIds", hostIds);
                bs.put("updatedAt", Instant.now().toString());
                writeEntityFile((String) bs.get("id"), bs);
            }
        }
    }

    // ── Keyword search ───────────────────────────────────────────────

    /**
     * Search business services by keyword matching against name, code, and tags.
     *
     * @param keyword search business services by keyword matching against name, code, and tags
     * @return the search business services by keyword matching against name, code, and tags
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchByKeyword(String keyword) {
        List<Map<String, Object>> all = listBusinessServices(null, null);
        if (keyword == null || keyword.trim().isEmpty()) {
            return all;
        }
        String kw = keyword.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> bs : all) {
            String name = String.valueOf(bs.getOrDefault("name", "")).toLowerCase(Locale.ROOT);
            String code = String.valueOf(bs.getOrDefault("code", "")).toLowerCase(Locale.ROOT);
            List<String> tags = (List<String>) bs.getOrDefault("tags", new ArrayList<>());

            boolean match = name.contains(kw) || code.contains(kw);
            if (!match) {
                for (String tag : tags) {
                    if (tag.toLowerCase(Locale.ROOT).contains(kw)) {
                        match = true;
                        break;
                    }
                }
            }
            if (match) {
                results.add(bs);
            }
        }
        return results;
    }

    // ── Internal helpers ─────────────────────────────────────────────

    private List<Map<String, Object>> listAllRelations() {
        List<Map<String, Object>> relations = new ArrayList<>();
        Path relDir = businessServicesDir.getParent().resolve("host-relations");
        if (!Files.isDirectory(relDir)) {
            return relations;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(relDir, "*.json")) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                Map<String, Object> rel = readFile(file);
                if (rel != null) {
                    relations.add(rel);
                }
            }
        } catch (IOException e) {
            log.error("Failed to list relations", e);
        }
        return relations;
    }

    private Map<String, Object> buildHostNode(Map<String, Object> h, Map<String, Map<String, Object>> clusterMap) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", h.get("id"));
        node.put("name", h.get("name"));
        node.put("ip", h.get("ip"));
        node.put("businessIp", h.get("businessIp"));
        String hostClusterId = h.get("clusterId") != null ? h.get("clusterId").toString() : null;
        node.put("clusterId", hostClusterId);
        Map<String, Object> cluster = hostClusterId != null ? clusterMap.get(hostClusterId) : null;
        node.put("clusterType", cluster != null ? cluster.get("type") : null);
        node.put("clusterName", cluster != null ? cluster.get("name") : null);
        node.put("purpose", h.get("purpose"));
        node.put("groupId", cluster != null ? cluster.get("groupId") : null);
        node.put("tags", h.get("tags"));
        return node;
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
            log.error("Failed to read business-service file: {}", file, e);
            return null;
        }
    }

    private void writeEntityFile(String id, Map<String, Object> entity) {
        try {
            Files.createDirectories(businessServicesDir);
            Path file = businessServicesDir.resolve(id + ".json");
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(entity);
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to write business-service file for id={}", id, e);
            throw new RuntimeException("Failed to save business service", e);
        }
    }
}
