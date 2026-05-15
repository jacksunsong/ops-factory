/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.service.BusinessServiceService;
import com.huawei.opsfactory.gateway.service.ClusterService;
import com.huawei.opsfactory.gateway.service.HostGroupService;
import com.huawei.opsfactory.gateway.service.HostService;

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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST controller for CRUD operations and connectivity testing on host entries.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RequestMapping("/gateway/hosts")
public class HostController {
    private static final Logger log = LoggerFactory.getLogger(HostController.class);

    private final HostService hostService;

    private final ClusterService clusterService;

    private final BusinessServiceService businessServiceService;

    private final HostGroupService hostGroupService;

    /**
     * Creates the host controller instance.
     */
    public HostController(HostService hostService, ClusterService clusterService,
        BusinessServiceService businessServiceService, HostGroupService hostGroupService) {
        this.hostService = hostService;
        this.clusterService = clusterService;
        this.businessServiceService = businessServiceService;
        this.hostGroupService = hostGroupService;
    }

    /**
     * Lists hosts, optionally filtered by tags, cluster, group, business service, or enabled status.
     *
     * @param tags tags
     * @param clusterId cluster identifier
     * @param groupId group identifier
     * @param businessServiceId business service id
     *        status
     * @param enabledOnly enabled-only filter flag
     * @param exchange server web exchange
     * @return the result
     */
    @GetMapping({"", "/"})
    public Mono<Map<String, Object>> listHosts(@RequestParam(value = "tags", required = false) String tags,
        @RequestParam(value = "clusterId", required = false) String clusterId,
        @RequestParam(value = "groupId", required = false) String groupId,
        @RequestParam(value = "businessServiceId", required = false) String businessServiceId,
        @RequestParam(value = "enabledOnly", required = false, defaultValue = "false") boolean enabledOnly,
        ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);

