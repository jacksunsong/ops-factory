package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.service.AgentSkillInstallService;
import com.huawei.opsfactory.gateway.service.SkillInstallConflictException;
import java.util.LinkedHashMap;
import java.util.Map;
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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/gateway/agents")
public class AgentSkillController {

    private final AgentSkillInstallService installService;

    public AgentSkillController(AgentSkillInstallService installService) {
        this.installService = installService;
    }

    @PostMapping("/{agentId}/skills/install")
    public Mono<ResponseEntity<Map<String, Object>>> installSkill(
            @PathVariable String agentId,
            @RequestBody Map<String, String> body,
            ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        String skillId = body.get("skillId");
        return Mono.fromCallable(() -> ResponseEntity.ok(installService.install(agentId, skillId)))
                .onErrorResume(SkillInstallConflictException.class, e -> Mono.just(conflict(e.getMessage())))
                .onErrorResume(IllegalArgumentException.class, e -> Mono.just(badRequest(e.getMessage())))
                .onErrorMap(Exception.class, e -> new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        e.getMessage() == null ? "Failed to install skill" : e.getMessage(),
                        e))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{agentId}/skills/{skillId}")
    public Mono<ResponseEntity<Map<String, Object>>> uninstallSkill(
            @PathVariable String agentId,
            @PathVariable String skillId,
            ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
        return Mono.fromCallable(() -> ResponseEntity.ok(installService.uninstall(agentId, skillId)))
                .onErrorResume(IllegalArgumentException.class, e -> Mono.just(badRequest(e.getMessage())))
                .onErrorMap(Exception.class, e -> new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        e.getMessage() == null ? "Failed to uninstall skill" : e.getMessage(),
                        e))
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
