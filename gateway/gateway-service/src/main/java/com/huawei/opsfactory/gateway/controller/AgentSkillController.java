/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.service.AgentSkillInstallService;
import com.huawei.opsfactory.gateway.service.SkillInstallConflictException;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller for installing and uninstalling skills on agent instances.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RequestMapping("/gateway/agents")
public class AgentSkillController {
    private final AgentSkillInstallService installService;

    /**
     * Creates the agent skill controller instance.
     *
     * @param installService service handling skill install/uninstall operations
     */
    public AgentSkillController(AgentSkillInstallService installService) {
        this.installService = installService;
    }

    /**
     * Installs a skill on the specified agent instance.
     *
     * @param agentId agent instance identifier
     * @param body request body containing "skillId"
     * @param exchange current HTTP exchange carrying user context
     * @return Mono emitting ResponseEntity with installation result
     */
    @PostMapping("/{agentId}/skills/install")
    public Mono<ResponseEntity<Map<String, Object>>> installSkill(@PathVariable("agentId") String agentId,
        @RequestBody Map<String, String> body, ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        String skillId = body.get("skillId");
        return Mono.fromCallable(() -> ResponseEntity.ok(installService.install(agentId, skillId)))
            .onErrorResume(SkillInstallConflictException.class, e -> Mono.just(conflict(e.getMessage())))
            .onErrorResume(IllegalArgumentException.class, e -> Mono.just(badRequest(e.getMessage())))
            .onErrorMap(Exception.class,
                e -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    e.getMessage() == null ? "Failed to install skill" : e.getMessage(), e))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Uninstalls a skill from the specified agent instance.
     *
     * @param agentId agent instance identifier
     * @param skillId skill identifier to remove
     * @param exchange current HTTP exchange carrying user context
     * @return Mono emitting ResponseEntity with uninstallation result
     */
    @DeleteMapping("/{agentId}/skills/{skillId}")
    public Mono<ResponseEntity<Map<String, Object>>> uninstallSkill(@PathVariable("agentId") String agentId,
        @PathVariable("skillId") String skillId, ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> ResponseEntity.ok(installService.uninstall(agentId, skillId)))
            .onErrorResume(IllegalArgumentException.class, e -> Mono.just(badRequest(e.getMessage())))
            .onErrorMap(Exception.class,
                e -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    e.getMessage() == null ? "Failed to uninstall skill" : e.getMessage(), e))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", message);
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<Map<String, Object>> conflict(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", message);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
