/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.common.model.AgentRegistryEntry;
import com.huawei.opsfactory.gateway.process.InstanceManager;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for managing agent registration, configuration, skills, and memory.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RequestMapping("/gateway/agents")
public class AgentController {
    private static final java.util.regex.Pattern CATEGORY_PATTERN = java.util.regex.Pattern.compile("^[a-zA-Z0-9_-]+$");

    private final AgentConfigService agentConfigService;

    private final InstanceManager instanceManager;

    /**
     * Creates the agent controller instance.
     *
     * @param agentConfigService service for loading and persisting agent configurations
     * @param instanceManager manager for controlling running agent instances
     */
    public AgentController(AgentConfigService agentConfigService, InstanceManager instanceManager) {
        this.agentConfigService = agentConfigService;
        this.instanceManager = instanceManager;
    }

    private static boolean isValidCategory(String category) {
        return CATEGORY_PATTERN.matcher(category).matches();
    }

    private static Mono<ResponseEntity<Map<String, Object>>> badCategory() {
        return Mono.just(
            ResponseEntity.badRequest().body(Map.of("success", (Object) false, "error", "Invalid category name")));
    }

    /**
     * Lists all registered agents with their status, provider, model, and skills.
     *
     * @return reactive map containing a list of agent summaries keyed by {@code "agents"}
     */
    @GetMapping
    public Mono<Map<String, Object>> listAgents() {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> agents = agentConfigService.getRegistry().stream().map(entry -> {
                Map<String, Object> config = Map.of();
                String status = "configured";
                String error = null;
                try {
                    config = agentConfigService.loadAgentConfigYaml(entry.id());
                } catch (IllegalStateException e) {
                    status = "invalid_config";
                    error = e.getMessage();
                }

                List<Map<String, String>> skills;
                try {
                    skills = agentConfigService.listSkills(entry.id());
                } catch (IllegalStateException e) {
                    skills = List.of();
                    if (error == null) {
                        status = "invalid_config";
                        error = e.getMessage();
                    }
                }
                Map<String, Object> agentMap = new LinkedHashMap<>();
                agentMap.put("id", entry.id());
                agentMap.put("name", entry.name());
                agentMap.put("status", status);
                agentMap.put("provider", config.getOrDefault("GOOSE_PROVIDER", ""));
                agentMap.put("model", config.getOrDefault("GOOSE_MODEL", ""));
                agentMap.put("skills", skills);
                if (error != null && !error.isBlank()) {
                    agentMap.put("error", error);
                }
                return (Map<String, Object>) agentMap;
            }).toList();
            return Map.<String, Object> of("agents", agents);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Creates a new agent with the given ID and name.
     *
     * @param body request body containing {@code "id"} and {@code "name"} fields
     * @param exchange current server web exchange used for admin authorization
     * @return reactive response with the created agent details on success, or an error body on failure
     * @throws ResponseStatusException if the agent configuration files cannot be written to disk
     */
    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> createAgent(@RequestBody Map<String, String> body,
        ServerWebExchange exchange) {
        String id = body.get("id");
        String name = body.get("name");
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agent ID is required");
        }
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agent name is required");
        }
        try {
            Map<String, Object> agent = agentConfigService.createAgent(id.strip(), name.strip());
            return Mono
                .just(ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", (Object) true, "agent", agent)));
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("success", false);
            errorBody.put("error", e.getMessage());
            return Mono.just(ResponseEntity.badRequest().body(errorBody));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create agent", e);
        }
    }

    /**
     * Deletes an agent by ID and stops all its running instances.
     *
     * @param id agent identifier from the URL path
     * @param exchange current server web exchange used for admin authorization
     * @return reactive response indicating success or a bad-request error body
     * @throws ResponseStatusException if the agent configuration files cannot be removed from disk
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteAgent(@PathVariable("id") String id, ServerWebExchange exchange) {
        try {
            instanceManager.stopAllForAgent(id);
            agentConfigService.deleteAgent(id);
            return Mono.just(ResponseEntity.ok(Map.of("success", (Object) true)));
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("success", false);
            errorBody.put("error", e.getMessage());
            return Mono.just(ResponseEntity.badRequest().body(errorBody));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete agent", e);
        }
    }

    /**
     * Lists all skills configured for the specified agent.
     *
     * @param id agent identifier from the URL path
     * @param exchange current server web exchange used for admin authorization
     * @return reactive map containing a list of skill descriptors keyed by {@code "skills"}
     */
    @GetMapping("/{id}/skills")
    public Mono<Map<String, Object>> listSkills(@PathVariable("id") String id, ServerWebExchange exchange) {
        return Mono.just(Map.of("skills", agentConfigService.listSkills(id)));
    }

