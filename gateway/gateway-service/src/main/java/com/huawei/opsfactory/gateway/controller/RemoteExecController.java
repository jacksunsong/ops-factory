/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.service.CommandWhitelistService;
import com.huawei.opsfactory.gateway.service.RemoteExecutionService;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller for executing and risk-checking remote commands on managed hosts.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RequestMapping("/gateway/remote")
public class RemoteExecController {
    private final RemoteExecutionService remoteExecutionService;

    private final CommandWhitelistService commandWhitelistService;

    /**
     * Creates the remote exec controller instance.
     */
    public RemoteExecController(RemoteExecutionService remoteExecutionService,
        CommandWhitelistService commandWhitelistService) {
        this.remoteExecutionService = remoteExecutionService;
        this.commandWhitelistService = commandWhitelistService;
    }

    /**
     * Executes a remote command on a managed host after whitelist validation.
     *
     * @param request HTTP request
     * @param exchange server web exchange
     * @return the result
     */
    @PostMapping("/execute")
    public Mono<ResponseEntity<Map<String, Object>>> execute(@RequestBody Map<String, Object> request,
        ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);

        String hostId = (String) request.get("hostId");
        String command = (String) request.get("command");
        Object timeoutObj = request.get("timeout");
        int timeout = (timeoutObj instanceof Number) ? ((Number) timeoutObj).intValue() : 30;

        if (hostId == null || hostId.isBlank()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("error", "hostId is required");
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body));
        }
        if (command == null || command.isBlank()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("error", "command is required");
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body));
        }

        final int finalTimeout = timeout;
        return Mono.fromCallable(() -> {
            Map<String, Object> result = remoteExecutionService.execute(hostId, command, finalTimeout);

            // Check for whitelist rejection
            if (Boolean.FALSE.equals(result.get("success")) && result.containsKey("rejectedCommands")) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("error", "Command rejected by whitelist");
                body.put("rejectedCommands", result.get("rejectedCommands"));
                body.put("message", result.getOrDefault("message", ""));
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("hostId", hostId);
            body.put("hostIp", result.get("hostIp"));
            body.put("username", result.get("username"));
            body.put("hostName", result.get("hostName"));
            body.put("command", result.get("command"));
            body.put("effectiveCommand", result.get("effectiveCommand"));
            body.put("exitCode", result.get("exitCode"));
            body.put("output", result.get("output"));
            body.put("error", result.getOrDefault("error", ""));
            body.put("duration", result.get("duration"));
            return ResponseEntity.ok(body);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Checks the risk level of a command against the whitelist.
     *
     * @param request HTTP request
     * @param exchange server web exchange
     * @return true if the risk level of a command against the whitelist
     */
    @PostMapping("/check-risk")
    public Mono<ResponseEntity<Map<String, Object>>> checkRisk(@RequestBody Map<String, Object> request,
        ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);

        String command = (String) request.get("command");
        if (command == null || command.isBlank()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("error", "command is required");
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body));
        }

        String riskLevel = commandWhitelistService.getRiskLevel(command);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("command", command);
        body.put("riskLevel", riskLevel);
        return Mono.just(ResponseEntity.ok(body));
    }
}
