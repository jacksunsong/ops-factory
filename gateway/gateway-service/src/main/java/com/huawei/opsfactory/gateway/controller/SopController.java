/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.service.SopService;

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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for CRUD operations on SOP (Standard Operating Procedure) definitions.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RequestMapping("/gateway/sops")
public class SopController {
    private static final Logger log = LoggerFactory.getLogger(SopController.class);

    private final SopService sopService;

    /**
     * Creates the sop controller instance.
     *
     * @param sopService service handling SOP persistence and business logic
     */
    public SopController(SopService sopService) {
        this.sopService = sopService;
    }

    /**
     * Lists all SOP definitions.
     *
     * @param exchange the current server web exchange, used for admin role verification
     * @return a mono wrapping a map containing the list of all SOP definitions under the {@code sops} key
     */
    @GetMapping({"", "/"})
    public Mono<Map<String, Object>> listSops(ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> sops = sopService.listSops();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sops", sops);
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Gets an SOP by ID.
     *
     * @param id the unique identifier of the SOP to retrieve
     * @param exchange the current server web exchange, used for admin role verification
     * @return a mono wrapping a response entity with the SOP details, or 404 if not found
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> getSop(@PathVariable("id") String id, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            Map<String, Object> sop = sopService.getSop(id);
            if (sop == null) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "SOP not found: " + id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("sop", sop);
            return ResponseEntity.ok(body);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Creates a new SOP definition.
     *
     * @param request the SOP definition fields to create, provided as a JSON request body
     * @param exchange the current server web exchange, used for admin role verification
     * @return a mono wrapping a response entity with the created SOP and 201 status,
     *         or 409 if a duplicate name already exists
     */
    @PostMapping({"", "/"})
    public Mono<ResponseEntity<Map<String, Object>>> createSop(@RequestBody Map<String, Object> request,
        ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                Map<String, Object> sop = sopService.createSop(request);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("sop", sop);
                return ResponseEntity.status(HttpStatus.CREATED).body(body);
            } catch (IllegalArgumentException e) {
                log.warn("Duplicate SOP name: {}", e.getMessage());
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "SOP name already exists");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Updates an SOP by ID.
     *
     * @param id the unique identifier of the SOP to update
     * @param request the SOP fields to modify, provided as a JSON request body
     * @param exchange the current server web exchange, used for admin role verification
     * @return a mono wrapping a response entity with the updated SOP, 404 if not found,
     *         or 409 if the update causes a name conflict
     */
    @PutMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> updateSop(@PathVariable("id") String id,
        @RequestBody Map<String, Object> request, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                Map<String, Object> sop = sopService.updateSop(id, request);
                if (sop == null) {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("success", false);
                    body.put("error", "SOP not found: " + id);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
                }
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("sop", sop);
                return ResponseEntity.ok(body);
            } catch (IllegalArgumentException e) {
                log.warn("SOP update conflict: {}", e.getMessage());
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "SOP update conflict");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Deletes an SOP by ID.
     *
     * @param id the unique identifier of the SOP to delete
     * @param exchange the current server web exchange, used for admin role verification
     * @return a mono wrapping a response entity with a success flag, or 404 if the SOP does not exist
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteSop(@PathVariable("id") String id, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            boolean deleted = sopService.deleteSop(id);
            if (!deleted) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "SOP not found: " + id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            return ResponseEntity.ok(body);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