    /**
     * Gets the full configuration for the specified agent.
     *
     * @param id agent identifier from the URL path
     * @param exchange current server web exchange used for admin authorization
     * @return reactive response containing the agent id, name, provider, model, working directory,
     *         and agents.md content; or 404 if the agent is not found
     */
    @GetMapping("/{id}/config")
    public Mono<ResponseEntity<Map<String, Object>>> getConfig(@PathVariable("id") String id, ServerWebExchange exchange) {
        AgentRegistryEntry entry = agentConfigService.findAgent(id);
        if (entry == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        Map<String, Object> config = agentConfigService.loadAgentConfigYaml(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entry.id());
        result.put("name", entry.name());
        result.put("agentsMd", agentConfigService.readAgentsMd(id));
        result.put("provider", config.getOrDefault("GOOSE_PROVIDER", ""));
        result.put("model", config.getOrDefault("GOOSE_MODEL", ""));
        result.put("modelConfig", agentConfigService.extractModelConfig(config));
        result.put("configSummary", agentConfigService.extractAgentConfigSummary(config));
        result.put("providers", agentConfigService.listCustomProviders(id));
        result.put("workingDir", agentConfigService.getAgentsDir().resolve(id).toString());
        return Mono.just(ResponseEntity.ok(result));
    }

    /**
     * Updates the agents.md configuration for the specified agent.
     *
     * @param id agent identifier from the URL path
     * @param body request body containing the {@code "agentsMd"} field to write
     * @param exchange current server web exchange used for admin authorization
     * @return reactive response indicating success, a bad-request body if the agent is not found,
     *         or an internal-error status if the file write fails
     * @throws ResponseStatusException if the agents.md file cannot be written to disk
     */
    @PutMapping("/{id}/config")
    public Mono<ResponseEntity<Map<String, Object>>> updateConfig(@PathVariable("id") String id,
        @RequestBody Map<String, String> body, ServerWebExchange exchange) {
        AgentRegistryEntry entry = agentConfigService.findAgent(id);
        if (entry == null) {
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("success", false);
            errorBody.put("error", "Agent '" + id + "' not found");
            return Mono.just(ResponseEntity.badRequest().body(errorBody));
        }
        String agentsMd = body.get("agentsMd");
        if (agentsMd != null) {
            try {
                agentConfigService.writeAgentsMd(id, agentsMd);
            } catch (IllegalStateException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update config", e);
            }
        }
        return Mono.just(ResponseEntity.ok(Map.of("success", (Object) true)));
    }

    /**
     * Updates the model configuration for the specified agent.
     *
     * @param id agent identifier from the URL path
     * @param body request body containing model configuration keys
     * @param exchange current server web exchange used for admin authorization
     * @return reactive response indicating success or an error body
     */
    @PutMapping("/{id}/model-config")
    public Mono<ResponseEntity<Map<String, Object>>> updateModelConfig(@PathVariable("id") String id,
        @RequestBody Map<String, String> body, ServerWebExchange exchange) {
        AgentRegistryEntry entry = agentConfigService.findAgent(id);
        if (entry == null) {
            return Mono.just(badAgent(id));
        }
        try {
            agentConfigService.updateModelConfig(id, body);
            return Mono.just(ResponseEntity.ok(Map.of("success", (Object) true)));
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("success", false);
            errorBody.put("error", e.getMessage());
            return Mono.just(ResponseEntity.badRequest().body(errorBody));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update model config", e);
        }
    }

    /**
     * Creates a custom provider for the specified agent.
     *
     * @param id agent identifier from the URL path
     * @param body request body containing the provider definition
     * @param exchange current server web exchange used for admin authorization
     * @return reactive response with the created provider on success, or an error body on failure
     */
    @PostMapping("/{id}/providers")
    public Mono<ResponseEntity<Map<String, Object>>> createProvider(@PathVariable("id") String id,
        @RequestBody Map<String, Object> body, ServerWebExchange exchange) {
        AgentRegistryEntry entry = agentConfigService.findAgent(id);
        if (entry == null) {
            return Mono.just(badAgent(id));
        }
        try {
            Map<String, Object> provider = agentConfigService.createCustomProvider(id, body);
            return Mono.just(ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("success", (Object) true, "provider", provider)));
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("success", false);
            errorBody.put("error", e.getMessage());
            return Mono.just(ResponseEntity.badRequest().body(errorBody));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create provider", e);
        }
    }

