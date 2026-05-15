/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.service.AgentConfigService;
import com.huawei.opsfactory.gateway.service.FileService;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Persists and retrieves file capsule metadata (messageId to output files).
 * Data is stored at: data/{sessionId}/file-capsules.json
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RequestMapping("/gateway/agents/{agentId}/file-capsules")
public class FileCapsuleController {
    private final AgentConfigService agentConfigService;

    private final FileService fileService;

    /**
     * Creates the file capsule controller.
     *
     * @param agentConfigService service for resolving agent directory paths
     * @param fileService service handling file persistence operations
     */
    public FileCapsuleController(AgentConfigService agentConfigService, FileService fileService) {
        this.agentConfigService = agentConfigService;
        this.fileService = fileService;
    }

    /**
     * Returns the persisted messageId-to-files mapping for a session.
     *
     * @param agentId agent instance identifier
     * @param sessionId session identifier whose file capsules to retrieve
     * @param exchange current HTTP exchange carrying user context
     * @return Mono emitting a map with "entries" keyed by messageId
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> getFileCapsules(@PathVariable String agentId, @RequestParam String sessionId,
        ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        Path workingDir = agentConfigService.getUserAgentDir(userId, agentId);
        return Mono.fromCallable(() -> {
            Map<String, List<Map<String, String>>> entries = fileService.loadOutputFiles(workingDir, sessionId);
            return Map.<String, Object> of("entries", entries);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Saves the messageId-to-files mapping reported by the frontend for a session.
     *
     * @param agentId agent instance identifier
     * @param body request body containing sessionId, messageId, and files list
     * @param exchange current HTTP exchange carrying user context
     * @return Mono emitting a map with "status" key
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> saveFileCapsule(@PathVariable String agentId,
        @RequestBody Map<String, Object> body, ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        Path workingDir = agentConfigService.getUserAgentDir(userId, agentId);

        String sessionId = (String) body.get("sessionId");
        String messageId = (String) body.get("messageId");
        Object rawFiles = body.get("files");

        if (sessionId == null || messageId == null || !(rawFiles instanceof List<?> fileList)) {
            return Mono.just(Map.of("status", "error", "message", "sessionId, messageId, and files are required"));
        }

        // Convert List<Object> → List<Map<String, String>>
        List<Map<String, String>> files = fileList.stream().filter(item -> item instanceof Map).map(item -> {
            Map<String, String> entry = new java.util.LinkedHashMap<>();
            ((Map<?, ?>) item).forEach((k, v) -> entry.put(String.valueOf(k), String.valueOf(v)));
            return entry;
        }).toList();

        return Mono.fromCallable(() -> {
            fileService.persistOutputFiles(workingDir, sessionId, messageId, files);
            return Map.<String, Object> of("status", "ok");
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
