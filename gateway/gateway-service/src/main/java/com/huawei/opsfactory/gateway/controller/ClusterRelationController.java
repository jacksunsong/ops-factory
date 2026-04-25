package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.service.ClusterRelationService;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

@RestController
@RequestMapping("/gateway/cluster-relations")
public class ClusterRelationController {

    private static final Logger log = LoggerFactory.getLogger(ClusterRelationController.class);

    private final ClusterRelationService clusterRelationService;

    public ClusterRelationController(ClusterRelationService clusterRelationService) {
        this.clusterRelationService = clusterRelationService;
    }

    @GetMapping
    public Mono<Map<String, Object>> listRelations(
            @RequestParam(value = "clusterId", required = false) String clusterId,
            ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> relations = clusterRelationService.listRelations(clusterId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("relations", relations);
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/graph")
    public Mono<Map<String, Object>> getGraph(
            @RequestParam(value = "groupId", required = false) String groupId,
            ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> clusterRelationService.getGraphData(groupId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/clusters/{clusterId}/neighbors")
    public Mono<Map<String, Object>> getClusterNeighbors(
            @PathVariable("clusterId") String clusterId,
            ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> clusterRelationService.getClusterNeighbors(clusterId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/hosts/{hostId}/neighbors")
    public Mono<Map<String, Object>> getHostNeighbors(
            @PathVariable("hostId") String hostId,
            ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> clusterRelationService.getHostNeighborsByCluster(hostId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> createRelation(
            @RequestBody Map<String, Object> request,
            ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            try {
                Map<String, Object> relation = clusterRelationService.createRelation(request);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("relation", relation);
                return ResponseEntity.status(HttpStatus.CREATED).body(body);
            } catch (IllegalArgumentException e) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
            } catch (Exception e) {
                log.error("Failed to create cluster relation", e);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> updateRelation(
            @PathVariable("id") String id,
            @RequestBody Map<String, Object> request,
            ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            try {
                Map<String, Object> relation = clusterRelationService.updateRelation(id, request);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("relation", relation);
                return ResponseEntity.ok(body);
            } catch (IllegalArgumentException e) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", e.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            } catch (Exception e) {
                log.error("Failed to update cluster relation {}", id, e);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteRelation(
            @PathVariable("id") String id,
            ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            boolean deleted = clusterRelationService.deleteRelation(id);
            if (!deleted) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Cluster relation not found: " + id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            return ResponseEntity.ok(body);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
