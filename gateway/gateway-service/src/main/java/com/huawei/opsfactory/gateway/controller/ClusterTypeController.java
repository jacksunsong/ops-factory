/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.service.ClusterTypeService;

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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for CRUD operations on cluster type definitions.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RequestMapping("/gateway/cluster-types")
public class ClusterTypeController {
    private final ClusterTypeService clusterTypeService;

    /**
     * Creates the cluster type controller instance.
     *
     * @param clusterTypeService service handling cluster type CRUD operations
     */
    public ClusterTypeController(ClusterTypeService clusterTypeService) {
        this.clusterTypeService = clusterTypeService;
    }

    /**
     * Lists all cluster type definitions.
     *
     * @param exchange current HTTP exchange carrying user context
     * @return Mono emitting a map with "clusterTypes" list
     */
    @GetMapping
    public Mono<Map<String, Object>> listClusterTypes(ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> types = clusterTypeService.listClusterTypes();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("clusterTypes", types);
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Gets a cluster type by ID.
     *
     * @param id cluster type identifier
     * @param exchange current HTTP exchange carrying user context
     * @return Mono emitting ResponseEntity containing the cluster type or 404
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> getClusterType(@PathVariable("id") String id,
        ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                Map<String, Object> ct = clusterTypeService.getClusterType(id);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("clusterType", ct);
                return ResponseEntity.ok(body);
            } catch (IllegalArgumentException e) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Cluster type not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Creates a new cluster type.
     *
     * @param request request body containing cluster type fields
     * @param exchange current HTTP exchange carrying user context
     * @return Mono emitting ResponseEntity with created cluster type or 400
     */
    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> createClusterType(@RequestBody Map<String, Object> request,
        ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                Map<String, Object> ct = clusterTypeService.createClusterType(request);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("clusterType", ct);
                return ResponseEntity.status(HttpStatus.CREATED).body(body);
            } catch (IllegalArgumentException e) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Invalid cluster type request");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Updates a cluster type by ID.
     *
     * @param id cluster type identifier
     * @param request request body containing updated fields
     * @param exchange current HTTP exchange carrying user context
     * @return Mono emitting ResponseEntity with updated cluster type or 404
     */
    @PutMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> updateClusterType(@PathVariable("id") String id,
        @RequestBody Map<String, Object> request, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                Map<String, Object> ct = clusterTypeService.updateClusterType(id, request);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("clusterType", ct);
                return ResponseEntity.ok(body);
            } catch (IllegalArgumentException e) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Cluster type not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Deletes a cluster type by ID.
     *
     * @param id cluster type identifier
     * @param exchange current HTTP exchange carrying user context
     * @return Mono emitting ResponseEntity with success status or 404
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteClusterType(@PathVariable("id") String id,
        ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            boolean deleted = clusterTypeService.deleteClusterType(id);
            if (!deleted) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Cluster type not found: " + id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            return ResponseEntity.ok(body);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
