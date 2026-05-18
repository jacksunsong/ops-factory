/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.service.BusinessServiceService;
import com.huawei.opsfactory.gateway.service.ClusterService;
import com.huawei.opsfactory.gateway.service.HostGroupService;
import com.huawei.opsfactory.gateway.service.HostService;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST controller for CRUD operations on host group definitions and the group tree.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RequestMapping("/gateway/host-groups")
public class HostGroupController {
    private final HostGroupService hostGroupService;

    private final ClusterService clusterService;

    private final BusinessServiceService businessServiceService;

    private final HostService hostService;

    /**
     * Creates the host group controller instance.
     */
    public HostGroupController(HostGroupService hostGroupService, ClusterService clusterService,
        BusinessServiceService businessServiceService, HostService hostService) {
        this.hostGroupService = hostGroupService;
        this.clusterService = clusterService;
        this.businessServiceService = businessServiceService;
        this.hostService = hostService;
    }

    /**
     * Lists host groups, optionally filtered by enabled status.
     *
     * @param enabledOnly enabled-only filter flag
     * @param exchange server web exchange
     * @return the result
     */
    @GetMapping({"", "/"})
    public Mono<Map<String, Object>> listGroups(
        @RequestParam(value = "enabledOnly", required = false, defaultValue = "false") boolean enabledOnly,
        ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> groups = hostGroupService.listGroups();
            if (enabledOnly) {
                Set<String> disabledGroupIds = hostGroupService.getDisabledGroupIds(groups);
                groups.removeIf(g -> disabledGroupIds.contains(g.get("id")));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("groups", groups);
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Returns the hierarchical tree of groups, clusters, and business services.
     *
     * @param enabledOnly returns the hierarchical tree of groups, clusters, and business services
     * @param exchange returns the hierarchical tree of groups, clusters, and business services
     * @return the hierarchical tree of groups, clusters, and business services
     */
    @GetMapping("/tree")
    public Mono<Map<String, Object>> getTree(
        @RequestParam(value = "enabledOnly", required = false, defaultValue = "false") boolean enabledOnly,
        ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> groups = hostGroupService.listGroups();
            List<Map<String, Object>> clusters = clusterService.listClusters(null, null);
            List<Map<String, Object>> businessServices = businessServiceService.listBusinessServices(null, null);
            if (enabledOnly) {
                Set<String> disabledGroupIds = hostGroupService.getDisabledGroupIds(groups);
                groups.removeIf(g -> disabledGroupIds.contains(g.get("id")));
                clusters.removeIf(
                    c -> Boolean.FALSE.equals(c.get("enabled")) || disabledGroupIds.contains(c.get("groupId")));
                businessServices.removeIf(
                    bs -> Boolean.FALSE.equals(bs.get("enabled")) || disabledGroupIds.contains(bs.get("groupId")));
            }
            return hostGroupService.getTree(groups, clusters, businessServices);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Gets a host group by ID.
     *
     * @param id entity identifier
     * @param exchange server web exchange
     * @return a host group by ID
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> getGroup(@PathVariable("id") String id,
        ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                Map<String, Object> group = hostGroupService.getGroup(id);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("group", group);
                return ResponseEntity.ok(body);
            } catch (IllegalArgumentException e) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Host group not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Creates a new host group.
     *
     * @param request HTTP request
     * @param exchange server web exchange
     * @return the result
     */
    @PostMapping({"", "/"})
    public Mono<ResponseEntity<Map<String, Object>>> createGroup(@RequestBody Map<String, Object> request,
        ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                Map<String, Object> group = hostGroupService.createGroup(request);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("group", group);
                return ResponseEntity.status(HttpStatus.CREATED).body(body);
            } catch (IllegalArgumentException e) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Invalid host group request");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Updates a host group by ID.
     *
     * @param id a host group by ID
     * @param request a host group by ID
     * @param exchange a host group by ID
     * @return the result
     */
    @PutMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> updateGroup(@PathVariable("id") String id,
        @RequestBody Map<String, Object> request, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                Map<String, Object> group = hostGroupService.updateGroup(id, request);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("group", group);
                return ResponseEntity.ok(body);
            } catch (IllegalArgumentException e) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Host group not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Deletes a host group by ID, optionally forcing deletion of associated resources.
     *
     * @param id entity identifier
     * @param force whether to force the operation
     * @param exchange server web exchange
     * @return the result
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteGroup(@PathVariable("id") String id,
        @RequestParam(value = "force", required = false, defaultValue = "false") boolean force,
        ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                boolean deleted;
                if (force) {
                    deleted =
                        hostGroupService.forceDeleteGroup(id, clusterService, hostService, businessServiceService);
                } else {
                    deleted = hostGroupService.deleteGroup(id, clusterService);
                }
                if (!deleted) {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("success", false);
                    body.put("error", "Host group not found: " + id);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
                }
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                return ResponseEntity.ok(body);
            } catch (IllegalStateException e) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Host group delete conflict");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