        return Mono.fromCallable(() -> {
            // Resolve disabled context once when enabledOnly is requested
            final Set<String> disabledGroupIds;
            final Set<String> disabledClusterIds;
            if (enabledOnly) {
                List<Map<String, Object>> allGroups = hostGroupService.listGroups();
                disabledGroupIds = hostGroupService.getDisabledGroupIds(allGroups);

                if (groupId != null && !groupId.isEmpty() && disabledGroupIds.contains(groupId)) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("hosts", List.of());
                    return result;
                }
                if (clusterId != null && !clusterId.isEmpty()) {
                    try {
                        Map<String, Object> cluster = clusterService.getCluster(clusterId);
                        if (Boolean.FALSE.equals(cluster.get("enabled"))
                            || disabledGroupIds.contains(cluster.get("groupId"))) {
                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("hosts", List.of());
                            return result;
                        }
                    } catch (IllegalArgumentException e) {
                        // Cluster not found, let normal flow handle it
                    }
                }
                // Build disabledClusterIds for the "list all hosts" fallback path
                List<Map<String, Object>> allClusters = clusterService.listClusters(null, null);
                disabledClusterIds = new java.util.HashSet<>();
                for (Map<String, Object> c : allClusters) {
                    if (Boolean.FALSE.equals(c.get("enabled")) || disabledGroupIds.contains(c.get("groupId"))) {
                        disabledClusterIds.add((String) c.get("id"));
                    }
                }
            } else {
                disabledGroupIds = Set.of();
                disabledClusterIds = Set.of();
            }

            List<Map<String, Object>> hosts;
            if (businessServiceId != null && !businessServiceId.isEmpty()) {
                hosts = businessServiceService.getHostsForBusinessService(businessServiceId);
            } else if (clusterId != null && !clusterId.isEmpty()) {
                hosts = hostService.listHostsByCluster(clusterId);
            } else if (groupId != null && !groupId.isEmpty()) {
                hosts = hostService.listHostsByGroup(groupId, clusterService);
            } else {
                List<String> tagList =
                    (tags != null && !tags.isBlank()) ? Arrays.asList(tags.split(",")) : Collections.emptyList();
                hosts = hostService.listHosts(tagList.toArray(new String[0]));
            }

            // Filter out hosts belonging to disabled clusters when enabledOnly=true
            if (enabledOnly && !disabledClusterIds.isEmpty()) {
                hosts.removeIf(h -> disabledClusterIds.contains(h.get("clusterId")));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("hosts", hosts);
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Gets a host by its IP address.
     *
     * @param ip ip
     * @param exchange server web exchange
     * @return a host by its IP address
     */
    @GetMapping("/by-ip")
    public Mono<ResponseEntity<Map<String, Object>>> getHostByIp(@RequestParam("ip") String ip,
        ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            Map<String, Object> host = hostService.findByIp(ip);
            if (host == null) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Host not found for IP: " + ip);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("host", host);
            return ResponseEntity.ok(body);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Gets a host by ID.
     *
     * @param id entity identifier
     * @param exchange server web exchange
     * @return a host by ID
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> getHost(@PathVariable("id") String id,
        ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            Map<String, Object> host;
            try {
                host = hostService.getHost(id);
            } catch (IllegalArgumentException e) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Host not found: " + id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
            if (host == null) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Host not found: " + id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("host", host);
            return ResponseEntity.ok(body);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Creates a new host.
     *
     * @param request HTTP request
     * @param exchange server web exchange
     * @return the result
     */
    @PostMapping({"", "/"})
    public Mono<ResponseEntity<Map<String, Object>>> createHost(@RequestBody Map<String, Object> request,
        ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            try {
                Map<String, Object> host = hostService.createHost(request);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("host", host);
                return ResponseEntity.status(HttpStatus.CREATED).body(body);
            } catch (IllegalArgumentException e) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Invalid host request");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Updates a host by ID.
     *
     * @param id a host by ID
     * @param request a host by ID
     * @param exchange a host by ID
     * @return the result
     */
    @PutMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> updateHost(@PathVariable("id") String id,
        @RequestBody Map<String, Object> request, ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            try {
                Map<String, Object> host = hostService.updateHost(id, request);
                if (host == null) {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("success", false);
                    body.put("error", "Host not found: " + id);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
                }
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("host", host);
                return ResponseEntity.ok(body);
            } catch (IllegalArgumentException e) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                HttpStatus status = e.getMessage() != null && e.getMessage().startsWith("Host not found:")
                    ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
                body.put("error", status == HttpStatus.NOT_FOUND ? "Host not found" : "Invalid host request");
                return ResponseEntity.status(status).body(body);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Deletes a host by ID.
     *
     * @param id entity identifier
     * @param exchange server web exchange
     * @return the result
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteHost(@PathVariable("id") String id,
        ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            boolean deleted = hostService.deleteHost(id);
            if (!deleted) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Host not found: " + id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            return ResponseEntity.ok(body);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Returns all unique host tags.
     *
     * @param exchange returns all unique host tags
     * @return all unique host tags
     */
    @GetMapping("/tags")
    public Mono<Map<String, Object>> getTags(ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            List<String> tags = hostService.getAllTags();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tags", tags);
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Tests SSH connectivity to a host.
     *
     * @param id tests SSH connectivity to a host
     * @param exchange tests SSH connectivity to a host
     * @return the tests SSH connectivity to a host
     */
    @PostMapping("/{id}/test")
    public Mono<Map<String, Object>> testConnectivity(@PathVariable("id") String id, ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            long startedAt = System.currentTimeMillis();
            String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
            log.info("Host connectivity test started hostId={} userId={}", id, userId);
            Map<String, Object> testResult = hostService.testConnection(id);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", testResult.get("success"));
            result.put("hostId", id);
            result.put("reachable", testResult.get("reachable"));
            result.put("latencyMs", testResult.get("latencyMs"));
            if (testResult.containsKey("error")) {
                result.put("error", testResult.get("error"));
            }
            Object success = result.get("success");
            Object reachable = result.get("reachable");
            Object latencyMs = result.get("latencyMs");
            log.info(
                "Host connectivity test completed hostId={} userId={} success={} reachable={} latencyMs={} "
                    + "durationMs={} testResultKeys={}",
                id, userId, success, reachable, latencyMs, System.currentTimeMillis() - startedAt, testResult.keySet());
            if (Boolean.FALSE.equals(success) && (reachable == null || latencyMs == null)) {
                log.warn("Host connectivity test returned missing fields hostId={} userId={} reachable={} latencyMs={} "
                    + "testResultKeys={}", id, userId, reachable, latencyMs, testResult.keySet());
                if (testResult.containsKey("message")) {
                    log.warn("Host connectivity test failure message hostId={} userId={} message={}", id, userId,
                        String.valueOf(testResult.get("message")));
                }
            }
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
