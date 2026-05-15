/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.common.constants.GatewayConstants;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.proxy.GoosedProxy;
import com.huawei.opsfactory.gateway.service.AgentConfigService;

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
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;

import java.util.Map;

/**
 * REST controller for managing MCP (Model Context Protocol) extensions on agent instances.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RequestMapping("/gateway/agents/{agentId}/mcp")
public class McpController {
    private static final String KNOWLEDGE_SERVICE_MCP = "knowledge-service";

    private static final String KNOWLEDGE_CLI_MCP = "knowledge-cli";

    private final InstanceManager instanceManager;

    private final GoosedProxy goosedProxy;

    private final AgentConfigService agentConfigService;

    /**
     * Creates the mcp controller instance.
     */
    public McpController(InstanceManager instanceManager, GoosedProxy goosedProxy,
        AgentConfigService agentConfigService) {
        this.instanceManager = instanceManager;
        this.goosedProxy = goosedProxy;
        this.agentConfigService = agentConfigService;
    }

    /**
     * Lists MCP extensions configured on the agent's system instance.
     *
     * @param agentId agentId
     * @param exchange exchange
     * @return the result
     */
    @GetMapping
    public Mono<Void> getMcpExtensions(@PathVariable("agentId") String agentId, ServerWebExchange exchange) {
        requireAdmin(exchange);
        // Route to the system instance
        return instanceManager.getOrSpawn(agentId, GatewayConstants.SYSTEM_USER)
            .flatMap(instance -> goosedProxy.proxy(exchange.getRequest(), exchange.getResponse(), instance.getPort(),
                "/config/extensions", instance.getSecretKey()));
    }

    /**
     * Creates a new MCP extension on the agent's system instance and recycles running instances.
     *
     * @param agentId agentId
     * @param body body
     * @param exchange exchange
     * @return a new MCP extension
     */
    @PostMapping
    public Mono<String> createMcpExtension(@PathVariable("agentId") String agentId, @RequestBody String body,
        ServerWebExchange exchange) {
        requireAdmin(exchange);

        // Persist config to the system instance, then recycle all agent instances so
        // subsequent requests start from a clean process with the updated config.
        return instanceManager.getOrSpawn(agentId, GatewayConstants.SYSTEM_USER).flatMap(sysInstance -> {
            WebClient wc = goosedProxy.getWebClient();
            String sysTarget = goosedProxy.goosedBaseUrl(sysInstance.getPort());

            return wc.post()
                .uri(sysTarget + "/config/extensions")
                .header(GatewayConstants.HEADER_SECRET_KEY, sysInstance.getSecretKey())
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .map(sysResult -> {
                    instanceManager.stopAllForAgent(agentId);
                    return sysResult;
                });
        });
    }

    /**
     * Deletes an MCP extension by name and recycles running instances.
     *
     * @param agentId agent identifier
     * @param name name value
     * @param exchange server web exchange
     * @return the result
     */
    @DeleteMapping("/{name}")
    public Mono<String> deleteMcpExtension(@PathVariable("agentId") String agentId, @PathVariable("name") String name,
        ServerWebExchange exchange) {
        requireAdmin(exchange);
        String path = "/config/extensions/" + name;

        return instanceManager.getOrSpawn(agentId, GatewayConstants.SYSTEM_USER).flatMap(sysInstance -> {
            WebClient wc = goosedProxy.getWebClient();
            String sysTarget = goosedProxy.goosedBaseUrl(sysInstance.getPort());

            return wc.delete()
                .uri(sysTarget + path)
                .header(GatewayConstants.HEADER_SECRET_KEY, sysInstance.getSecretKey())
                .retrieve()
                .bodyToMono(String.class)
                .map(sysResult -> {
                    instanceManager.stopAllForAgent(agentId);
                    return sysResult;
                });
        });
    }

    /**
     * Gets the settings for a specific MCP extension.
     *
     * @param agentId agent identifier
     * @param name name value
     * @param exchange server web exchange
     * @return the settings for a specific MCP extension
     */
    @GetMapping("/{name}/settings")
    public Mono<ResponseEntity<Map<String, Object>>> getMcpSettings(@PathVariable("agentId") String agentId,
        @PathVariable("name") String name, ServerWebExchange exchange) {
        requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            try {
                Map<String, Object> settings = agentConfigService.readMcpSettings(agentId, name);
                if (hasConfigBackedSettings(name)) {
                    if (settings == null) {
                        return ResponseEntity.ok(emptyKnowledgeSettings(name));
                    }
                    if (!settings.containsKey("sourceId")) {
                        settings.put("sourceId", null);
                    }
                    return ResponseEntity.ok(settings);
                }
                if (settings == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.<String, Object> of());
                }
                return ResponseEntity.ok(settings);
            } catch (IllegalStateException e) {
                if (hasConfigBackedSettings(name)) {
                    return ResponseEntity.ok(emptyKnowledgeSettings(name));
                }
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.<String, Object> of("code", "SETTINGS_READ_FAILED", "message",
                        "Failed to read MCP settings"));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Updates the settings for a specific MCP extension.
     *
     * @param agentId the settings for a specific MCP extension
     * @param name the settings for a specific MCP extension
     * @param body the settings for a specific MCP extension
     * @param exchange the settings for a specific MCP extension
     * @return the result
     */
    @PutMapping("/{name}/settings")
    public Mono<ResponseEntity<Map<String, Object>>> putMcpSettings(@PathVariable("agentId") String agentId,
        @PathVariable("name") String name, @RequestBody Map<String, Object> body, ServerWebExchange exchange) {
        requireAdmin(exchange);
        return Mono.fromCallable(() -> {
            try {
                agentConfigService.writeMcpSettings(agentId, name, body);
                return ResponseEntity.ok(body);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.<String, Object> of("code", "RESOURCE_NOT_FOUND", "message", e.getMessage()));
            } catch (IllegalStateException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.<String, Object> of("code", "SETTINGS_WRITE_FAILED", "message",
                        "Failed to write MCP settings"));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void requireAdmin(ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
    }

    private boolean hasConfigBackedSettings(String name) {
        return KNOWLEDGE_SERVICE_MCP.equals(name) || KNOWLEDGE_CLI_MCP.equals(name);
    }

    private Map<String, Object> emptyKnowledgeSettings(String name) {
        Map<String, Object> fallback = new java.util.HashMap<>();
        fallback.put("sourceId", null);
        if (KNOWLEDGE_CLI_MCP.equals(name)) {
            fallback.put("rootDir", null);
        }
        return fallback;
    }
}