    /**
     * Updates a custom provider for the specified agent.
     *
     * @param id agent identifier from the URL path
     * @param providerName provider name from the URL path
     * @param body request body containing the updated provider fields
     * @param exchange current server web exchange used for admin authorization
     * @return reactive response with the updated provider on success, or an error body on failure
     */
    @PutMapping("/{id}/providers/{providerName}")
    public Mono<ResponseEntity<Map<String, Object>>> updateProvider(@PathVariable("id") String id,
        @PathVariable("providerName") String providerName, @RequestBody Map<String, Object> body,
        ServerWebExchange exchange) {
        AgentRegistryEntry entry = agentConfigService.findAgent(id);
        if (entry == null) {
            return Mono.just(badAgent(id));
        }
        try {
            Map<String, Object> provider = agentConfigService.updateCustomProvider(id, providerName, body);
            return Mono.just(ResponseEntity.ok(Map.of("success", (Object) true, "provider", provider)));
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("success", false);
            errorBody.put("error", e.getMessage());
            return Mono.just(ResponseEntity.badRequest().body(errorBody));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update provider", e);
        }
    }

    private ResponseEntity<Map<String, Object>> badAgent(String id) {
        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("success", false);
        errorBody.put("error", "Agent '" + id + "' not found");
        return ResponseEntity.badRequest().body(errorBody);
    }

    /**
     * Lists all memory files for the specified agent.
     *
     * @param id agent identifier from the URL path
     * @param exchange current server web exchange used for admin authorization
     * @return reactive response containing a list of memory file descriptors keyed by {@code "files"}
     */
    @GetMapping("/{id}/memory")
    public Mono<ResponseEntity<Map<String, Object>>> listMemory(@PathVariable String id, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            List<Map<String, String>> files = agentConfigService.listMemoryFiles(id);
            return ResponseEntity.ok(Map.<String, Object> of("files", files));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Gets the content of a specific memory category for the specified agent.
     *
     * @param id agent identifier from the URL path
     * @param category memory category name extracted from the URL path
     * @param exchange current server web exchange used for admin authorization
     * @return reactive response with the category name and file content, a 400 for invalid
     *         category names, or 404 if the memory file does not exist
     */
    @GetMapping("/{id}/memory/{category}")
    public Mono<ResponseEntity<Map<String, Object>>> getMemoryFile(@PathVariable("id") String id,
        @PathVariable("category") String category, ServerWebExchange exchange) {
        if (!isValidCategory(category)) {
            return badCategory();
        }
        return Mono.<ResponseEntity<Map<String, Object>>> fromCallable(() -> {
            String content = agentConfigService.readMemoryFile(id, category);
            if (content == null) {
                return ResponseEntity.notFound().<Map<String, Object>> build();
            }
            return ResponseEntity.ok(Map.<String, Object> of("category", category, "content", content));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Writes content to a specific memory category for the specified agent.
     *
     * @param id agent identifier from the URL path
     * @param category memory category name extracted from the URL path
     * @param body request body containing the {@code "content"} field to write
     * @param exchange current server web exchange used for admin authorization
     * @return reactive response indicating success, a 400 for invalid category names,
     *         or a bad-request body if the write fails due to invalid arguments
     */
    @PutMapping("/{id}/memory/{category}")
    public Mono<ResponseEntity<Map<String, Object>>> putMemoryFile(@PathVariable("id") String id,
        @PathVariable("category") String category, @RequestBody Map<String, String> body, ServerWebExchange exchange) {
        if (!isValidCategory(category)) {
            return badCategory();
        }
        return Mono.fromCallable(() -> {
            try {
                agentConfigService.writeMemoryFile(id, category, body.getOrDefault("content", ""));
                return ResponseEntity.ok(Map.<String, Object> of("success", (Object) true));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                    .body(Map.<String, Object> of("success", (Object) false, "error", e.getMessage()));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Deletes a specific memory category for the specified agent.
     *
     * @param id agent identifier from the URL path
     * @param category memory category name extracted from the URL path
     * @param exchange current server web exchange used for admin authorization
     * @return reactive response indicating success, a 400 for invalid category names,
     *         or a bad-request body if the deletion fails due to invalid arguments
     */
    @DeleteMapping("/{id}/memory/{category}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteMemoryFile(@PathVariable("id") String id,
        @PathVariable("category") String category, ServerWebExchange exchange) {
        if (!isValidCategory(category)) {
            return badCategory();
        }
        return Mono.fromCallable(() -> {
            try {
                agentConfigService.deleteMemoryFile(id, category);
                return ResponseEntity.ok(Map.<String, Object> of("success", (Object) true));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                    .body(Map.<String, Object> of("success", (Object) false, "error", e.getMessage()));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
