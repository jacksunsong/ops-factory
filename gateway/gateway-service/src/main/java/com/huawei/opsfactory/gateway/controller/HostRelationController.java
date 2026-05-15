/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.service.BusinessServiceService;
import com.huawei.opsfactory.gateway.service.HostRelationService;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @deprecated Use {@link ClusterRelationController} instead. Host-level relations are replaced by cluster-level
 *             relations.
 */
@Deprecated
@RestController
@RequestMapping("/gateway/host-relations")
public class HostRelationController {
    private static final Logger log = LoggerFactory.getLogger(HostRelationController.class);

    private final HostRelationService hostRelationService;

    private final BusinessServiceService businessServiceService;

    /**
     * Creates the host relation controller instance.
     */
    public HostRelationController(HostRelationService hostRelationService,
        BusinessServiceService businessServiceService) {
        this.hostRelationService = hostRelationService;
        this.businessServiceService = businessServiceService;
    }

    /**
     * Lists host relations, optionally filtered by host, group, cluster, or source.
     *
     * @param hostId host identifier
     * @param groupId group identifier
     * @param clusterId cluster identifier
     * @param sourceType source type filter
     * @param sourceId source identifier filter
     * @param exchange server web exchange
     * @return the result
     */
    @GetMapping
    public Mono<Map<String, Object>> listRelations(@RequestParam(value = "hostId", required = false) String hostId,
        @RequestParam(value = "groupId", required = false) String groupId,
        @RequestParam(value = "clusterId", required = false) String clusterId,
        @RequestParam(value = "sourceType", required = false) String sourceType,
        @RequestParam(value = "sourceId", required = false) String sourceId, ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> relations =
                hostRelationService.listRelations(hostId, groupId, clusterId, sourceType, sourceId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("relations", relations);
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Returns the host relation graph data enriched with business services.
     *
     * @param groupId returns the host relation graph data enriched with business services
     * @param clusterId returns the host relation graph data enriched with business services
     * @param exchange returns the host relation graph data enriched with business services
     * @return the host relation graph data enriched with business services
     */
    @GetMapping("/graph")
    public Mono<Map<String, Object>> getGraph(@RequestParam(value = "groupId", required = false) String groupId,
        @RequestParam(value = "clusterId", required = false) String clusterId, ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            Map<String, Object> graph = hostRelationService.getGraphData(groupId, clusterId);
            enrichWithBusinessServices(graph, groupId, clusterId);
            return graph;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Returns the neighbor hosts for a given host.
     *
     * @param hostId returns the neighbor hosts for a given host
     * @param exchange returns the neighbor hosts for a given host
     * @return the neighbor hosts for a given host
     */
    @GetMapping("/hosts/{hostId}/neighbors")
    public Mono<Map<String, Object>> getHostNeighbors(@PathVariable("hostId") String hostId,
        ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> hostRelationService.getNeighbors(hostId))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Creates a new host relation edge.
     *
     * @param request HTTP request
     * @param exchange server web exchange
     * @return the result
     */
    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> createRelation(@RequestBody Map<String, Object> request,
        ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            try {
                Map<String, Object> relation = hostRelationService.createRelation(request);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("relation", relation);
                return ResponseEntity.status(HttpStatus.CREATED).body(body);
            } catch (IllegalArgumentException e) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Invalid host relation request");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Updates a host relation by ID.
     *
     * @param id a host relation by ID
     * @param request a host relation by ID
     * @param exchange a host relation by ID
     * @return the result
     */
    @PutMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> updateRelation(@PathVariable("id") String id,
        @RequestBody Map<String, Object> request, ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            try {
                Map<String, Object> relation = hostRelationService.updateRelation(id, request);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("relation", relation);
                return ResponseEntity.ok(body);
            } catch (IllegalArgumentException e) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Host relation not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Deletes a host relation by ID.
     *
     * @param id entity identifier
     * @param exchange server web exchange
     * @return the result
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteRelation(@PathVariable("id") String id,
        ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            boolean deleted = hostRelationService.deleteRelation(id);
            if (!deleted) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Host relation not found: " + id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            return ResponseEntity.ok(body);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @SuppressWarnings("unchecked")
    private void enrichWithBusinessServices(Map<String, Object> graph, String groupId, String clusterId) {
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        List<Map<String, Object>> edges = (List<Map<String, Object>>) graph.get("edges");

        // Collect existing host node IDs
        Set<String> hostNodeIds = new HashSet<>();
        for (Map<String, Object> node : nodes) {
            hostNodeIds.add((String) node.get("id"));
        }

        // Fetch all business services, then filter by overlap with current graph hosts.
        // We cannot use listBusinessServices(groupId) because the groupId param may be
        // a province-level (parent) group while BS records store the direct child groupId.
        List<Map<String, Object>> bsList = businessServiceService.listBusinessServices(null, null);

        int addedBs = 0;
        for (Map<String, Object> bs : bsList) {
            String bsId = (String) bs.get("id");
            // Only include BS whose entry hosts overlap with current graph hosts
            List<String> bsHostIds = (List<String>) bs.getOrDefault("hostIds", Collections.emptyList());
            boolean hasOverlap = false;
            for (String hid : bsHostIds) {
                if (hostNodeIds.contains(hid)) {
                    hasOverlap = true;
                    break;
                }
            }
            if (!hasOverlap) {
                continue;
            }

            // Add BS node
            Map<String, Object> bsNode = new LinkedHashMap<>();
            bsNode.put("id", bsId);
            bsNode.put("name", bs.get("name"));
            bsNode.put("ip", null);
            bsNode.put("clusterType", null);
            bsNode.put("clusterName", null);
            bsNode.put("purpose", null);
            bsNode.put("groupId", bs.get("groupId"));
            bsNode.put("nodeType", "business-service");
            nodes.add(bsNode);

            // Add edges from BS to each entry host that exists in the graph, using actual relation descriptions
            List<Map<String, Object>> bsRelations =
                hostRelationService.listRelations(null, null, null, "business-service", bsId);
            for (Map<String, Object> rel : bsRelations) {
                String targetId = (String) rel.get("targetHostId");
                if (targetId != null && hostNodeIds.contains(targetId)) {
                    Map<String, Object> edge = new LinkedHashMap<>();
                    edge.put("source", bsId);
                    edge.put("target", targetId);
                    edge.put("description", rel.getOrDefault("description", ""));
                    edge.put("type", "business-entry");
                    edges.add(edge);
                }
            }
            addedBs++;
        }
        log.info("enrichWithBusinessServices: added {} BS nodes to graph", addedBs);
    }
}
